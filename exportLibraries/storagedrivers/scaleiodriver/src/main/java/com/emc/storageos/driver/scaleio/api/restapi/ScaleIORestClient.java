/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi;

import com.emc.storageos.driver.scaleio.ScaleIOException;
import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.request.*;
import com.emc.storageos.driver.scaleio.api.restapi.response.*;
import com.emc.storageos.driver.scaleio.serviceutils.restutil.StandardRestClient;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.*;

/**
 * This class implements methods for calling ScaleIO REST API to do operations.
 */

public class ScaleIORestClient extends StandardRestClient {

    private static Logger log = LoggerFactory.getLogger(ScaleIORestClient.class);

    /**
     * Constructor
     * 
     * @param baseURI the base URI to connect to ScaleIO Gateway
     * @param client A reference to a Jersey Apache HTTP client.
     * @param username The MDM usernam.
     * @param password The MDM user password.
     */
    public ScaleIORestClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    public void setUsername(String username) {
        _username = username;
    }

    public void setPassword(String password) {
        _password = password;
    }

    public String init() throws Exception {
        return getVersion();
    }

    /**
     * Query all SDCs
     * 
     * @return The list of ScaleIOSDC
     * @throws JSONException
     */
    public List<ScaleIOSDC> queryAllSDC() throws JSONException {
        ClientResponse response = get(URI.create(ScaleIOConstants.GET_SDC_URI));
        return getResponseObjects(ScaleIOSDC.class, response);

    }

    /**
     * Query all SDS
     * 
     * @return The list of ScaleIOSDS
     * @throws Exception
     */
    public List<ScaleIOSDS> queryAllSDS() throws Exception {
        log.info("Discoverying all SDS.");
        ClientResponse response = get(URI.create(ScaleIOConstants.GET_SDS_URI));
        return getResponseObjects(ScaleIOSDS.class, response);
    }

