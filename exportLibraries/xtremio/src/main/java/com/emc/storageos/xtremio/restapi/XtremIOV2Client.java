/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.httpclient.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOConsistencyGroupRequest;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOConsistencyGroupVolumeRequest;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOInitiatorCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOInitiatorGroupCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOLunMapCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOSnapCreateAndReassign;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOTagRequest;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOV2SnapCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOVolumeCreate;
import com.emc.storageos.xtremio.restapi.model.request.XtremIOVolumeExpand;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOCGResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOCluster;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOClusterInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOClusters;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroupVolInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroupVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroups;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiators;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorsInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPort;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPorts;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPortsInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTags;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTagsInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumes;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumesInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMSResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMSsInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

public class XtremIOV2Client extends XtremIOClient {

    private static Logger log = LoggerFactory.getLogger(XtremIOV2Client.class);

    public XtremIOV2Client(URI baseURI, String username, String password, Client client) {
        super(baseURI, username, password, client);
    }

    @Override
    public List<XtremIOSystem> getXtremIOSystemInfo() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_V2_BASE_CLUSTERS_URI);
        log.info(response.toString());
        XtremIOClusters xioClusters = getResponseObject(XtremIOClusters.class, response);
        log.info("Returned Clusters : {}", xioClusters.getClusters().length);
        List<XtremIOSystem> discoveredXIOSystems = new ArrayList<XtremIOSystem>();
        for (XtremIOCluster cluster : xioClusters.getClusters()) {
            URI clusterURI = URI.create(URIUtil.getFromPath(cluster.getHref()));
            log.debug("Trying to get cluster details for {}", clusterURI.toString());
            response = get(clusterURI);
            XtremIOClusterInfo xioSystem = getResponseObject(XtremIOClusterInfo.class, response);
            log.info("System {}", xioSystem.getContent().getName() + "-"
                    + xioSystem.getContent().getSerialNumber() + "-"
                    + xioSystem.getContent().getVersion());
            discoveredXIOSystems.add(xioSystem.getContent());
        }
        return discoveredXIOSystems;
    }

    @Override
    public List<XtremIOPort> getXtremIOPortInfo(String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_TARGETS_STR.concat(XtremIOConstants.getInputClusterString(clusterName));
        ClientResponse response = get(URI.create(uriString));
        XtremIOPortsInfo targetPortLinks = getResponseObject(XtremIOPortsInfo.class, response);
        log.info("Returned Target Links size : {}", targetPortLinks.getPortInfo().length);
        List<XtremIOPort> targetPortList = new ArrayList<XtremIOPort>();
        for (XtremIOObjectInfo targetPortInfo : targetPortLinks.getPortInfo()) {
            URI targetPortUri = URI.create(URIUtil.getFromPath(targetPortInfo.getHref().concat(
                    XtremIOConstants.getInputClusterString(clusterName))));
            log.debug("Trying to get port details for {}", targetPortUri.toString());
            response = get(targetPortUri);
            XtremIOPorts targetPorts = getResponseObject(XtremIOPorts.class, response);
            log.info("Target Port {}", targetPorts.getContent().getName() + "-"
                    + targetPorts.getContent().getPortAddress());
            targetPortList.add(targetPorts.getContent());
        }
        return targetPortList;
    }

    @Override
    public List<XtremIOInitiator> getXtremIOInitiatorsInfo(String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_INITIATORS_STR.concat(XtremIOConstants.getInputClusterString(clusterName));
        ClientResponse response = get(URI.create(uriString));
        XtremIOInitiatorsInfo initiatorPortLinks = getResponseObject(XtremIOInitiatorsInfo.class,
                response);
        log.info("Returned Initiator Links size : {}", initiatorPortLinks.getInitiators().length);
        List<XtremIOInitiator> initiatorPortList = new ArrayList<XtremIOInitiator>();
        for (XtremIOObjectInfo initiatorPortInfo : initiatorPortLinks.getInitiators()) {
            URI initiatorPortUri = URI.create(URIUtil.getFromPath(initiatorPortInfo.getHref().concat(
                    XtremIOConstants.getInputClusterString(clusterName))));
            log.debug("Trying to get initiator details for {}", initiatorPortUri.toString());
            response = get(initiatorPortUri);
            XtremIOInitiators initiatorPorts = getResponseObject(XtremIOInitiators.class, response);
            log.info("Initiator Port {}", initiatorPorts.getContent().getName() + "-"
                    + initiatorPorts.getContent().getPortAddress());
            initiatorPortList.add(initiatorPorts.getContent());
        }
        return initiatorPortList;
    }

    @Override
    public List<XtremIOVolume> getXtremIOVolumes(String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_VOLUMES_STR.concat(XtremIOConstants.getInputClusterString(clusterName));
        ClientResponse response = get(URI.create(uriString));
        XtremIOVolumesInfo volumeLinks = getResponseObject(XtremIOVolumesInfo.class, response);
        log.info("Returned Volume Links size : {}", volumeLinks.getVolumeInfo().length);
        List<XtremIOVolume> volumeList = getXtremIOVolumesForLinks(Arrays.asList(volumeLinks.getVolumeInfo()), clusterName);

        return volumeList;
    }

    @Override
    public List<XtremIOVolume> getXtremIOVolumesForLinks(List<XtremIOObjectInfo> volumeLinks, String clusterName) throws Exception {
        List<XtremIOVolume> volumeList = new ArrayList<XtremIOVolume>();
        for (XtremIOObjectInfo volumeInfo : volumeLinks) {
            try {
                URI volumeURI = URI.create(URIUtil.getFromPath(volumeInfo.getHref().concat(
                        XtremIOConstants.getInputClusterString(clusterName))));
                log.debug("Trying to get volume details for {}", volumeURI.toString());
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

    @Override
    public XtremIOConsistencyGroupVolInfo getXtremIOConsistencyGroupInfo(XtremIOObjectInfo cgVolume, String clusterName) throws Exception {
        log.debug("Trying to get ConsistencyGroup details for {}", cgVolume.getHref());
        XtremIOConsistencyGroupVolInfo cgInfo = new XtremIOConsistencyGroupVolInfo();
        try {
            URI cgURI = URI.create(URIUtil.getFromPath(cgVolume.getHref().concat(XtremIOConstants.getInputClusterString(clusterName))));
            ClientResponse response = get(cgURI);
            cgInfo = getResponseObject(XtremIOConsistencyGroupVolInfo.class, response);
            log.info("ConsistencyGroup {}", cgInfo.getContent().getName() + " has " + cgInfo.getContent().getNumOfVols() + " Volumes");
        } catch (InternalException ex) {
            log.warn("Exception while trying to retrieve xtremio Consistency Group Info {}", cgVolume.getHref());
        }

        return cgInfo;
    }

    @Override
    public List<XtremIOObjectInfo> getXtremIOVolumeLinks(String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_VOLUMES_STR.concat(XtremIOConstants.getInputClusterString(clusterName));
        ClientResponse response = get(URI.create(uriString));
        XtremIOVolumesInfo volumeLinks = getResponseObject(XtremIOVolumesInfo.class, response);

        return Arrays.asList(volumeLinks.getVolumeInfo());
    }

    @Override
    public List<XtremIOObjectInfo> getXtremIOConsistencyGroups(String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_CONSISTENCY_GROUP_VOLUMES_STR.concat(XtremIOConstants
                .getInputClusterString(clusterName));
        ClientResponse response = get(URI.create(uriString));
        XtremIOConsistencyGroupVolume cgLinks = getResponseObject(XtremIOConsistencyGroupVolume.class, response);

        return Arrays.asList(cgLinks.getConsitencyGroups());
    }

    @Override
    public void createTag(String tagName, String parentTag, String entityType, String clusterName) throws Exception {
        try {
            XtremIOTagRequest tagCreate = new XtremIOTagRequest();
            tagCreate.setEntity(entityType);
            tagCreate.setTagName(tagName);
            ClientResponse response = post(XtremIOConstants.XTREMIO_V2_TAGS_URI,
                    getJsonForEntity(tagCreate));
        } catch (Exception ex) {
            log.warn("Tag  {} already available", tagName);
        }
    }

    @Override
    public List<String> getVolumeFolderNames() throws Exception {
        throw XtremIOApiException.exceptions.operationNotSupportedForVersion("createInitiatorGroupFolder");
    }

    @Override
    public List<String> getTagNames(String clusterName) throws Exception {
        // No need to throw exception if we are not able to get tag names.
        List<String> tagNames = new ArrayList<String>();
        try {
            ClientResponse response = get(XtremIOConstants.XTREMIO_V2_TAGS_URI);
            XtremIOTagsInfo responseObjs = getResponseObject(XtremIOTagsInfo.class, response);
            for (XtremIOObjectInfo objectInfo : responseObjs.getTagsInfo()) {
                tagNames.add(objectInfo.getName());
            }
        } catch (Exception ex) {
            log.warn("Error getting tag names", ex.getMessage());
        }
        return tagNames;
    }

    @Override
    public XtremIOResponse createVolume(String volumeName, String size, String parentFolderName, String clusterName) throws Exception {
        XtremIOVolumeCreate volCreate = new XtremIOVolumeCreate();
        volCreate.setName(volumeName);
        volCreate.setSize(size);
        volCreate.setClusterName(clusterName);

        log.info("Calling Volume Create with: {}", volCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_VOLUMES_URI, getJsonForEntity(volCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public XtremIOResponse createVolumeSnapshot(String parentVolumeName, String snapName, String folderName, String snapType,
            String clusterName) throws Exception {
        XtremIOV2SnapCreate snapCreate = new XtremIOV2SnapCreate();
        snapCreate.setClusterId(clusterName);
        List<String> volumes = new ArrayList<String>();
        volumes.add(parentVolumeName);
        snapCreate.setVolumeList(volumes);
        snapCreate.setSnapshotSetName(snapName);
        snapCreate.setSnapshotType(snapType);
        log.info("Calling Snapshot Create URI: {} and paramaters: {}", XtremIOConstants.XTREMIO_V2_SNAPS_URI.toString(),
                snapCreate.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI, getJsonForEntity(snapCreate));

        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public void tagObject(String tagName, String entityType, String entity, String clusterName) throws Exception {
        // No need to throw exception if we are not able to tag objects.
        try {
            String uriString = XtremIOConstants.XTREMIO_V2_TAGS_STR.concat(XtremIOConstants.getInputNameString(tagName));
            XtremIOTagRequest tagRequest = new XtremIOTagRequest();
            tagRequest.setEntity(entityType);
            tagRequest.setEntityDetails(entity);
            tagRequest.setClusterId(clusterName);
            log.info("Calling tag object with URI: {} and parameters: {}", uriString, tagRequest.toString());
            ClientResponse response = put(URI.create(uriString), getJsonForEntity(tagRequest));
        } catch (Exception ex) {
            log.warn("Error tagging object {} with tag {}", entity, tagName);
        }
    }

    @Override
    public XtremIOResponse createConsistencyGroupSnapshot(String consistencyGroupName, String snapshotSetName, String folderName,
            String snapType, String clusterName) throws Exception {
        XtremIOV2SnapCreate snapCreate = new XtremIOV2SnapCreate();
        snapCreate.setClusterId(clusterName);
        snapCreate.setConsistencyGroupId(consistencyGroupName);
        snapCreate.setSnapshotSetName(snapshotSetName);
        snapCreate.setSnapshotType(snapType);
        log.info("Calling Snapshot Create URI: {} and paramaters: {}", XtremIOConstants.XTREMIO_V2_SNAPS_URI.toString(),
                snapCreate.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI, getJsonForEntity(snapCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public void deleteSnapshot(String snapName, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_SNAPS_STR.concat(XtremIOConstants.getInputNameForClusterString(snapName, clusterName));
        log.info("Calling Delete on uri : {}", uriStr);
        delete(URI.create(uriStr));
    }

    @Override
    public void expandVolume(String volumeName, String size, String clusterName) throws Exception {
        XtremIOVolumeExpand volExpand = new XtremIOVolumeExpand();
        volExpand.setSize(size);
        volExpand.setClusterName(clusterName);
        log.info("Calling Volume Expand with: {}", volExpand.toString());
        String volUriStr = XtremIOConstants.XTREMIO_V2_VOLUMES_STR.concat(XtremIOConstants.getInputNameString(volumeName));
        put(URI.create(volUriStr), getJsonForEntity(volExpand));
    }

    @Override
    public XtremIOResponse createInitiator(String initiatorName, String igId, String portAddress, String os, String clusterName)
            throws Exception {
        XtremIOInitiatorCreate initiatorCreate = new XtremIOInitiatorCreate();
        initiatorCreate.setClusterName(clusterName);
        initiatorCreate.setInitiatorGroup(igId);
        initiatorCreate.setName(initiatorName);
        initiatorCreate.setPortAddress(portAddress);
        if (null != os) {
            initiatorCreate.setOperatingSystem(os);
        }

        log.info("Calling Initiator Create with: {}", initiatorCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_INITIATORS_URI,
                getJsonForEntity(initiatorCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public void createInitiatorGroup(String igName, String parentFolderId, String clusterName) throws Exception {
        try {
            XtremIOInitiatorGroupCreate initiatorGroupCreate = new XtremIOInitiatorGroupCreate();
            initiatorGroupCreate.setClusterName(clusterName);
            initiatorGroupCreate.setName(igName);
            List<String> tags = new ArrayList<String>();
            tags.add(XtremIOConstants.V2_INITIATOR_GROUP_ROOT_FOLDER.concat(parentFolderId));
            initiatorGroupCreate.setTagList(tags);
            post(XtremIOConstants.XTREMIO_V2_INITIATOR_GROUPS_URI,
                    getJsonForEntity(initiatorGroupCreate));
        } catch (Exception e) {
            log.warn("Initiator Group {} already available", igName);
        }
    }

    @Override
    public void createLunMap(String volName, String igName, String hlu, String clusterName) throws Exception {
        XtremIOLunMapCreate lunMapCreate = new XtremIOLunMapCreate();
        if (!hlu.equalsIgnoreCase("-1")) {
            lunMapCreate.setHlu(hlu);
        }
        lunMapCreate.setInitiatorGroupName(igName);
        lunMapCreate.setName(volName);
        lunMapCreate.setClusterName(clusterName);
        log.info("Calling lun map Create {}", lunMapCreate.toString());
        try {
            post(XtremIOConstants.XTREMIO_V2_LUNMAPS_URI, getJsonForEntity(lunMapCreate));
        } catch (Exception e) {
            // TODO Right now making the fix very simple ,instead of trying to acquire a lock on Storage System
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.VOLUME_MAPPED)) {
                throw e;
            } else {
                log.warn("Volume  {} already mapped to IG {}", volName, igName);
            }
        }
    }

    @Override
    public XtremIOInitiator getInitiator(String initiatorName, String clusterName) throws Exception {
        try {
            String uriStr = XtremIOConstants.XTREMIO_V2_INITIATORS_STR.concat(
                    XtremIOConstants.getInputNameForClusterString(initiatorName, clusterName));
            log.info("Calling Get Initiator with  uri : {}", uriStr);
            ClientResponse response = get(URI.create(uriStr));
            XtremIOInitiators initiators = getResponseObject(XtremIOInitiators.class, response);
            return initiators.getContent();
        } catch (Exception e) {
            // No need to log this message at error level.
            log.warn(e.getMessage(), e);
        }
        log.info("Initiators not registered on Array with name : {}", initiatorName);
        return null;
    }

    @Override
    public XtremIOInitiatorGroup getInitiatorGroup(String initiatorGroupName, String clusterName) throws Exception {
        try {
            String uriStr = XtremIOConstants.XTREMIO_V2_INITIATOR_GROUPS_STR.concat(
                    XtremIOConstants.getInputNameForClusterString(initiatorGroupName, clusterName));
            log.info("Calling Get Initiator Group with with uri : {}", uriStr);
            ClientResponse response = get(URI.create(uriStr));
            XtremIOInitiatorGroups igGroups = getResponseObject(XtremIOInitiatorGroups.class,
                    response);
            return igGroups.getContent();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        log.info("Initiator Group not registered on Array with name : {}", initiatorGroupName);
        return null;
    }

    @Override
    public void deleteInitiatorGroup(String igName, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_INITIATOR_GROUPS_STR.concat(
                XtremIOConstants.getInputNameForClusterString(igName, clusterName));
        log.info("Calling Delete Initiator Group with uri : {}", uriStr);
        delete(URI.create(uriStr));
    }

    @Override
    public XtremIOVolume getVolumeDetails(String volumeName, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_VOLUMES_STR.concat(
                XtremIOConstants.getInputNameForClusterString(volumeName, clusterName));
        log.info("Calling Get on Volume URI : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOVolumes volumesResponse = getResponseObject(XtremIOVolumes.class, response);
        return volumesResponse.getContent();
    }

    @Override
    public XtremIOVolume getSnapShotDetails(String snapName, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_SNAPS_STR.concat(
                XtremIOConstants.getInputNameForClusterString(snapName, clusterName));
        log.info("Calling Get on Snapshot URI : {}", uriStr);
        ClientResponse response = get(URI.create(uriStr));
        XtremIOVolumes volumesResponse = getResponseObject(XtremIOVolumes.class, response);
        return volumesResponse.getContent();
    }

    @Override
    public XtremIOConsistencyGroup getConsistencyGroupDetails(String cgName, String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_CONSISTENCY_GROUPS_STR.concat(
                XtremIOConstants.getInputNameForClusterString(cgName, clusterName));
        log.info("Calling Get on Consistency Group URI : {}", uriString);
        ClientResponse response = get(URI.create(uriString));
        XtremIOCGResponse cgResponse = getResponseObject(XtremIOCGResponse.class, response);

        return cgResponse.getContent();
    }

    @Override
    public void deleteVolume(String volumeName, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_VOLUMES_STR.concat(
                XtremIOConstants.getInputNameForClusterString(volumeName, clusterName));
        log.info("Volume Delete URI : {}", uriStr);
        delete(URI.create(uriStr));
    }

    @Override
    public void deleteInitiator(String initiatorName, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_INITIATORS_STR.concat(
                XtremIOConstants.getInputNameForClusterString(initiatorName, clusterName));
        log.info("Initiator Delete URI : {}", uriStr);
        delete(URI.create(uriStr));
    }

    @Override
    public void deleteLunMap(String lunMap, String clusterName) throws Exception {
        String uriStr = XtremIOConstants.XTREMIO_V2_LUNMAPS_STR.concat(
                XtremIOConstants.getInputNameForClusterString(lunMap, clusterName));
        log.info("Calling Delete on LunMap URI : {}", uriStr);
        delete(URI.create(uriStr));
    }

    @Override
    public XtremIOResponse createConsistencyGroup(String cgName, String clusterName) throws Exception {
        XtremIOConsistencyGroupRequest cgCreate = new XtremIOConsistencyGroupRequest();
        cgCreate.setCgName(cgName);
        cgCreate.setClusterName(clusterName);
        log.info("Calling Consistency Group Create with: {}", cgCreate.toString());

        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_CONSISTENCY_GROUPS_URI,
                getJsonForEntity(cgCreate));
        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public void removeConsistencyGroup(String cgName, String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_CONSISTENCY_GROUPS_STR
                .concat(XtremIOConstants.getInputNameForClusterString(cgName, clusterName));
        log.info("Calling Consistency Group Delete with: {}", uriString);
        delete(URI.create(uriString));
    }

    @Override
    public XtremIOResponse addVolumeToConsistencyGroup(String volName, String cgName, String clusterName) throws Exception {
        XtremIOConsistencyGroupVolumeRequest cgVolumeRequest = new XtremIOConsistencyGroupVolumeRequest();
        cgVolumeRequest.setCgName(cgName);
        cgVolumeRequest.setVolName(volName);
        cgVolumeRequest.setClusterName(clusterName);

        log.info("Calling Add Volume to Consistency Group with: {}", cgVolumeRequest.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_CONSISTENCY_GROUP_VOLUMES_URI,
                getJsonForEntity(cgVolumeRequest));

        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public void removeVolumeFromConsistencyGroup(String volName, String cgName, String clusterName) throws Exception {
        XtremIOConsistencyGroupVolumeRequest cgVolumeRequest = new XtremIOConsistencyGroupVolumeRequest();
        cgVolumeRequest.setCgName(cgName);
        cgVolumeRequest.setVolName(volName);
        cgVolumeRequest.setClusterName(clusterName);

        String uriString = XtremIOConstants.XTREMIO_V2_CONSISTENCY_GROUP_VOLUMES_STR
                .concat(XtremIOConstants.getInputNameString(cgName));
        log.info("Calling Remove Volume from Consistency Group with: {} and parameters {}", uriString, cgVolumeRequest.toString());
        delete(URI.create(uriString), getJsonForEntity(cgVolumeRequest));
    }

    @Override
    public void deleteSnapshotSet(String snapshotSetName, String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_SNAPSHOT_SET_STR
                .concat(XtremIOConstants.getInputNameForClusterString(snapshotSetName, clusterName));

        URI deleteURI = URI.create(uriString);
        log.info("Calling Snapshot Set Delete with: {}", deleteURI.toString());
        delete(deleteURI);
    }

    @Override
    public XtremIOResponse restoreVolumeFromSnapshot(String clusterName, String volName, String snapshotName) throws Exception {
        XtremIOSnapCreateAndReassign restoreParam = new XtremIOSnapCreateAndReassign();
        restoreParam.setClusterId(clusterName);
        // If no-backup is false, then snapshot of snapshot to restore from is created.
        // We don't support ingestion of such snaps. So mark it as true
        restoreParam.setNoBackup(Boolean.TRUE.toString());
        restoreParam.setToVolumeId(volName);
        restoreParam.setFromVolumeId(snapshotName);

        log.info("Calling restore Volume from snapshot with: {}", restoreParam.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI,
                getJsonForEntity(restoreParam));

        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public XtremIOResponse refreshSnapshotFromVolume(String clusterName, String volName, String snapshotName) throws Exception {
        XtremIOSnapCreateAndReassign refreshParam = new XtremIOSnapCreateAndReassign();
        refreshParam.setClusterId(clusterName);
        // If no-backup is false, then snapshot of snapshot to refresh is created.
        // We don't support ingestion of such snaps. So mark it as true
        refreshParam.setNoBackup(Boolean.TRUE.toString());
        refreshParam.setFromVolumeId(volName);
        refreshParam.setToVolumeId(snapshotName);

        log.info("Calling refresh snapshot with: {}", refreshParam.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI,
                getJsonForEntity(refreshParam));

        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public XtremIOResponse restoreCGFromSnapshot(String clusterName, String cgName, String snapshotName) throws Exception {
        XtremIOSnapCreateAndReassign restoreParam = new XtremIOSnapCreateAndReassign();
        restoreParam.setClusterId(clusterName);
        // If no-backup is false, then snapshot of snapshot to restore from is created.
        // We don't support ingestion of such snaps. So mark it as true
        restoreParam.setNoBackup(Boolean.TRUE.toString());
        restoreParam.setToConsistencyGroupId(cgName);
        restoreParam.setFromSnapshotSetId(snapshotName);

        log.info("Calling restore CG from snapshot with: {}", restoreParam.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI,
                getJsonForEntity(restoreParam));

        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public XtremIOResponse refreshSnapshotFromCG(String clusterName, String cgName, String snapshotName) throws Exception {
        XtremIOSnapCreateAndReassign refreshParam = new XtremIOSnapCreateAndReassign();
        refreshParam.setClusterId(clusterName);
        // If no-backup is false, then a snapshot of snapshot to refresh is created.
        // We don't support ingestion of such snaps. So mark it as true
        refreshParam.setFromConsistencyGroupId(cgName);
        refreshParam.setToSnapshotSetId(snapshotName);

        log.info("Calling refresh snapshot with: {}", refreshParam.toString());
        ClientResponse response = post(XtremIOConstants.XTREMIO_V2_SNAPS_URI,
                getJsonForEntity(refreshParam));

        return getResponseObject(XtremIOResponse.class, response);
    }

    @Override
    public String getXtremIOXMSVersion() throws Exception {
        ClientResponse response = get(XtremIOConstants.XTREMIO_V2_XMS_URI);
        XtremIOXMSsInfo xmssInfo = getResponseObject(XtremIOXMSsInfo.class, response);
        for (XtremIOObjectInfo xmsInfo : xmssInfo.getXmssInfo()) {
            URI xmsURI = URI.create(URIUtil.getFromPath(xmsInfo.getHref()));
            log.debug("Trying to get xms details for {}", xmsURI.toString());
            response = get(xmsURI);
            XtremIOXMSResponse xmsResponse = getResponseObject(XtremIOXMSResponse.class, response);
            return xmsResponse.getContent().getVersion();
        }
        return null;
    }

    @Override
    public XtremIOSystem getClusterDetails(String clusterSerialNumber) throws Exception {
        String filterString = String.format(XtremIOConstants.XTREMIO_CLUSTER_FILTER_STR, clusterSerialNumber);
        String uriString = XtremIOConstants.XTREMIO_V2_BASE_CLUSTERS_STR.concat(filterString);
        log.info("Calling get cluster details with: {}", uriString);
        ClientResponse response = get(URI.create(uriString));
        log.info(response.toString());
        XtremIOClusters xioClusters = getResponseObject(XtremIOClusters.class, response);
        log.info("Returned Clusters : {}", xioClusters.getClusters().length);
        for (XtremIOCluster cluster : xioClusters.getClusters()) {
            URI clusterURI = URI.create(URIUtil.getFromPath(cluster.getHref()));
            log.debug("Trying to get cluster details for {}", clusterURI.toString());
            response = get(clusterURI);
            XtremIOClusterInfo xioSystem = getResponseObject(XtremIOClusterInfo.class, response);
            log.info("System {}", xioSystem.getContent().getName() + "-"
                    + xioSystem.getContent().getSerialNumber() + "-"
                    + xioSystem.getContent().getVersion());
            return xioSystem.getContent();
        }

        return null;
    }

    @Override
    public void deleteTag(String tagName, String tagEntityType, String clusterName) throws Exception {
        String rootFolder = XtremIOConstants.getV2RootFolderForEntityType(tagEntityType);
        String xioTagName = rootFolder.concat(tagName);
        String uriString = XtremIOConstants.XTREMIO_V2_TAGS_STR
                .concat(XtremIOConstants.getInputNameForClusterString(xioTagName, clusterName));

        URI deleteURI = URI.create(uriString);
        log.info("Calling Tag Delete with: {}", deleteURI.toString());
        delete(deleteURI);
    }

    @Override
    public XtremIOTag getTagDetails(String tagName, String tagEntityType, String clusterName) throws Exception {
        try {
            String rootFolder = XtremIOConstants.getV2RootFolderForEntityType(tagEntityType);
            String xioTagName = rootFolder.concat(tagName);
            String uriString = XtremIOConstants.XTREMIO_V2_TAGS_STR
                    .concat(XtremIOConstants.getInputNameForClusterString(xioTagName, clusterName));
            log.info("Calling get tag details with: {}", uriString);
            ClientResponse response = get(URI.create(uriString));
            log.info(response.toString());
            XtremIOTags tags = getResponseObject(XtremIOTags.class, response);

            return tags.getContent();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
        log.info("Tag not available on Array with name : {}",
                tagName);

        return null;
    }

    @Override
    public XtremIOConsistencyGroup getSnapshotSetDetails(String snapshotSetName, String clusterName) throws Exception {
        String uriString = XtremIOConstants.XTREMIO_V2_SNAPSHOT_SET_STR
                .concat(XtremIOConstants.getInputNameForClusterString(snapshotSetName, clusterName));
        ClientResponse response = get(URI.create(uriString));
        log.info(response.toString());
        XtremIOCGResponse cgResponse = getResponseObject(XtremIOCGResponse.class, response);

        return cgResponse.getContent();
    }

}
