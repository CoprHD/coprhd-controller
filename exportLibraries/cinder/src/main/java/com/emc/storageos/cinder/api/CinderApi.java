/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cinder.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.errorhandling.CinderException;
import com.emc.storageos.cinder.model.AccessWrapper;
import com.emc.storageos.cinder.model.AuthTokenRequest;
import com.emc.storageos.cinder.model.SnapshotCreateRequest;
import com.emc.storageos.cinder.model.SnapshotCreateResponse;
import com.emc.storageos.cinder.model.SnapshotListResponse;
import com.emc.storageos.cinder.model.VolumeAttachRequest;
import com.emc.storageos.cinder.model.VolumeAttachResponse;
import com.emc.storageos.cinder.model.VolumeAttachResponseAlt;
import com.emc.storageos.cinder.model.VolumeCreateRequest;
import com.emc.storageos.cinder.model.VolumeCreateResponse;
import com.emc.storageos.cinder.model.VolumeDetachRequest;
import com.emc.storageos.cinder.model.VolumeExpandRequest;
import com.emc.storageos.cinder.model.VolumeShowResponse;
import com.emc.storageos.cinder.model.VolumeTypes;
import com.emc.storageos.cinder.rest.client.CinderRESTClient;
import com.emc.storageos.services.util.SecurityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Cinder API client
 */
public class CinderApi {
    private Logger _log = LoggerFactory.getLogger(CinderApi.class);
    private CinderEndPointInfo endPoint;
    private CinderRESTClient client;

    /**
     * Create Cinder API client
     * 
     * @param provider Storage Provider URI
     * @param client Jersey Client
     * @return
     */
    public CinderApi(CinderEndPointInfo endPointInfo, Client client) {
        endPoint = endPointInfo;
        this.client = new CinderRESTClient(client, endPointInfo.getCinderToken());
    }

    public CinderRESTClient getClient() {
        return client;
    }

    /**
     * Gets token from Keystone. It is a synchronous operation.
     * 
     * @param uri String
     * @return token String
     */
    public String getAuthToken(String uri) {

        String token = "";
        String tenantId = "";
        Gson gson = new Gson();

        AuthTokenRequest w = new AuthTokenRequest();
        w.auth.passwordCredentials.username = endPoint.getCinderRESTuserName();
        w.auth.passwordCredentials.password = endPoint.getCinderRESTPassword();
        w.auth.tenantName = endPoint.getCinderTenantName();
        String json = gson.toJson(w);

        try {
            ClientResponse js_response = client.post(URI.create(uri), json);
            String s = js_response.getEntity(String.class);
            AccessWrapper wrapper = gson.fromJson(SecurityUtils.sanitizeJsonString(s), AccessWrapper.class);
            token = wrapper.access.token.id;
            tenantId = wrapper.access.token.tenant.id;
        } catch (Exception e) {
            _log.error("Exception!! \n Now trying String processing....\n");
            ClientResponse js_response = client.post(URI.create(uri), json);
            String s = js_response.getEntity(String.class);

            token = getTokenId(s);
            tenantId = getTenantId(s);

        } /* catch */

        // Update it in endPoint instance for every fetch/re-fetch
        endPoint.setCinderToken(token);
        getClient().setAuthTokenHeader(token);
        endPoint.setCinderTenantId(tenantId);

        return token;
    }

    private String getTokenId(String data) {
        return getId("token", data);
    }

    private String getTenantId(String data) {
        return getId("tenant", data);
    }

    private String getId(String queryStr, String data) {

        String id = "";
        int index = data.indexOf(queryStr);

        if (index > -1) {
            // contains the token
            String token_str = data.substring(index);
            int id_index = token_str.indexOf("id");

            if (id_index > -1) {
                // contains the id
                String id_str = token_str.substring(id_index);

                String[] splits = id_str.split("\"");
                if (splits.length >= 2) {
                    id = splits[2].trim();
                }
            }
        }

        return id;
    }

    /**
     * Gets all volume types present in the OpenStack
     * cinder node. It is a synchronous operation.
     * 
     * @return
     */
    public VolumeTypes getVolumeTypes() {

        VolumeTypes volTypes = null;
        String vol_types_uri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_LIST_VOLUME_TYPES,
                        endPoint.getCinderTenantId());