    /**
     * Get the detail information for a pool
     * 
     * @param poolId The storage pool native Id
     * @return The storage pool information
     * @throws Exception
     */
    public ScaleIOStoragePool queryStoragePool(String poolId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOConstants.getStoragePoolStatsURI(poolId)));
        ScaleIOStoragePool pool = getResponseObject(ScaleIOStoragePool.class, response);
        ScaleIOStoragePool poolStats = getStoragePoolStats(poolId);
        pool.setCapacityAvailableForVolumeAllocationInKb(poolStats.getCapacityAvailableForVolumeAllocationInKb());
        pool.setMaxCapacityInKb(poolStats.getMaxCapacityInKb());
        return pool;
    }

    /**
     * Create a volume
     * 
     * @param protectionDomainId The protection domain ID
     * @param storagePoolId The storage pool ID
     * @param volumeName The volume name
     * @param volumeSize The volume Size
     * @return The detail of the created volume
     * @throws Exception
     */
    public ScaleIOVolume addVolume(String protectionDomainId,
            String storagePoolId, String volumeName, String volumeSize) throws Exception {
        ScaleIOCreateVolume volume = new ScaleIOCreateVolume();
        Long sizeInKb = Long.parseLong(volumeSize) / 1024L;
        volume.setVolumeSizeInKb(sizeInKb.toString());
        volume.setStoragePoolId(storagePoolId);
        volume.setName(volumeName);
        volume.setProtectionDomainId(protectionDomainId);
        ClientResponse response = post(URI.create(ScaleIOConstants.VOLUMES_URI), getJsonForEntity(volume));
        ScaleIOVolume createdVol = getResponseObject(ScaleIOVolume.class, response);
        return queryVolume(createdVol.getId());
    }

    /**
     * Create a volume
     * 
     * @param protectionDomainId The protection domain ID
     * @param storagePoolId The storage pool ID
     * @param volumeName The volume name
     * @param volumeSize The volume Size
     * @param thinProvisioned true for thinProvisioned, false for thickProvisioned
     * @return
     * @throws Exception
     */
    public ScaleIOVolume addVolume(String protectionDomainId,
            String storagePoolId, String volumeName, String volumeSize,
            boolean thinProvisioned) throws Exception {
        ScaleIOCreateVolume volume = new ScaleIOCreateVolume();
        Long sizeInKb = Long.parseLong(volumeSize) / 1024L;
        volume.setVolumeSizeInKb(sizeInKb.toString());
        volume.setStoragePoolId(storagePoolId);
        volume.setName(volumeName);
        volume.setProtectionDomainId(protectionDomainId);
        if (thinProvisioned) {
            volume.setVolumeType(ScaleIOConstants.THIN_PROVISIONED);
        } else {
            volume.setVolumeType(ScaleIOConstants.THICK_PROVISIONED);
        }
        ClientResponse response = post(URI.create(ScaleIOConstants.VOLUMES_URI), getJsonForEntity(volume));
        ScaleIOVolume createdVol = getResponseObject(ScaleIOVolume.class, response);
        return queryVolume(createdVol.getId());
    }

    /**
     * Remove the volume
     * 
     * @param volumeId The volume ID
     * @throws Exception
     */
    public void removeVolume(String volumeId) throws Exception {
        String uri = ScaleIOConstants.getRemoveVolumeURI(volumeId);
        ScaleIORemoveVolume removeParm = new ScaleIORemoveVolume();
        post(URI.create(uri), getJsonForEntity(removeParm));
    }

    /**
     * Expand the volume
     * 
     * @param volumeId The volume ID
     * @param newSizeGB The new size of the volume in GB
     * @throws Exception
     */
    public ScaleIOVolume modifyVolumeCapacity(String volumeId, String newSizeGB) throws Exception {
        String uri = ScaleIOConstants.getModifyVolumeSizeURI(volumeId);
        ScaleIOModifyVolumeSize modifyParm = new ScaleIOModifyVolumeSize();
        modifyParm.setSizeInGB(newSizeGB);
        post(URI.create(uri), getJsonForEntity(modifyParm));
        return queryVolume(volumeId);
    }

    /**
     * Create a snapshot of the volume
     * 
     * @param volId The volume ID
     * @param snapshotName The snapshot name
     * @param systemId
     * @return
     * @throws Exception
     */
    public ScaleIOSnapshotVolumeResponse snapshotVolume(String volId, String snapshotName, String systemId) throws Exception {
        String uri = ScaleIOConstants.getSnapshotVolumesURI(systemId);
        ScaleIOSnapshotVolumes spVol = new ScaleIOSnapshotVolumes();
        spVol.addSnapshot(volId, snapshotName);
        ClientResponse response = post(URI.create(uri), getJsonForEntity(spVol));
        return getResponseObject(ScaleIOSnapshotVolumeResponse.class, response);
    }

    /**
     * Create multiple snapshots in a consistency group
     * 
     * @param id2snapshot Volume ID to snapshotName map
     * @param systemId The system ID
     * @return The result of the snapshots created
     * @throws Exception
     */
    public ScaleIOSnapshotVolumeResponse snapshotMultiVolume(Map<String, String> id2snapshot, String systemId) throws Exception {
        String uri = ScaleIOConstants.getSnapshotVolumesURI(systemId);
        ScaleIOSnapshotVolumes spVol = new ScaleIOSnapshotVolumes();
        for (Map.Entry<String, String> entry : id2snapshot.entrySet()) {
            spVol.addSnapshot(entry.getKey(), entry.getValue());
        }
        ClientResponse response = post(URI.create(uri), getJsonForEntity(spVol));
        return getResponseObject(ScaleIOSnapshotVolumeResponse.class, response);
    }

    /**
     * Map volume to SDC
     * 
     * @param volumeId The volume ID
     * @param sdcId The SDC ID
     * @throws Exception
     */
    public void mapVolumeToSDC(String volumeId, String sdcId) throws Exception {
        log.info("mapping to sdc");
        String uri = ScaleIOConstants.getMapVolumeToSDCURI(volumeId);
        ScaleIOMapVolumeToSDC mapParm = new ScaleIOMapVolumeToSDC();
        mapParm.setSdcId(sdcId);
        mapParm.setAllowMultipleMappings("TRUE");
        post(URI.create(uri), getJsonForEntity(mapParm));
    }

    /**
     * Unmap the volume
     * 
     * @param volumeId The Volume ID
     * @param sdcId The SDC ID
     * @throws Exception
     */
    public void unMapVolumeToSDC(String volumeId, String sdcId) throws Exception {
        String uri = ScaleIOConstants.getUnmapVolumeToSDCURI(volumeId);
        ScaleIOUnmapVolumeToSDC unmapParm = new ScaleIOUnmapVolumeToSDC();
        unmapParm.setSdcId(sdcId);
        unmapParm.setIgnoreScsiInitiators("TRUE");
        post(URI.create(uri), getJsonForEntity(unmapParm));
    }

    /**
     * Remove snapshots, which are in a consistency group
     * 
     * @param consistencyGroupId The consistency group ID
     * @throws Exception
     */
    public void removeConsistencyGroupSnapshot(
            String consistencyGroupId) throws Exception {
        String systemId = getSystemId();
        String uri = ScaleIOConstants.getRemoveConsistencyGroupSnapshotsURI(systemId);
        ScaleIORemoveConsistencyGroupSnapshots parm = new ScaleIORemoveConsistencyGroupSnapshots();
        parm.setSnapGroupId(consistencyGroupId);
        post(URI.create(uri), getJsonForEntity(parm));
    }

    /**
     * Query all SCSI Initiators
     * 
     * @return The list of the ScaleIOScsiInitiator
     * @throws JSONException
     */
    public List<ScaleIOScsiInitiator> queryAllSCSIInitiators() throws JSONException {
        log.info("Discovery all SCSI Initiators");
        List<ScaleIOScsiInitiator> scsiInits = Collections.emptyList();
        try {
            ClientResponse response = get(URI.create(ScaleIOConstants.GET_SCSI_INITIATOR_URI));
            scsiInits = getResponseObjects(ScaleIOScsiInitiator.class, response);
        } catch (Exception e) {
            // 1.32 and later does not support get ScsiInitiators
            log.debug("This exception is expected for ScaleIO 1.32 and later, since iSCSI supported is removed. Exception:", e);
        }
        return scsiInits;
    }

    /**
     * Map the volume to SCSI Initiator
     * 
     * @param volumeId the volume ID
     * @param initiatorId The ScsiInitiator ID
     * @throws Exception
     */
    public void mapVolumeToSCSIInitiator(
            String volumeId, String initiatorId) throws Exception {
        log.info("mapping to scsi");
        String uri = ScaleIOConstants.getMapVolumeToScsiInitiatorURI(volumeId);
        ScaleIOMapVolumeToScsiInitiator mapParm = new ScaleIOMapVolumeToScsiInitiator();
        mapParm.setScsiInitiatorId(initiatorId);
        mapParm.setAllowMultipleMapp("TRUE");
        post(URI.create(uri), getJsonForEntity(mapParm));
    }

    /**
     * Unmap the volume from SCSI Initiator
     * 
     * @param volumeId The volume ID
     * @param initiatorId The ScsiInitiator ID
     * @throws Exception
     */
    public void unMapVolumeFromSCSIInitiator(String volumeId, String initiatorId) throws Exception {
        String uri = ScaleIOConstants.getUnmapVolumeToScsiInitiatorURI(volumeId);
        ScaleIOUnmapVolumeToScsiInitiator unmapParm = new ScaleIOUnmapVolumeToScsiInitiator();
        unmapParm.setScsiInitiatorId(initiatorId);
        post(URI.create(uri), getJsonForEntity(unmapParm));
    }

    /**
     * Get the system version
     * 
     * @return The version
     */
    public String getVersion() {
        String result = null;
        try {
            ScaleIOSystem system = getSystem();
            result = system.getVersion();
        } catch (Exception e) {
            log.error("Exception while getting version", e);
        }
        return result;
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.getRequestBuilder();
    }

    @Override
    protected void authenticate() {
        log.info("Authenticating");

        _client.removeAllFilters();
        _client.addFilter(new HTTPBasicAuthFilter(_username, _password));
        if (log.isDebugEnabled()) {
            _client.addFilter(new LoggingFilter(System.out));
        }
        URI requestURI = _base.resolve(URI.create(ScaleIOConstants.API_LOGIN));
        ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        if (response.getClientResponseStatus() != ClientResponse.Status.OK
                && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
            throw ScaleIOException.exceptions.authenticationFailure(_base.toString());
        }
        _authToken = response.getEntity(String.class).replace("\"", "");
        _client.removeAllFilters();
        _client.addFilter(new HTTPBasicAuthFilter(_username, _authToken));
        if (log.isDebugEnabled()) {
            _client.addFilter(new LoggingFilter(System.out));
        }
    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {
            JSONObject obj = null;
            int code = 0;
            try {
                obj = response.getEntity(JSONObject.class);
                code = obj.getInt(ScaleIOConstants.ERROR_CODE);
            } catch (Exception e) {
                log.error("Parsing the failure response object failed", e);
            }

            if (code == 404 || code == 410) {
                throw ScaleIOException.exceptions.resourceNotFound(uri.toString());
            } else if (code == 401) {
                throw ScaleIOException.exceptions.authenticationFailure(uri.toString());
            } else {
                throw ScaleIOException.exceptions.internalError(uri.toString(), obj.toString());
            }
        } else {
            return errorCode;
        }
    }

    private <T> List<T> getResponseObjects(Class<T> clazz, ClientResponse response) throws JSONException {
        JSONArray resp = response.getEntity(JSONArray.class);
        List<T> result = null;
        if (resp != null && resp.length() > 0) {
            result = new ArrayList<T>();
            for (int i = 0; i < resp.length(); i++) {
                JSONObject entry = resp.getJSONObject(i);
                T respObject = new Gson().fromJson(entry.toString(), clazz);
                result.add(respObject);
            }
        }
        return result;
    }

    /**
     * Get the system
     * 
     * @return The details of the system
     * @throws Exception
     */
    public ScaleIOSystem getSystem() throws Exception {
        ClientResponse response = get(URI.create(ScaleIOConstants.GET_SYSTEMS_URI));
        List<ScaleIOSystem> systemsInfo = getResponseObjects(ScaleIOSystem.class, response);
        return systemsInfo.get(0);
    }

    /**
     * Get protection domains in the system
     * 
     * @return The List of protection domains
     * @throws JSONException
     */
    public List<ScaleIOProtectionDomain> getProtectionDomains() throws JSONException {
        ClientResponse response = get(URI.create(ScaleIOConstants.GET_PROTECTION_DOMAIN_URI));
        return getResponseObjects(ScaleIOProtectionDomain.class, response);
    }

    /**
     * Get the storage pools in the protection domain
     * 
     * @param pdId The protection domain ID
     * @return The List of storage pools
     * @throws JSONException
     */
    public List<ScaleIOStoragePool> getProtectionDomainStoragePools(String pdId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOConstants.getProtectionDomainStoragePoolURI(pdId)));
        List<ScaleIOStoragePool> pools = getResponseObjects(ScaleIOStoragePool.class, response);
        for (ScaleIOStoragePool pool : pools) {
            ScaleIOStoragePool poolResult = getStoragePoolStats(pool.getId());
            pool.setCapacityAvailableForVolumeAllocationInKb(poolResult.getCapacityAvailableForVolumeAllocationInKb());
            pool.setMaxCapacityInKb(poolResult.getMaxCapacityInKb());
        }
        return pools;
    }

    /**
     * Get the Storage Pool detail data
     * 
     * @param poolId The storage pool ID
     * @return The details of the storage pool
     * @throws Exception
     */
    public ScaleIOStoragePool getStoragePoolStats(String poolId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOConstants.getStoragePoolStatsURI(poolId)));
        ScaleIOStoragePool pool = getResponseObject(ScaleIOStoragePool.class, response);
        pool.setId(poolId);
        return pool;
    }

    /**
     * Get the system ID
     * 
     * @return The System ID
     * @throws Exception
     */
    public String getSystemId() throws Exception {
        ClientResponse response = get(URI.create(ScaleIOConstants.GET_SYSTEMS_URI));
        List<ScaleIOSystem> systemsInfo = getResponseObjects(ScaleIOSystem.class, response);
        return systemsInfo.get(0).getId();
    }

    /**
     * Query the volume details
     * 
     * @param volId The Volume ID
     * @return The details of the volume
     * @throws Exception
     */
    public ScaleIOVolume queryVolume(String volId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOConstants.getVolumeURI(volId)));
        return getResponseObject(ScaleIOVolume.class, response);
    }

    /**
     * Get the volume names for the given volume Ids
     * 
     * @param volumeIds The list of volume IDs
     * @return The map of the volume name and volume ID
     * @throws Exception
     */
    public Map<String, String> getVolumes(List<String> volumeIds) throws Exception {
        Map<String, String> result = new HashMap<String, String>();
        ScaleIOVolumeList parm = new ScaleIOVolumeList();
        parm.setIds(volumeIds);
        ClientResponse response = post(URI.create(ScaleIOConstants.GET_VOLUMES_BYIDS_URI), getJsonForEntity(parm));
        List<ScaleIOVolume> volumes = getResponseObjects(ScaleIOVolume.class, response);
        for (ScaleIOVolume volume : volumes) {
            result.put(volume.getName(), volume.getId());
        }
        return result;
    }

    /**
     * Get the volumes for the given volume Ids
     * 
     * @param volumeIds The list of volume IDs
     * @return The map of the ScaleIO volume and volume ID
     * @throws Exception
     */
    public Map<String, ScaleIOVolume> getVolumeNameMap(List<String> volumeIds) throws Exception {
        Map<String, ScaleIOVolume> result = new HashMap<String, ScaleIOVolume>();
        ScaleIOVolumeList parm = new ScaleIOVolumeList();
        parm.setIds(volumeIds);
        ClientResponse response = post(URI.create(ScaleIOConstants.GET_VOLUMES_BYIDS_URI), getJsonForEntity(parm));
        List<ScaleIOVolume> volumes = getResponseObjects(ScaleIOVolume.class, response);
        for (ScaleIOVolume volume : volumes) {
            result.put(volume.getId(), volume);
        }
        return result;
    }

    /**
     * Get the Snapshot volume info for the given snapshot Ids
     *
     * @param snapIds The list of snapshot IDs
     * @return The map of the ScaleIO snapshot and parent volume ID
     * @throws Exception
     */
    public Map<String, ScaleIOVolume> getSnapshotParentIdMap(List<String> snapIds) throws Exception {
        Map<String, ScaleIOVolume> result = new HashMap<String, ScaleIOVolume>();
        ScaleIOVolumeList parm = new ScaleIOVolumeList();
        parm.setIds(snapIds);
        ClientResponse response = post(URI.create(ScaleIOConstants.GET_VOLUMES_BYIDS_URI), getJsonForEntity(parm));
        List<ScaleIOVolume> snapshots = getResponseObjects(ScaleIOVolume.class, response);
        for (ScaleIOVolume snapshot : snapshots) {
            result.put(snapshot.getAncestorVolumeId(), snapshot);
        }
        return result;
    }

}
