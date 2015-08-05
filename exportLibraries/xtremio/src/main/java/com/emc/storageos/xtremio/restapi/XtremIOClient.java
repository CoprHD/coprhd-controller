/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.restutil.StandardRestClient;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.XtremIOAuthInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOIGFolder;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPort;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumeInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMS;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMSsInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public abstract class XtremIOClient extends StandardRestClient {

    private static Logger log = LoggerFactory.getLogger(XtremIOClient.class);

    /**
     * Constructor
     * 
     * @param client
     *            A reference to a Jersey Apache HTTP client.
     * @param username
     *            The user to be authenticated.
     * @param password
     *            The user password for authentication.
     */
    public XtremIOClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    /**
     * Get information on all the clusters configured. TODO Need to find out whether multiple
     * clusters gets translated to multiple Systems.
     * 
     * @return
     * @throws Exception
     */
    public abstract List<XtremIOSystem> getXtremIOSystemInfo() throws Exception;
    
    public abstract XtremIOXMS getXtremIOXMSInfo() throws Exception;

    public abstract List<XtremIOPort> getXtremIOPortInfo(String clusterName) throws Exception;
    
    public abstract List<XtremIOInitiator> getXtremIOInitiatorsInfo(String clusterName) throws Exception;
    
    public abstract List<XtremIOVolume> getXtremIOVolumes(String clusterName) throws Exception;

    public abstract List<XtremIOVolume> getXtremIOVolumesForLinks(List<XtremIOVolumeInfo> volumeLinks, String clusterName) 
            throws Exception;
    
    public abstract List<XtremIOVolumeInfo> getXtremIOVolumeLinks(String clusterName) throws Exception;

    /**
     * Creates a new XtremIO Volume folder
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public abstract void createVolumeFolder(String projectName, String parentFolder) throws Exception;
    
    public abstract void createTag(String tagName, XtremIOConstants.XTREMIO_TAG_ENTITY entityType, String clusterName) throws Exception;

    /**
     * Creates a new XtremIO IG Group folder
     * 
     * @param igFolderName
     * @return
     * @throws Exception
     */
    public abstract void createInitiatorGroupFolder(String igFolderName, String clusterName) throws Exception;

    public abstract void deleteInitiatorGroupFolder(String igFolderName, String clusterName) throws Exception;

    /**
     * Deletes a new XtremIO Volume folder
     * 
     * @param folderName
     * @return
     * @throws Exception
     */
    public abstract void deleteVolumeFolder(String folderName, String clusterName) throws Exception;

    public abstract List<String> getVolumeFolderNames() throws Exception;
    
    public abstract List<String> getTagNames(String clusterName) throws Exception;

    public abstract int getNumberOfVolumesInFolder(String folderName, String clusterName) throws Exception;

    public abstract int getNumberOfInitiatorsInInitiatorGroup(String igName, String clusterName) throws Exception;

    public abstract int getNumberOfVolumesInInitiatorGroup(String igName, String clusterName) throws Exception;

    public abstract XtremIOResponse createVolume(String volumeName, String size, String parentFolderName, String clusterName)
            throws Exception;

    public abstract XtremIOResponse createVolumeSnapshot(String parentVolumeName, String snapName, String folderName, String snapType, 
            String clusterName) throws Exception;
    
    public abstract XtremIOResponse createConsistencyGroupSnapshot(String consistencyGroupName, String snapshotSetName, String folderName, 
            String snapType, String clusterName) throws Exception;

    public abstract void deleteSnapshot(String snapName, String clusterName) throws Exception;

    public abstract void expandVolume(String volumeName, String size, String clusterName) throws Exception;

    public abstract XtremIOResponse createInitiator(String initiatorName, String igId, String portAddress, String clusterName)
            throws Exception;

    /**
     * Creates a new XtremIO IG
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public abstract void createInitiatorGroup(String igName, String parentFolderId, String clusterName) throws Exception;

    public abstract void createLunMap(String volName, String igName, String hlu, String clusterName) throws Exception;

    public abstract XtremIOInitiator getInitiator(String initiatorName, String clusterName) throws Exception;

    public abstract XtremIOInitiatorGroup getInitiatorGroup(String initiatorGroupName, String clusterName) throws Exception;

    public abstract XtremIOIGFolder getInitiatorGroupFolder(String initiatorGroupFolderName, String clusterName)
            throws Exception;

    public abstract void deleteInitiatorGroup(String igName, String clusterName) throws Exception;

    public abstract XtremIOVolume getVolumeDetails(String volumeName, String clusterName) throws Exception;

    public abstract XtremIOVolume getSnapShotDetails(String snapName, String clusterName) throws Exception;
    
    public abstract XtremIOConsistencyGroup getConsistencyGroupDetails(String cgName, String clusterName) throws Exception;
    
    public abstract XtremIOSystem getClusterDetails(String clusterSerialNumber) throws Exception;

    /**
     * Deletes a volume
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public abstract void deleteVolume(String volumeName, String clusterName) throws Exception;

    /**
     * Delete initiator
     * 
     * @param initiatorName
     * @throws Exception
     */
    public abstract void deleteInitiator(String initiatorName, String clusterName) throws Exception;

    /**
     * Deletes a lunMap
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public abstract void deleteLunMap(String lunMap, String clusterName) throws Exception;
    
    public abstract XtremIOResponse createConsistencyGroup(String cgName, String clusterName) throws Exception;
    
    public abstract void removeConsistencyGroup(String cgName, String clusterName) throws Exception;
    
    public abstract XtremIOResponse addVolumeToConsistencyGroup(String volName, String cgName, String clusterName) 
    		throws Exception;
    
    public abstract void removeVolumeFromConsistencyGroup(String volName, String cgName, String clusterName) 
    		throws Exception;
    
    public abstract void deleteSnapshotSet(String snapshotSetName, String clusterName) throws Exception;
    
    public abstract XtremIOResponse restoreVolumeFromSnapshot(String clusterName, String volName, String snapshotName) throws Exception;
    
    public abstract XtremIOResponse refreshSnapshotFromVolume(String clusterName, String volName, String snapshotName) throws Exception;
    
    public abstract XtremIOResponse restoreCGFromSnapshot(String clusterName, String cgName, String snapshotName) throws Exception;
    
    public abstract XtremIOResponse refreshSnapshotFromCG(String clusterName, String cgName, String snapshotName) throws Exception;
    
    protected WebResource.Builder setResourceHeaders(WebResource resource) {
        return resource.header(XtremIOConstants.AUTH_TOKEN, _authToken);
    }
    
    public boolean isVersion2() {
        boolean isV2 = false;
        try {
            ClientResponse response = get(XtremIOConstants.XTREMIO_V2_XMS_URI);
            XtremIOXMSsInfo xmssInfo = getResponseObject(XtremIOXMSsInfo.class, response);
            for(XtremIOObjectInfo xmsInfo : xmssInfo.getXmssInfo()) {
                URI xmsURI = URI.create(xmsInfo.getHref().concat(XtremIOConstants.XTREMIO_XMS_FILTER_STR));
                response = get(xmsURI);
                if(response.getClientResponseStatus() != ClientResponse.Status.OK) {
                    isV2 = false;
                } else {
                    isV2 = true;
                }
            }
        } catch (Exception ex) {
            log.error("Error retrieving xms version info", ex);
            isV2 = false;
        }
        
        return isV2;
    }
    
    protected int checkResponse(URI uri, ClientResponse response) throws XtremIOApiException {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {
            JSONObject obj = null;
            int xtremIOCode = 0;
            try {
                obj = response.getEntity(JSONObject.class);
                xtremIOCode = obj.getInt(XtremIOConstants.ERROR_CODE);
            } catch (Exception e) {
                log.error("Parsing the failure response object failed", e);
            }

            if (xtremIOCode == 404 || xtremIOCode == 410) {
                throw XtremIOApiException.exceptions.resourceNotFound(uri.toString());
            } else if (xtremIOCode == 401) {
                throw XtremIOApiException.exceptions.authenticationFailure(uri.toString());
            } else {
                throw XtremIOApiException.exceptions.internalError(uri.toString(), obj.toString());
            }
        } else {
            return errorCode;
        }
    }

    protected void authenticate() throws XtremIOApiException {
        try {
            XtremIOAuthInfo authInfo = new XtremIOAuthInfo();
            authInfo.setPassword(_password);
            authInfo.setUsername(_username);

            String body = getJsonForEntity(authInfo);

            URI requestURI = _base.resolve(URI.create(XtremIOConstants.XTREMIO_BASE_STR));
            ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, body);

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw XtremIOApiException.exceptions.authenticationFailure(_base.toString());
            }
            _authToken = response.getHeaders().getFirst(XtremIOConstants.AUTH_TOKEN_HEADER);
        } catch (Exception e) {
            throw XtremIOApiException.exceptions.authenticationFailure(_base.toString());
        }
    }
}
