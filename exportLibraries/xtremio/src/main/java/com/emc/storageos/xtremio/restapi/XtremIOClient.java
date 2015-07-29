/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.StandardRestClient;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.*;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOConsistencyGroupRequest;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOConsistencyGroupVolumeRequest;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOFolderCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOInitiatorCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOInitiatorGroupFolderCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOLunMapCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOTagCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOV1SnapCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOV2SnapCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOVolumeCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOVolumeExpand;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOVolumeFolderCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOInitiatorGroupCreate;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOCGResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOCluster;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOClusterInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOClusters;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOFolders;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOIGFolder;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOIGFolderResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroups;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiators;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorsInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPort;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPortInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPorts;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPortsInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTagsInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumeInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumes;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumesInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMSsInfo;
import com.google.common.base.Joiner;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XtremIOClient extends StandardRestClient {

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
    public List<XtremIOSystem> getXtremIOSystemInfo() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_BASE_CLUSTERS_URI);
        log.info(response.toString());
        XtremIOClusters xioClusters = getResponseObject(XtremIOClusters.class, response);
        log.info("Returned Clusters : {}", xioClusters.getClusters().length);
        List<XtremIOSystem> discoveredXIOSystems = new ArrayList<XtremIOSystem>();
        for (XtremIOCluster cluster : xioClusters.getClusters()) {
            URI clusterURI = URI.create(cluster.getHref());
            response = get(clusterURI);
            XtremIOClusterInfo xioSystem = getResponseObject(XtremIOClusterInfo.class, response);
            log.info("System {}", xioSystem.getContent().getName() + "-"
                    + xioSystem.getContent().getSerialNumber() + "-"
                    + xioSystem.getContent().getVersion());
            discoveredXIOSystems.add(xioSystem.getContent());
        }
        return discoveredXIOSystems;
    }

    public List<XtremIOPort> getXtremIOPortInfo() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_TARGETS_URI);
        XtremIOPortsInfo targetPortLinks = getResponseObject(XtremIOPortsInfo.class, response);
        log.info("Returned Target Links size : {}", targetPortLinks.getPortInfo().length);
        List<XtremIOPort> targetPortList = new ArrayList<XtremIOPort>();
        for (XtremIOPortInfo targetPortInfo : targetPortLinks.getPortInfo()) {
            URI targetPortUri = URI.create(targetPortInfo.getHref());
            response = get(targetPortUri);
            XtremIOPorts targetPorts = getResponseObject(XtremIOPorts.class, response);
            log.info("Target Port {}", targetPorts.getContent().getName() + "-"
                    + targetPorts.getContent().getPortAddress());
            targetPortList.add(targetPorts.getContent());
        }
        return targetPortList;
    }

    public List<XtremIOInitiator> getXtremIOInitiatorsInfo() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_INITIATORS_URI);
        XtremIOInitiatorsInfo initiatorPortLinks = getResponseObject(XtremIOInitiatorsInfo.class,
                response);
        log.info("Returned Initiator Links size : {}", initiatorPortLinks.getInitiators().length);
        List<XtremIOInitiator> initiatorPortList = new ArrayList<XtremIOInitiator>();
        for (XtremIOInitiatorInfo initiatorPortInfo : initiatorPortLinks.getInitiators()) {
            URI initiatorPortUri = URI.create(initiatorPortInfo.getHref());
            response = get(initiatorPortUri);
            XtremIOInitiators initiatorPorts = getResponseObject(XtremIOInitiators.class, response);
            log.info("Initiator Port {}", initiatorPorts.getContent().getName() + "-"
                    + initiatorPorts.getContent().getPortAddress());
            initiatorPortList.add(initiatorPorts.getContent());
        }
        return initiatorPortList;
    }
    
    public List<XtremIOVolume> getXtremIOVolumes() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_VOLUMES_URI);
        XtremIOVolumesInfo volumeLinks = getResponseObject(XtremIOVolumesInfo.class, response);
        log.info("Returned Volume Links size : {}", volumeLinks.getVolumeInfo().length);
        List<XtremIOVolume> volumeList = new ArrayList<XtremIOVolume>();
        for(XtremIOVolumeInfo volumeInfo : volumeLinks.getVolumeInfo()) {
            URI volumeURI = URI.create(volumeInfo.getHref());
            response = get(volumeURI);
            XtremIOVolumes volumes = getResponseObject(XtremIOVolumes.class, response);
            log.info("Volume {}", volumes.getContent().getVolInfo().get(1) + "-" 
                    + volumes.getContent().getVolInfo().get(2));
            volumeList.add(volumes.getContent());            
        }
        
        return volumeList;
    }
    
    public List<XtremIOVolume> getXtremIOVolumesForLinks(List<XtremIOVolumeInfo> volumeLinks) throws Exception {
        List<XtremIOVolume> volumeList = new ArrayList<XtremIOVolume>();
        for(XtremIOVolumeInfo volumeInfo : volumeLinks) {
            log.debug("Trying to get volume details for {}", volumeInfo.getHref());
            try {
                URI volumeURI = URI.create(volumeInfo.getHref());
                ClientResponse response = get(volumeURI);
                XtremIOVolumes volumes = getResponseObject(XtremIOVolumes.class, response);
                log.info("Volume {}", volumes.getContent().getVolInfo().get(1) + "-" 
                        + volumes.getContent().getVolInfo().get(2));
                volumeList.add(volumes.getContent());
            } catch (InternalException ex) {
                log.warn("Exception while trying to retrieve xtremio volume link {}", volumeInfo.getHref());
            }           
        }
        
        return volumeList;
    }
    
    public List<XtremIOVolumeInfo> getXtremIOVolumeLinks() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_VOLUMES_URI);
        XtremIOVolumesInfo volumeLinks = getResponseObject(XtremIOVolumesInfo.class, response);
        
        return Arrays.asList(volumeLinks.getVolumeInfo());
    }

    /**
     * Creates a new XtremIO Volume folder
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void createVolumeFolder(String projectName, String parentFolder) throws Exception {
        try {
            XtremIOVolumeFolderCreate volumeFolderCreate = new XtremIOVolumeFolderCreate();
            volumeFolderCreate.setCaption(projectName);
            volumeFolderCreate.setParentFolderId(parentFolder);
            ClientResponse response = post(XtremIOConstants.XTREMIO_VOLUME_FOLDERS_URI,
                    getJsonForEntity(volumeFolderCreate));
            getResponseObject(XtremIOFolderCreate.class, response);
        } catch (Exception e) {
            //TODO Right now making the fix very simple ,instead of trying to acquire a lock on Storage System
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.CAPTION_NOT_UNIQUE)) {
                throw e;
            } else {
                log.warn("Volume folder {} already created by a different operation at the same time", projectName);
            }
        }
        
    }
    
    public Map<String, String> createFoldersForVolumeAndSnaps(String rootVolumeFolderName)
            throws Exception {
        
        List<String> folderNames = getVolumeFolderNames();
        log.info("Volume folder Names found on Array : {}", Joiner.on("; ").join(folderNames));
        Map<String, String> folderNamesMap = new HashMap<String, String>();
        String rootFolderName = XtremIOConstants.ROOT_FOLDER.concat(rootVolumeFolderName);
        log.info("rootVolumeFolderName: {}", rootFolderName);
        String volumesFolderName = rootFolderName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
        String snapshotsFolderName = rootFolderName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
        folderNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesFolderName);
        folderNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsFolderName);
        if (!folderNames.contains(rootFolderName)) {
            log.info("Sending create root folder request {}", rootFolderName);
            createVolumeFolder(rootVolumeFolderName, "/");
        } else {
            log.info("Found {} folder on the Array.", rootFolderName);
        }

        if (!folderNames.contains(volumesFolderName)) {
            log.info("Sending create volume folder request {}", volumesFolderName);
            createVolumeFolder("volumes", rootFolderName);
        } else {
            log.info("Found {} folder on the Array.", volumesFolderName);
        }

        if (!folderNames.contains(snapshotsFolderName)) {
            log.info("Sending create snapshot folder request {}", snapshotsFolderName);
            createVolumeFolder("snapshots", rootFolderName);
        } else {
            log.info("Found {} folder on the Array.", snapshotsFolderName);
        }

        return folderNamesMap;
    }
    
    public Map<String, String> createTagsForVolumeAndSnaps(String rootTagName)
            throws Exception {
    	List<String> tagNames = getTagNames();
        log.info("Tag Names found on Array : {}", Joiner.on("; ").join(tagNames));
        Map<String, String> tagNamesMap = new HashMap<String, String>();
        
        String rootVolumeTagName = XtremIOConstants.V2_VOLUME_ROOT_FOLDER.concat(rootTagName);
        log.info("rootTagFolderName: {}", rootTagName);
        String volumesTagName = rootVolumeTagName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
        String snapshotsTagName = rootVolumeTagName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
        tagNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesTagName);
        tagNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsTagName);
        
        if (!tagNames.contains(volumesTagName)) {
            log.info("Sending create volume tag request {}", volumesTagName);
            createTag(volumesTagName, XtremIOConstants.XTREMIO_TAG_ENTITY.Volume);
        } else {
            log.info("Found {} tag on the Array.", volumesTagName);
        }
        
        if (!tagNames.contains(snapshotsTagName)) {
            log.info("Sending create snapshot tag request {}", snapshotsTagName);
            createTag(snapshotsTagName, XtremIOConstants.XTREMIO_TAG_ENTITY.Snapshot);
        } else {
            log.info("Found {} tag on the Array.", snapshotsTagName);
        }
        
        return tagNamesMap;
        
    }
    
    public void createTag(String tagName, XtremIOConstants.XTREMIO_TAG_ENTITY entityType) throws Exception {
    	try {
    		XtremIOTagCreate tagCreate = new XtremIOTagCreate();
    		tagCreate.setEntity(entityType.name());
    		tagCreate.setTagName(tagName);
    		ClientResponse response = post(XtremIOConstants.XTREMIO_TAGS_URI,
                    getJsonForEntity(tagCreate));
    	} catch (Exception ex) {
    		log.warn("Tag  {} already available", tagName);
    	}
    }

    /**
     * Creates a new XtremIO IG Group folder
     * 
     * @param igFolderName
     * @return
     * @throws Exception
     */
    public void createInitiatorGroupFolder(String igFolderName) throws Exception {
        try {
            XtremIOInitiatorGroupFolderCreate igFolderCreate = new XtremIOInitiatorGroupFolderCreate();
            igFolderCreate.setCaption(igFolderName);
            igFolderCreate.setParentFolderId("/");
            ClientResponse response = post(XtremIOConstants.XTREMIO_INITIATOR_GROUPS_FOLDER_URI,
                    getJsonForEntity(igFolderCreate));
        } catch (Exception e) {
            log.warn("Initiator Group Folder  {} already available", igFolderName);
        }

    }

    public void deleteInitiatorGroupFolder(String igFolderName) throws Exception {
        String uriStr = XtremIOConstants.getXIOIGFolderURI(igFolderName);
        log.info("Calling Delete on uri : {}", uriStr);
        delete(URI.create(uriStr));

    }

    /**
     * Deletes a new XtremIO Volume folder
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void deleteVolumeFolder(String projectName) throws Exception {
        String uriStr = XtremIOConstants.getXIOVolumeFolderURI(projectName);
        log.info("Calling Delete on uri : {}", uriStr);
        delete(URI.create(uriStr));

    }

    public List<String> getVolumeFolderNames() throws Exception {
        List<String> folderNames = new ArrayList<String>();
        ClientResponse response = get(XtremIOConstants.XTREMIO_VOLUME_FOLDERS_URI);
        XtremIOFolderCreate responseObjs = getResponseObject(XtremIOFolderCreate.class, response);
        for (XtremIOResponseContent responseObj : responseObjs.getVolumeFolders()) {
            folderNames.add(responseObj.getName());
        }
        return folderNames;
    }
    
    public List<String> getTagNames() throws Exception {
    	List<String> tagNames = new ArrayList<String>();
    	ClientResponse response = get(XtremIOConstants.XTREMIO_TAGS_URI);
    	XtremIOTagsInfo responseObjs = getResponseObject(XtremIOTagsInfo.class, response);
    	for (XtremIOObjectInfo objectInfo : responseObjs.getTagsInfo()) {
    		tagNames.add(objectInfo.getName());
    	}
    	
    	return tagNames;
    }

    public int getNumberOfVolumesInFolder(String folderName) throws Exception {
        String uriStr = XtremIOConstants.getXIOVolumeFolderURI(folderName);
        log.info("Calling Get on uri : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOFolders folderResponse = getResponseObject(XtremIOFolders.class, response);
        log.info("{} volumes present in folder {}", folderResponse.getContent()
                .getNumberOfVolumes(), folderName);
        return Integer.parseInt(folderResponse.getContent().getNumberOfVolumes());
    }

    public int getNumberOfInitiatorsInInitiatorGroup(String igName) throws Exception {
        String uriStr = XtremIOConstants.getXIOInitiatorGroupUri(igName, null);
        log.info("Calling Get on uri : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOInitiatorGroups igResponse = getResponseObject(XtremIOInitiatorGroups.class,
                response);
        log.info("{} initaitors present in ig Group {}", igResponse.getContent()
                .getNumberOfInitiators(), igName);
        return Integer.parseInt(igResponse.getContent().getNumberOfInitiators());
    }

    public int getNumberOfVolumesInInitiatorGroup(String igName) throws Exception {
        String uriStr = XtremIOConstants.getXIOInitiatorGroupUri(igName, null);
        log.info("Calling Get on uri : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOInitiatorGroups igResponse = getResponseObject(XtremIOInitiatorGroups.class,
                response);
        log.info("{} volumes present in ig Group {}", igResponse.getContent().getNumberOfVolumes(),
                igName);
        return Integer.parseInt(igResponse.getContent().getNumberOfVolumes());
    }

    public XtremIOResponse createVolume(String volumeName, String size,
            String parentFolderName) throws Exception {
        XtremIOVolumeCreate volCreate = new XtremIOVolumeCreate();
        volCreate.setName(volumeName);
        volCreate.setSize(size);
        volCreate.setParentFolderId(parentFolderName);
        log.info("Calling Volume Create with: {}", volCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_VOLUMES_URI,
                getJsonForEntity(volCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    public XtremIOResponse createSnapshot(String parentVolumeName, String snapName,
            String folderName) throws Exception {
        XtremIOV1SnapCreate snapCreate = new XtremIOV1SnapCreate();
        snapCreate.setParentName(parentVolumeName);
        snapCreate.setSnapName(snapName);
        snapCreate.setFolderId(folderName);
        log.info("Calling Snapshot Create with URI: {} and patamaters: {}", XtremIOConstants.XTREMIO_SNAPS_URI.toString(), snapCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_SNAPS_URI,
                getJsonForEntity(snapCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }
    
    public XtremIOResponse createV2Snapshot(String clusterId, String consistencyGroupId, String snapshotSetId, String snapSuffix, 
    		String snapshotSetName, String snapshotType, List<String> tagList, List<String> volumeList) throws Exception {
        XtremIOV2SnapCreate snapCreate = new XtremIOV2SnapCreate();
        if(clusterId != null) {
        	snapCreate.setClusterId(clusterId);
        }
        if(consistencyGroupId != null) {
        	snapCreate.setConsistencyGroupId(consistencyGroupId);
        }
        if(snapshotSetId != null) {
        	snapCreate.setSnapshotSetId(snapshotSetId);
        }
        if(snapSuffix != null) {
        	snapCreate.setSnapSuffix(snapSuffix);
        }
        if(snapshotSetName != null) {
        	snapCreate.setSnapshotSetName(snapshotSetName);
        }
        if(snapshotType != null) {
        	snapCreate.setSnapshotType(snapshotType);
        }
        if(tagList != null && !tagList.isEmpty()) {
        	snapCreate.setTagList(tagList);
        }
        if(volumeList != null && !volumeList.isEmpty()) {
        	snapCreate.setVolumeList(volumeList);
        }
        log.info("Calling Snapshot Create URI: {} and patamaters: {}", XtremIOConstants.XTREMIO_V2_SNAPS_URI.toString(), snapCreate.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI,
                getJsonForEntity(snapCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    public void deleteSnapshot(String snapName) throws Exception {
        String uriStr = XtremIOConstants.getXIOSnapURI(snapName, null);
        log.info("Calling Delete on uri : {}", uriStr);
        delete(URI.create(uriStr));

    }

    public void expandVolume(String volumeName, String size) throws Exception {
        XtremIOVolumeExpand volExpand = new XtremIOVolumeExpand();
        volExpand.setSize(size);
        log.info("Calling Volume Expand with: {}", volExpand.toString());
        String volUriStr = XtremIOConstants.getXIOVolumeURI(volumeName, null);
        put(URI.create(volUriStr), getJsonForEntity(volExpand));

    }

    public XtremIOResponse createInitiator(String initiatorName, String igId,
            String portAddress) throws Exception {
        XtremIOInitiatorCreate initiatorCreate = new XtremIOInitiatorCreate();
        initiatorCreate.setInitiatorGroup(igId);
        initiatorCreate.setName(initiatorName);
        initiatorCreate.setPortAddress(portAddress);

        log.info("Calling Initiator Create with: {}", initiatorCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_INITIATORS_URI,
                getJsonForEntity(initiatorCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    /**
     * Creates a new XtremIO IG
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void createInitiatorGroup(String igName, String parentFolderId) throws Exception {
        try {
            XtremIOInitiatorGroupCreate initiatorGroupCreate = new XtremIOInitiatorGroupCreate();
            initiatorGroupCreate.setName(igName);
            initiatorGroupCreate.setParentFolderId(parentFolderId);
            post(XtremIOConstants.XTREMIO_INITIATOR_GROUPS_URI,
                    getJsonForEntity(initiatorGroupCreate));
        } catch (Exception e) {
            log.warn("Initiator Group {} already available", igName);
        }
    }

    public void createLunMap(String volName, String igName, String hlu) throws Exception {
        XtremIOLunMapCreate lunMapCreate = new XtremIOLunMapCreate();
        if (!hlu.equalsIgnoreCase("-1")) {
            lunMapCreate.setHlu(hlu);
        }
        lunMapCreate.setInitiatorGroupName(igName);
        lunMapCreate.setName(volName);
        log.info("Calling lun map Create {}", lunMapCreate.toString());
        try {
            post(XtremIOConstants.XTREMIO_LUNMAPS_URI, getJsonForEntity(lunMapCreate));
        } catch (Exception e) {
            // TODO Right now making the fix very simple ,instead of trying to acquire a lock on Storage System
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.VOLUME_MAPPED)) {
                throw e;
            } else {
                log.warn("Volume  {} already mapped to IG {}", volName, igName);
            }
        }
    }

    public XtremIOInitiator getInitiator(String initiatorName) throws Exception {
        try {
            String uriStr = XtremIOConstants.getXIOVolumeInitiatorUri(initiatorName, null);
            log.info("Calling Get Initiator with  uri : {}", uriStr);
            ClientResponse response = get(URI.create(uriStr));
            XtremIOInitiators initiators = getResponseObject(XtremIOInitiators.class, response);
            return initiators.getContent();
        } catch (Exception e) {
        	log.error(e.getMessage(),e);
        }
        log.info("Initiators not registered on Array with name : {}", initiatorName);
        return null;
    }

    public XtremIOInitiatorGroup getInitiatorGroup(String initiatorGroupName) throws Exception {
        try {
            String uriStr = XtremIOConstants.getXIOInitiatorGroupUri(initiatorGroupName, null);
            log.info("Calling Get Initiator Group with with uri : {}", uriStr);
            ClientResponse response = get(URI.create(uriStr));
            XtremIOInitiatorGroups igGroups = getResponseObject(XtremIOInitiatorGroups.class,
                    response);
            return igGroups.getContent();
        } catch (Exception e) {
        	log.error(e.getMessage(),e);
        }
        log.info("Initiator Group not registered on Array with name : {}", initiatorGroupName);
        return null;

    }

    public XtremIOIGFolder getInitiatorGroupFolder(String initiatorGroupFolderName)
            throws Exception {
        try {
            String uriStr = XtremIOConstants.getXIOIGFolderURI(initiatorGroupFolderName);
            log.info("Calling Get Initiator Group Folder with with uri : {}", uriStr);
            ClientResponse response = get(URI.create(uriStr));
            XtremIOIGFolderResponse folderResponse = getResponseObject(
                    XtremIOIGFolderResponse.class, response);
            return folderResponse.getContent();

        } catch (Exception e) {
        	log.error(e.getMessage(),e);
        }
        log.info("Initiator Group Folder not available on Array with name : {}",
                initiatorGroupFolderName);
        return null;

    }

    public void deleteInitiatorGroup(String igName) throws Exception {
        String uriStr = XtremIOConstants.getXIOInitiatorGroupUri(igName, null);
        log.info("Calling Delete Initiator Group with uri : {}", uriStr);
        delete(URI.create(uriStr));

    }

    public XtremIOVolume getVolumeDetails(String volumeName) throws Exception {
        String uriStr = XtremIOConstants.getXIOVolumeURI(volumeName, null);
        log.info("Calling Get on Volume URI : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOVolumes volumesResponse = getResponseObject(XtremIOVolumes.class, response);
        return volumesResponse.getContent();
    }

    public XtremIOVolume getSnapShotDetails(String snapName) throws Exception {
        String uriStr = XtremIOConstants.getXIOSnapURI(snapName, null);
        log.info("Calling Get on Volume URI : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOVolumes volumesResponse = getResponseObject(XtremIOVolumes.class, response);
        return volumesResponse.getContent();
    }
    
    public XtremIOConsistencyGroup getConsistencyGroupDetails(String cgName) throws Exception {
    	String uriString = XtremIOConstants.getXIOConsistencyGroupsUri(cgName, null);
    	log.info("Calling Get on Consistency Group URI : {}", uriString);
    	ClientResponse response = get(URI.create(uriString));
    	XtremIOCGResponse cgResponse = getResponseObject(XtremIOCGResponse.class, response);
    	
    	return cgResponse.getContent();
    }

    /**
     * Deletes a volume
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void deleteVolume(String volumeName) throws Exception {
        String uriStr = XtremIOConstants.getXIOVolumeURI(volumeName, null);
        log.info("Volume Delete URI : {}", uriStr);
        delete(URI.create(uriStr));
    }
    /**
     * Delete initiator
     * @param initiatorName
     * @throws Exception
     */
    public void deleteInitiator(String initiatorName) throws Exception {
    	  String uriStr = XtremIOConstants.getXIOVolumeInitiatorUri(initiatorName, null);
         log.info("Initiator Delete URI : {}", uriStr);
         delete(URI.create(uriStr));
    }
    /**
     * Deletes a lunMap
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void deleteLunMap(String lunMap) throws Exception {
        String uriStr = XtremIOConstants.getXIOLunMapUri(lunMap, null);
        log.info("Calling Delete on LunMap URI : {}", uriStr);
        delete(URI.create(uriStr));
    }
    
    public XtremIOResponse createConsistencyGroup(String cgName, String clusterName) throws Exception {
    	XtremIOConsistencyGroupRequest cgCreate = new XtremIOConsistencyGroupRequest();
        cgCreate.setCgName(cgName);
        if(clusterName != null && !clusterName.isEmpty()) {
        	cgCreate.setClusterName(clusterName);
        }
        log.info("Calling Consistency Group Create with: {}", cgCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_CONSISTENCY_GROUPS_URI,
                getJsonForEntity(cgCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }
    
    public void removeConsistencyGroup(String cgName, String clusterName) throws Exception {
    	XtremIOConsistencyGroupRequest cgRequest = new XtremIOConsistencyGroupRequest();
        cgRequest.setCgName(cgName);
        if(clusterName != null && !clusterName.isEmpty()) {
        	cgRequest.setClusterName(clusterName);
        }
      
        log.info("Calling Consistency Group Delete with: {}", cgRequest.toString());
        delete(XtremIOConstants.XTREMIO_CONSISTENCY_GROUPS_URI, 
        		getJsonForEntity(cgRequest));
    }
    
    public XtremIOResponse addVolumeToConsistencyGroup(String volName, String cgName, String clusterName) 
    		throws Exception {
    	XtremIOConsistencyGroupVolumeRequest cgVolumeRequest = new XtremIOConsistencyGroupVolumeRequest();
    	cgVolumeRequest.setCgName(cgName);
    	cgVolumeRequest.setVolName(volName);
    	if(clusterName != null && !clusterName.isEmpty()) {
    		cgVolumeRequest.setClusterName(clusterName);
    	}
    	
    	log.info("Calling Add Volume to Consistency Group with: {}", cgVolumeRequest.toString());
    	ClientResponse response = post(XtremIOConstants.XTREMIO_CONSISTENCY_GROUP_VOLUMES_URI,
    			getJsonForEntity(cgVolumeRequest));
    	
    	return getResponseObject(XtremIOResponse.class, response);
    	
    }
    
    public void removeVolumeFromConsistencyGroup(String volName, String cgName, String clusterName) 
    		throws Exception {
    	XtremIOConsistencyGroupVolumeRequest cgVolumeRequest = new XtremIOConsistencyGroupVolumeRequest();
    	cgVolumeRequest.setCgName(cgName);
    	cgVolumeRequest.setVolName(volName);
    	if(clusterName != null && !clusterName.isEmpty()) {
    		cgVolumeRequest.setClusterName(clusterName);
    	}
    	
    	log.info("Calling Remove Volume from Consistency Group with: {}", cgVolumeRequest.toString());
    	delete(XtremIOConstants.XTREMIO_CONSISTENCY_GROUP_VOLUMES_URI, 
    			getJsonForEntity(cgVolumeRequest));
    }
    
    public boolean isVersion2() {
    	boolean isV2 = false;
    	try {
	    	ClientResponse response = get(XtremIOConstants.XTREMIO_XMS_URI);
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
    
    protected WebResource.Builder setResourceHeaders(WebResource resource) {
        return resource.header(XtremIOConstants.AUTH_TOKEN, _authToken);
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