        ClientResponse js_response = getClient().get(URI.create(vol_types_uri));
        String s = js_response.getEntity(String.class);
        _log.info("Volume types = {}", s);

        Gson gson = new Gson();
        volTypes = gson.fromJson(SecurityUtils.sanitizeJsonString(s), VolumeTypes.class);

        return volTypes;

    }

    /**
     * Create volume operation ( It is asynchronous operation )
     * 
     * @param volumeName : Name of the new volume.
     * @param capacity : Capacity/Size of the volume
     * @param volumeTypeName : The volume type/storage system on which new volume should
     *            be created.
     * 
     * @return volumeId of the new volume under creation
     * @throws Exception
     */
    public String createVolume(String volumeName, long capacity,
            String volumeTypeId) throws Exception
    {
        return createVolume(volumeName, capacity, volumeTypeId, null, null);
    }

    /**
     * Create volume operation ( It is asynchronous operation )
     * 
     * @param volumeName : Name of the new volume.
     * @param capacity : Capacity/Size of the volume
     * @param volumeTypeName : The volume type/storage system on which new volume should
     *            be created.
     * @param sourceVolId : The volume id that needs to be cloned.
     * @param sourceSnapId : The snapshot id that needs to be cloned.
     * 
     * @return volumeId of the new volume under creation
     * @throws Exception
     */
    private String createVolume(String volumeName, long capacity,
            String volumeTypeId, String sourceVolId, String sourceSnapId) throws Exception
    {
        _log.info("CinderApi - start createVolume");

        Gson gson = new Gson();

        VolumeCreateRequest volumeCreate = new VolumeCreateRequest();
    	volumeCreate.volume.name = volumeName;
        volumeCreate.volume.size = capacity;
        // Volume type wont be available in cinder if volumeTypeId starts with "DEFAULT"
        // In that case, no need to pass volume type id in create volume request.
        if (!volumeTypeId.toUpperCase().startsWith(CinderConstants.DEFAULT))
        {
            volumeCreate.volume.volume_type = volumeTypeId;
        }

        // volume clone
        if (null != sourceVolId)
        {
            volumeCreate.volume.source_volid = sourceVolId;
        }

        // create volume from snapshot
        if (null != sourceSnapId)
        {
            volumeCreate.volume.snapshot_id = sourceSnapId;
        }

        String volumeCreateUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_CREATE_VOLUME,
                        endPoint.getCinderTenantId());

        _log.debug("creting volume with uri {}", volumeCreateUri);
        String json = gson.toJson(volumeCreate);
        _log.debug("creating volume with body {}", json);
        ClientResponse js_response = getClient().postWithHeader(URI.create(volumeCreateUri), json);
        String s = js_response.getEntity(String.class);
        _log.debug("Got the response {}", s);

        String newVolumeId = "";
        if (js_response.getStatus() == ClientResponse.Status.ACCEPTED.getStatusCode())
        {
            // This means volume creation request accepted
            VolumeCreateResponse response = gson.fromJson(SecurityUtils.sanitizeJsonString(s), VolumeCreateResponse.class);
            newVolumeId = response.volume.id;
        }
        else
        {
            if (null != sourceVolId)
            {
                throw CinderException.exceptions.volumeCloneFailed(s);
            }

            if (null != sourceSnapId)
            {
                throw CinderException.exceptions.createVolumeFromSnapshotFailed(s);
            }

            throw CinderException.exceptions.volumeCreationFailed(s);
        }

        _log.info("CinderApi - end createVolume");
        return newVolumeId;
    }

    public void expandVolume(String volumeId, long new_size) {
        _log.info("CinderApi - expandVolume START");

        Gson gson = new Gson();

        VolumeExpandRequest request = new VolumeExpandRequest();
        request.extend.new_size = new_size;

        String expandVolumeUri = endPoint.getBaseUri()
                + String.format(CinderConstants.URI_VOLUME_ACTION,
                        new Object[] { endPoint.getCinderTenantId(), volumeId });

        _log.debug("Expanding volume with uri : {}", expandVolumeUri);
        String json = gson.toJson(request);
        _log.debug("Expanding volume with body : {}", json);
        ClientResponse js_response = getClient().postWithHeader(URI.create(expandVolumeUri), json);

        String s = js_response.getEntity(String.class);
        _log.debug("Got the response {}", s);

        _log.debug("Response status {}", String.valueOf(js_response.getStatus()));

        if (js_response.getStatus() != ClientResponse.Status.ACCEPTED.getStatusCode()) {
            // This means volume expand request not accepted
            throw CinderException.exceptions.volumeExpandFailed(s);
        }

        _log.info("CinderApi - expandVolume END");
    }

    /**
     * clone volume operation ( It is asynchronous operation )
     * 
     * @param volumeName : Name of the new volume.
     * @param capacity : Capacity/Size of the volume
     * @param volumeTypeName : The volume type/storage system on which new volume should
     *            be created.
     * @param sourceVolId : The volume id that needs to be cloned
     * 
     * @return volumeId of the new volume under creation
     * @throws Exception
     */
    public String cloneVolume(String volumeName, long capacity, String volumeTypeId, String sourceVolId) throws Exception
    {
        return createVolume(volumeName, capacity, volumeTypeId, sourceVolId, null);
    }

    /**
     * create volume from snapshot operation ( It is asynchronous operation )
     * 
     * @param volumeName : Name of the new volume.
     * @param capacity : Capacity/Size of the volume
     * @param volumeTypeName : The volume type/storage system on which new volume should
     *            be created.
     * @param sourceSnap : The snapshot id that needs to be cloned
     * 
     * @return volumeId of the new volume under creation
     * @throws Exception
     */
    public String createVolumeFromSnapshot(String volumeName, long capacity, String volumeTypeId, String sourceSnap) throws Exception
    {
        return createVolume(volumeName, capacity, volumeTypeId, null, sourceSnap);
    }

    /**
     * Deletes the volume with given volume id.
     * ( It is asynchronous operation )
     * 
     * @param volumeId
     * @return
     */
    public void deleteVolume(String volumeId) throws Exception
    {
        _log.info("CinderApi - start deleteVolume");

        String deleteVolumeUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_DELETE_VOLUME,
                        new Object[] { endPoint.getCinderTenantId(), volumeId });
        ClientResponse deleteResponse = getClient().delete(URI.create(deleteVolumeUri));

        String s = deleteResponse.getEntity(String.class);
        _log.debug("Got the response {}", s);

        if (deleteResponse.getStatus() == ClientResponse.Status.NOT_FOUND.getStatusCode())
        {
            throw CinderException.exceptions.volumeNotFound(volumeId);
        }

        if (deleteResponse.getStatus() != ClientResponse.Status.ACCEPTED.getStatusCode()) {
            throw CinderException.exceptions.volumeDeleteFailed(s);
        }

        _log.info("CinderApi - end deleteVolume");
    }

    /**
     * Deletes multiple volumes
     * 
     * @param volumeIdsToBeDeleted
     */
    public void deleteVolumes(String[] volumeIdsToBeDeleted) throws Exception
    {
        _log.info("CinderApi - start deleteVolumes");

        for (String volumeId : volumeIdsToBeDeleted)
        {
            try
            {
                deleteVolume(volumeId);
            } catch (CinderException e)
            {
                _log.error("Failed to delete the volume with the id : " + volumeId, e);
            }
        }

        _log.info("CinderApi - end deleteVolumes");
    }

    public String updateVolume(String volumeId)
    {
        _log.info("CinderApi - start updateVolume");
        _log.info("CinderApi - end updateVolume");
        return "";
    }

    /**
     * Gets the volume information. Its a synchronous operation.
     * 
     * @param volumeId
     * @return
     */
    public VolumeShowResponse showVolume(String volumeId) throws Exception
    {
        _log.info("CinderApi - start showVolume");

        String showVolumeUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_DELETE_VOLUME,
                        new Object[] { endPoint.getCinderTenantId(), volumeId });
        ClientResponse js_response = getClient().get(URI.create(showVolumeUri));

        _log.debug("uri {} : Response status {}", showVolumeUri, String.valueOf(js_response.getStatus()));
        if (js_response.getStatus() == ClientResponse.Status.NOT_FOUND.getStatusCode())
        {
            throw CinderException.exceptions.volumeNotFound(volumeId);
        }

        String jsonString = js_response.getEntity(String.class);
        VolumeShowResponse volumeDetails = new Gson().fromJson(SecurityUtils.sanitizeJsonString(jsonString), VolumeShowResponse.class);

        _log.info("CinderApi - end showVolume");
        return volumeDetails;
    }

    /**
     * Attaches specified volume to the specified initiator on the host.
     * It is a synchronous operation.
     * 
     * @param volumeId the volume id
     * @param initiator the initiator
     * @param host the host
     * @return the volume attachment response
     * @throws Exception the exception
     */
    public VolumeAttachResponse attachVolume(String volumeId, String initiator,
            String[] wwpns, String[] wwnns, String host) throws Exception {
        _log.info("CinderApi - start attachVolume");

        Gson gson = new Gson();

        VolumeAttachRequest volumeAttach = new VolumeAttachRequest();
        if (initiator != null) {
            volumeAttach.initializeConnection.connector.initiator = initiator;
        }
        else {
            if (wwpns != null) {
                volumeAttach.initializeConnection.connector.wwpns = Arrays.copyOf(wwpns, wwpns.length);
            }

            if (null != wwnns) {
                volumeAttach.initializeConnection.connector.wwnns = Arrays.copyOf(wwnns, wwnns.length);
            }

        }

        volumeAttach.initializeConnection.connector.host = host;

        String volumeAttachmentUri = endPoint.getBaseUri()
                + String.format(CinderConstants.URI_VOLUME_ACTION,
                        new Object[] { endPoint.getCinderTenantId(), volumeId });

        _log.debug("attaching volume to initiator with uri {}", volumeAttachmentUri);
        String json = gson.toJson(volumeAttach);
        _log.info("attaching volume with body {}", json);
        ClientResponse js_response = getClient().postWithHeader(URI.create(volumeAttachmentUri), json);
        String s = js_response.getEntity(String.class);
        _log.debug("Got the response {}", s);

        VolumeAttachResponse response = null;
        _log.debug("Response status {}", String.valueOf(js_response.getStatus()));
        if (js_response.getStatus() == ClientResponse.Status.OK.getStatusCode()) {
            // This means volume attachment request accepted and being processed (Synch opr)
            try {
                response = gson.fromJson(SecurityUtils.sanitizeJsonString(s), VolumeAttachResponse.class);
            } catch (JsonSyntaxException ex) {
                /**
                 * Some drivers return 'target_wwn' as a string list but some returns it as string.
                 * We observed in practice that IBM SVC returning string which could be a faulty one.
                 * In such a case, we capture the response string in an Alternate Java object and
                 * return the details in default response object.
                 */
                VolumeAttachResponseAlt altResponse = gson.fromJson(SecurityUtils.sanitizeJsonString(s), VolumeAttachResponseAlt.class);
                response = getResponseInVolumeAttachResponseFormat(altResponse);
            }
        }
        else {
            throw CinderException.exceptions.volumeAttachFailed(s);
        }

        _log.info("CinderApi - end attachVolume");
        return response;
    }

    /**
     * Copies data from VolumeAttachResponseAlt object to VolumeAttachResponse object.
     * 
     * @param altResponse the VolumeAttachResponseAlt
     * @return the response in VolumeAttachResponse format
     */
    private VolumeAttachResponse getResponseInVolumeAttachResponseFormat(
            VolumeAttachResponseAlt altResponse) {

        VolumeAttachResponse response = new VolumeAttachResponse();

        response.connection_info = response.new ConnectionInfo();
        response.connection_info.driver_volume_type = altResponse.connection_info.driver_volume_type;

        response.connection_info.data = response.new Data();
        response.connection_info.data.target_discovered = altResponse.connection_info.data.target_discovered;
        response.connection_info.data.qos_spec = altResponse.connection_info.data.qos_spec;
        response.connection_info.data.target_iqn = altResponse.connection_info.data.target_iqn;
        response.connection_info.data.target_portal = altResponse.connection_info.data.target_portal;
        // convert string to string list
        response.connection_info.data.target_wwn = Arrays.asList(altResponse.connection_info.data.target_wwn);
        response.connection_info.data.initiator_target_map = altResponse.connection_info.data.initiator_target_map;
        response.connection_info.data.volume_id = altResponse.connection_info.data.volume_id;
        response.connection_info.data.target_lun = altResponse.connection_info.data.target_lun;
        response.connection_info.data.access_mode = altResponse.connection_info.data.access_mode;

        return response;
    }

    /**
     * Detaches the specified volume from the specified initiator.
     * It is an asynchronous operation.
     * 
     * @param volumeId the volume id
     * @param initiator the initiator
     * @param host the host
     * @return the volume attachment response
     * @throws Exception
     */
    public void detachVolume(String volumeId, String initiator, String[] wwpns,
    		String[] wwnns,
            String host) throws Exception
    {
        _log.info("CinderApi - start detachVolume");
        Gson gson = new Gson();

        VolumeDetachRequest volumeDetach = new VolumeDetachRequest();
        if (initiator != null)
        {
            volumeDetach.terminateConnection.connector.initiator = initiator;
        }
        else 
        {
        	if (wwpns != null)
        	{
        		volumeDetach.terminateConnection.connector.wwpns = Arrays.copyOf(wwpns, wwpns.length);
        	}
        	
        	if(null != wwnns)
        	{
        		volumeDetach.terminateConnection.connector.wwnns = Arrays.copyOf(wwnns, wwnns.length);
        	}
            
        } 
        volumeDetach.terminateConnection.connector.host = host;

        String volumeDetachmentUri = endPoint.getBaseUri()
                + String.format(CinderConstants.URI_DETACH_VOLUME,
                        new Object[] { endPoint.getCinderTenantId(), volumeId });

        _log.debug("detaching volume from initiator with uri {}", volumeDetachmentUri);
        String json = gson.toJson(volumeDetach);
        _log.info("detaching volume with body {}", json);
        ClientResponse js_response = getClient().postWithHeader(URI.create(volumeDetachmentUri), json);
        String s = js_response.getEntity(String.class);
        _log.debug("Got the response {}", s);
        _log.debug("Response status {}", String.valueOf(js_response.getStatus()));

        if (js_response.getStatus() != ClientResponse.Status.ACCEPTED.getStatusCode()) {
            // if volume is not found on cinder, mark it as passed.
            if (js_response.getStatus() == ClientResponse.Status.NOT_FOUND.getStatusCode()) {
                _log.info("Volume {} is not found on Cinder. It could have been deleted manually.", volumeId);
            } else {
                throw CinderException.exceptions.volumeDetachFailed(s);
            }
        }
        _log.info("CinderApi - end detachVolume");
    }

    /**
     * Shows the specified volume attachment details.
     * 
     * @param serverId the server id
     * @param volumeAttachmentId the volume attachment id
     * @return the volume attachment response
     */
    /*
     * public VolumeAttachResponse showVolumeAttachment(String serverId, String volumeAttachmentId)
     * {
     * _log.info("CinderApi - start showVolumeAttachment");
     * String showVolumeAttachmentUri = endPoint.getBaseUri()
     * + String.format(CinderConstants.URI_LIST_VOLUME_ATTACHMENT,
     * new Object[] { endPoint.getCinderTenantId(), serverId, volumeAttachmentId });
     * ClientResponse js_response = get_client().get(URI.create(showVolumeAttachmentUri));
     * String jsonString = js_response.getEntity(String.class);
     * 
     * VolumeAttachResponse volumeAttachmentDetails = new Gson().fromJson(jsonString, VolumeAttachResponse.class);
     * _log.info("CinderApi - end showVolumeAttachment");
     * return volumeAttachmentDetails;
     * }
     */

    /**
     * Create Snapshot operation ( It is asynchronous operation )
     * 
     * @param volumeName
     * @param volumeTypeId
     * @return
     * @throws Exception
     */
    public String createSnapshot(String volumeId, String snapshotName) throws Exception {
        _log.info("CinderApi - start createSnapshot");

        Gson gson = new Gson();

        VolumeShowResponse volumeDetails = showVolume(volumeId);
        String volumeName = volumeDetails.volume.name;

        SnapshotCreateRequest request = new SnapshotCreateRequest();
        request.snapshot.name = snapshotName;
        request.snapshot.description = "Snapshot of volume " + volumeName;
        request.snapshot.volume_id = volumeId;
        request.snapshot.force = true;

        String snapshotCreateUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_CREATE_SNAPSHOT,
                        endPoint.getCinderTenantId());

        _log.debug("Creating snapshot with uri : {}", snapshotCreateUri);
        String json = gson.toJson(request);
        _log.debug("Creating snapshot with body : {}", json);
        ClientResponse js_response = getClient().postWithHeader(URI.create(snapshotCreateUri), json);

        String s = js_response.getEntity(String.class);
        _log.debug("Got the response {}", s);

        _log.debug("Response status {}", String.valueOf(js_response.getStatus()));

        String snapshotId = "";
        if (js_response.getStatus() == ClientResponse.Status.ACCEPTED.getStatusCode()) {
            // This means snapshot creation request accepted
            SnapshotCreateResponse response = gson.fromJson(SecurityUtils.sanitizeJsonString(s), SnapshotCreateResponse.class);
            snapshotId = response.snapshot.id;
        } else {
            throw CinderException.exceptions.snapshotCreationFailed(s);
        }

        return snapshotId;
    }

    /**
     * Gets the Snapshot information.
     * It is a synchronous operation.
     * 
     * @param snapshotId
     * @return
     */
    public SnapshotCreateResponse showSnapshot(String snapshotId) throws Exception
    {
        _log.info("CinderApi - start showSnapshot");
        String showSnapshotUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_DELETE_SNAPSHOT,
                        new Object[] { endPoint.getCinderTenantId(), snapshotId });
        ClientResponse js_response = getClient().get(URI.create(showSnapshotUri));
        String jsonString = js_response.getEntity(String.class);

        if (js_response.getStatus() == ClientResponse.Status.NOT_FOUND.getStatusCode())
        {
            throw CinderException.exceptions.snapshotNotFound(snapshotId);
        }

        SnapshotCreateResponse snapshotDetails = new Gson().fromJson(SecurityUtils.sanitizeJsonString(jsonString), SnapshotCreateResponse.class);
        _log.info("CinderApi - end showSnapshot");
        return snapshotDetails;
    }

    /**
     * Deletes the Snapshot with the given id.( It is asynchronous operation )
     * 
     * @param snapshotId
     */
    public void deleteSnapshot(String snapshotId) {

        _log.info("CinderApi - start deleteSnapshot");

        String deleteSnapshotUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_DELETE_SNAPSHOT,
                        new Object[] { endPoint.getCinderTenantId(), snapshotId });

        ClientResponse deleteResponse = getClient().delete(URI.create(deleteSnapshotUri));

        String s = deleteResponse.getEntity(String.class);
        _log.debug("Got the response {}", s);

        if (deleteResponse.getStatus() == ClientResponse.Status.NOT_FOUND.getStatusCode()) {
            throw CinderException.exceptions.snapshotNotFound(snapshotId);
        }

        if (deleteResponse.getStatus() != ClientResponse.Status.ACCEPTED.getStatusCode()) {
            throw CinderException.exceptions.snapshotDeleteFailed(s);
        }

        _log.info("CinderApi - end deleteSnapshot");
    }

    /**
     * Gets the current status { "creating", "attaching", "deleting", "Error", "Available" }
     * of the component ( volume or snapshot ).
     * 
     * @param volumeId
     * @return
     */
    public String getTaskStatus(String componentId, String componentType) throws Exception
    {
        String status = "";
        if (CinderConstants.ComponentType.volume.name().equals(componentType))
        {
            VolumeShowResponse volumeDetails = showVolume(componentId);
            status = volumeDetails.volume.status;
        }
        else if (CinderConstants.ComponentType.snapshot.name().equals(componentType))
        {
            SnapshotCreateResponse snapshotDetails = showSnapshot(componentId);
            status = snapshotDetails.snapshot.status;
        }

        return status;
    }

    /**
     * Returns the list of snapshots on the end point
     * 
     * It is a synchronous operation.
     * 
     * @return
     */
    public SnapshotListResponse listSnapshots()
    {
        _log.info("CinderApi - start listSnapshots");
        SnapshotListResponse listRes = null;
        String listSnapshotsUri = endPoint.getBaseUri() +
                String.format(CinderConstants.URI_LIST_SNAPSHOTS,
                        new Object[] { endPoint.getCinderTenantId() });
        ClientResponse js_response = getClient().get(URI.create(listSnapshotsUri));

        _log.debug("uri {} : Response status {}", listSnapshotsUri, String.valueOf(js_response.getStatus()));
        if (js_response.getStatus() == ClientResponse.Status.OK.getStatusCode())
        {
            String jsonString = js_response.getEntity(String.class);
            listRes = new Gson().fromJson(SecurityUtils.sanitizeJsonString(jsonString), SnapshotListResponse.class);
        }

        _log.info("CinderApi - end listSnapshots");
        return listRes;
    }

    /**
     * Gets the list of snapshot ids created for the volume.
     * 
     * @param volumeId id of the volume for which snapshot list
     *            needs to be fetched
     * 
     * @return List of snapshot Ids
     */
    public List<String> listSnapshotsForVolume(String volumeId)
    {
        _log.info("CinderApi - start listSnapshotsForVolume");
        List<String> snapshotIdList = new ArrayList<String>();

        SnapshotListResponse listRes = listSnapshots();

        if (null != listRes)
        {
            for (int i = 0; i < listRes.snapshots.length; i++)
            {
                String snapshotsVolumeId = listRes.snapshots[i].volume_id;
                if (volumeId.equals(snapshotsVolumeId))
                {
                    snapshotIdList.add(listRes.snapshots[i].id);
                }
            }
        }

        _log.info("CinderApi - end listSnapshotsForVolume");
        return snapshotIdList;
    }

    /*
     * public static void main(String args[]) throws Exception {
     * CinderEndPointInfo ep = new CinderEndPointInfo("lglw9118.lss.emc.com",
     * "cinder", "password", "service");
     * String restBaseUri = "http://lglw9118.lss.emc.com:5000/v2.0";
     * 
     * if (restBaseUri.startsWith(CinderConstants.HTTP_URL)) {
     * ep.setCinderBaseUriHttp(restBaseUri);
     * } else {
     * ep.setCinderBaseUriHttps(restBaseUri);
     * }
     * 
     * ClientConfig config = new DefaultClientConfig();
     * Client jerseyClient = Client.create(config);
     * CinderApi cinderApi = new CinderApi(ep, jerseyClient);
     * 
     * String authToken = cinderApi.getAuthToken("http://lglw9118.lss.emc.com:5000/v2.0/tokens");
     * System.out.println("Auth token is " + authToken);
     * 
     * // System.out.println(" VolumeTypes count "+cinderApi.getVolumeTypes().volume_types.length);
     * // System.out.println("Volume Type Name is "+cinderApi.getVolumeTypes().volume_types[0].name);
     * // System.out.println("Volume Type id is "+cinderApi.getVolumeTypes().volume_types[0].id);
     * 
     * VolumeShowResponse showVolumeResp = cinderApi.showVolume("483b5c80-61ed-4932-b842-ca93fa74955b");
     * System.out.println("showVolumeResp.volume.size : " + showVolumeResp.volume.size);
     * System.out.println("showVolumeResp.volume.name : " + showVolumeResp.volume.name);
     * 
     * System.out.println("Expanding volume!");
     * cinderApi.expandVolume("483b5c80-61ed-4932-b842-ca93fa74955b", 3);
     * System.out.println("showVolumeResp.volume.size : " + showVolumeResp.volume.size);
     * 
     * showVolumeResp = cinderApi.showVolume("483b5c80-61ed-4932-b842-ca93fa74955b");
     * 
     * 
     * // System.out.println("List Snaps "+cinderApi.listSnapshots().snapshots[0].name);
     * 
     * // System.out.println(" Snapshot for the volume with id ac23dd1d-a15c-445d-b6bb-e9c1c5592dfa "+
     * // cinderApi.listSnapshotsForVolume("ac23dd1d-a15c-445d-b6bb-e9c1c5592dfa").toString());
     * }
     */
}
