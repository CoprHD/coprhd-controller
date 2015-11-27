/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vplex.api.clientdata.VolumeInfo;
import com.sun.jersey.api.client.ClientResponse;

/**
 * VPlexApiDiscoveryManger provides methods for discovering and collecting
 * information from the VPlex.
 */
public class VPlexApiDiscoveryManager {

    // Logger reference.
    private static Logger s_logger = LoggerFactory.getLogger(VPlexApiDiscoveryManager.class);

    // A reference to the API client.
    private VPlexApiClient _vplexApiClient;

    /**
     * Package protected constructor.
     * 
     * @param client A reference to the API client.
     */
    VPlexApiDiscoveryManager(VPlexApiClient client) {
        _vplexApiClient = client;
    }

    /**
     * Returns the version of the VPlex management software.
     * 
     * @return The version of the VPlex management software.
     * 
     * @throws VPlexApiException When an error occurs getting the version information.
     */
    public String getManagementSoftwareVersion() throws VPlexApiException {
        String version = null;

        // Get the URI for the management server info request and make the request.
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_VERSION_INFO);
        s_logger.info("Mangement software version request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.post(requestURI, "");
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                s_logger.info("Get management server version is completing asynchronously");
                responseStr = _vplexApiClient.waitForCompletion(response);
                s_logger.info("Task Response is {}", responseStr);
            } else {
                throw VPlexApiException.exceptions
                        .failedGettingVPLEXMgmntSvrVersionStatus(String.valueOf(status));
            }
        }

        // Now parse this response to extract the custom data, which
        // contains the version.
        try {
            String customData = VPlexApiUtils.getCustomDataFromResponse(responseStr);
            s_logger.info("Custom data is {}", customData);
            String[] versionSubStrs = customData.split("Product Version");
            String versionSubString = versionSubStrs[1];
            versionSubString = versionSubString.substring(0, versionSubString.indexOf("-"));
            version = versionSubString.trim();
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedProcessingMgmntSvrVersionFromResponse(e);
        }

        return version;
    }

    /**
     * Determines the serial number of the VPlex management server.
     * 
     * @return The serial number of the VPlex management server.
     * 
     * @throws VPlexApiException When an error occurs querying the VPlex.
     */
    String getManagementServerSerialNumber() throws VPlexApiException {

        // Get the URI for the management server info request and make the request.
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_MANAGEMENT_SERVER);
        s_logger.info("Management Server Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(String.format(
                    "Failed getting management server info with status: %s",
                    status));
        }

        // Now parse this response to extract the attributes.
        try {
            Map<String, Object> attributes = VPlexApiUtils.getAttributesFromResponse(responseStr);
            return attributes.get(VPlexApiConstants.SERIAL_NO_ATT_KEY).toString();
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing management server information: %s", e.getMessage()), e);
        }
    }

    /**
     * Rediscovers the storage systems attached to the VPlex identified by the
     * passed identifiers for the purpose of discovering new volumes accessible
     * to the VPlex.
     * 
     * @param storageSystemNativeGuids The native guids of the storage systems
     *            to be rediscovered.
     */
    void rediscoverStorageSystems(List<String> storageSystemNativeGuids)
            throws VPlexApiException {

        List<VPlexStorageSystemInfo> storageSystemInfoList = getStorageSystemInfo();
        Iterator<String> storageSystemIter = storageSystemNativeGuids.iterator();
        while (storageSystemIter.hasNext()) {
            boolean foundSystem = false;
            String storageSystemNativeGuid = storageSystemIter.next();
            for (VPlexStorageSystemInfo storageSystemInfo : storageSystemInfoList) {
                if (!storageSystemInfo.matches(storageSystemNativeGuid)) {
                    continue;
                }
                // Found the storage system, rediscover it.
                foundSystem = true;
                ClientResponse response = null;
                try {
                    URI requestURI = _vplexApiClient.getBaseURI().resolve(
                            VPlexApiConstants.URI_REDISCOVER_ARRAY);
                    s_logger.info("Rediscover storage system URI is {}", requestURI.toString());
                    Map<String, String> argsMap = new HashMap<String, String>();
                    argsMap.put(VPlexApiConstants.ARG_DASH_A, storageSystemInfo.getPath());
                    argsMap.put(VPlexApiConstants.ARG_DASH_C, storageSystemInfo.getClusterId());
                    JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
                    s_logger.info("Rediscover system POST data is {}", postDataObject.toString());
                    response = _vplexApiClient.post(requestURI,
                            postDataObject.toString());
                    String responseStr = response.getEntity(String.class);
                    s_logger.info("Rediscover response is {}", responseStr);
                    if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                        if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                            _vplexApiClient.waitForCompletion(response);
                        } else {
                            throw new VPlexApiException(
                                    String.format("Request to rediscover storage systems failed with Status: %s",
                                            response.getStatus()));
                        }
                    }
                } catch (VPlexApiException vae) {
                    throw vae;
                } catch (Exception e) {
                    throw new VPlexApiException(String.format(
                            "Exception redsicovering storage systems: %s", e.getMessage()), e);
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }

            if (!foundSystem) {
                throw new VPlexApiException(String.format(
                        "Could not find storage system %s", storageSystemNativeGuid));
            }
        }
    }

    /**
     * Gets the information for the storage systems accessible by the VPlex.
     * 
     * @return A list of VPlexStorageSystemInfo specifying the info for the
     *         storage systems accessible to the VPlex.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    List<VPlexStorageSystemInfo> getStorageSystemInfo() throws VPlexApiException {

        List<VPlexStorageSystemInfo> storageSystemInfoList = new ArrayList<VPlexStorageSystemInfo>();
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            List<VPlexStorageSystemInfo> clusterStorageSystemInfoList = getStorageSystemInfoForCluster(clusterInfo
                    .getName());
            // We used to have a check here that only added an array once, even if it is in both clusters.
            // However in our lab environment, it seems to often occur that an array will be connected
            // to both clusters. If we don't include it both times, we would be unable to discover
            // storage-volumes on it in cluster-2 because cluster-2 would not issue the rediscover command.
            for (VPlexStorageSystemInfo storageSystemInfo : clusterStorageSystemInfoList) {
                storageSystemInfoList.add(storageSystemInfo);
            }
        }
        return storageSystemInfoList;
    }

    /**
     * Gets the information for the VPlex Ports.
     * 
     * @return A list of VPlexPortInfo specifying the info for the VPlex ports.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    List<VPlexPortInfo> getPortAndDirectorInfo() throws VPlexApiException {

        List<VPlexPortInfo> portInfoList = new ArrayList<VPlexPortInfo>();
        for (VPlexEngineInfo engineInfo : getEngineInfo()) {
            for (VPlexDirectorInfo directorInfo : engineInfo.getDirectorInfo()) {
                for (VPlexPortInfo portInfo : directorInfo.getPortInfo()) {
                    portInfoList.add(portInfo);
                }
            }
        }

        return portInfoList;
    }

    /**
     * Gets all the storage port info for a VPLEX device.
     * 
     * @return a list of VPlexPortInfo objects for the VPLEX.
     * 
     * @throws VPlexApiException
     */
    List<VPlexPortInfo> getPortInfo() throws VPlexApiException {

        s_logger.info("Getting all port information from VPLEX at "
                + _vplexApiClient.getBaseURI().toString());
        // Get the URI for the port info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_ENGINES.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        uriBuilder.append(VPlexApiConstants.URI_DIRECTORS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        uriBuilder.append(VPlexApiConstants.URI_DIRECTOR_PORTS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Director Ports Request URI is {}", requestURI.toString());

        ClientResponse response = _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status == VPlexApiConstants.SUCCESS_STATUS) {
            try {
                return VPlexApiUtils.getResourcesFromResponseContext(uriBuilder.toString(),
                        responseStr, VPlexPortInfo.class);
            } catch (Exception e) {
                throw VPlexApiException.exceptions.errorProcessingPortInformation(e.getLocalizedMessage());
            }
        } else if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            // return an empty list rather than an error
            s_logger.info("VPLEX returned a 404 Not Found for this context, returning an empty list instead.");
            return new ArrayList<VPlexPortInfo>();
        } else {
            throw VPlexApiException.exceptions.failedGettingPortInfo(String.valueOf(status));
        }
    }

    /**
     * Finds the info for the VPlex cluster by cluster name.
     * 
     * @param clusterName The VPlex cluster name.
     * @return VPlexClusterInfo specifying the info for the VPlex
     *         clusters.
     */
    public VPlexClusterInfo findClusterInfo(String clusterName) {
        VPlexClusterInfo vplexclusterInfo = null;
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            if (clusterInfo.getName().equals(clusterName)) {
                vplexclusterInfo = clusterInfo;
                break;
            }
        }
        return vplexclusterInfo;
    }

    /**
     * Gets the information for the VPlex Clusters
     * 
     * @param shallow true to get just the name and path for each cluster, false
     *            to get additional info about the systems and volumes.
     * 
     * @param isItlsRequired true to get the storage volume ITLs, false otherwise.
     * 
     * @return A list of VPlexClusterInfo specifying the info for the VPlex
     *         clusters.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */

    List<VPlexClusterInfo> getClusterInfo(boolean shallow, boolean isItlsRequired)
            throws VPlexApiException {

        // Get the URI for the cluster info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Clusters Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failedToGetClusterInfo(String.valueOf(status));
        }

        // Successful Response
        try {
            List<VPlexClusterInfo> clusterInfoList = VPlexApiUtils.getResourcesFromResponseContext(
                    VPlexApiConstants.URI_CLUSTERS.toString(), responseStr,
                    VPlexClusterInfo.class);

            if (!shallow) {
                for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                    String clusterName = clusterInfo.getName();
                    clusterInfo.setStorageSystemInfo(getStorageSystemInfoForCluster(clusterName));
                    clusterInfo.setSystemVolumeInfo(getSystemVolumeInfoForCluster(clusterName));
                    clusterInfo.setStorageVolumeInfo(getStorageVolumeInfoForCluster(clusterName, isItlsRequired));
                }
            }

            return clusterInfoList;
        } catch (Exception e) {
            s_logger.error(e.getLocalizedMessage(), e);
            throw VPlexApiException.exceptions.errorProcessingClusterInfo(e.getLocalizedMessage());
        }
    }

    /**
     * Gets the basic information for the VPlex Clusters. This will include
     * the cluster name, type, and context path only.
     * 
     * @return A list of VPlexClusterInfo specifying the info for the VPlex
     *         clusters.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    List<VPlexClusterInfo> getClusterInfoLite()
            throws VPlexApiException {

        // Get the URI for the cluster info request and make the request.
        URI requestURI = _vplexApiClient.getBaseURI().resolve(VPlexApiConstants.URI_CLUSTERS);
        s_logger.info("Clusters Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failedToGetClusterInfo(String.valueOf(status));
        }

        // Successful Response
        try {
            List<VPlexClusterInfo> clusterInfoList = VPlexApiUtils.getChildrenFromResponse(
                    VPlexApiConstants.URI_CLUSTERS.toString(), responseStr,
                    VPlexClusterInfo.class);

            return clusterInfoList;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.errorProcessingClusterInfo(e.getLocalizedMessage());
        }
    }

    /**
     * Finds the volumes in the VPlex configuration identified by the passed
     * native volume information.
     * 
     * @param volumeInfoList The native volume info for the volumes to find.
     * @param clusterInfoList The cluster information.
     * 
     * @return A map of the found VPlex volumes, key'd by the native volume
     *         information for each volume.
     * 
     * @throws VPlexApiException When an error occurs find the volumes.
     */

    Map<VolumeInfo, VPlexStorageVolumeInfo> findStorageVolumes(List<VolumeInfo> volumeInfoList,
            List<VPlexClusterInfo> clusterInfoList)
            throws VPlexApiException {

        Map<VolumeInfo, VPlexStorageVolumeInfo> storageVolumeInfoMap = new HashMap<VolumeInfo, VPlexStorageVolumeInfo>();
        Iterator<VolumeInfo> volumeInfoIter = volumeInfoList.iterator();
        while (volumeInfoIter.hasNext()) {
            boolean volumeFound = false;
            VolumeInfo volumeInfo = volumeInfoIter.next();
            String storageSystemNativeGuid = volumeInfo.getStorageSystemNativeGuid();
            String volumeWWN = volumeInfo.getVolumeWWN().toLowerCase();
            s_logger.info("Volume WWN is {}", volumeWWN);

            for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                if (clusterInfo.containsStorageSystem(storageSystemNativeGuid)) {
                    s_logger.info("Found storage system {} in cluster {}", storageSystemNativeGuid, clusterInfo.getName());
                    VPlexStorageVolumeInfo storageVolumeInfo = clusterInfo.getStorageVolume(volumeInfo);
                    if (storageVolumeInfo == null) {
                        s_logger.info("Storage volume with WWN {} was not found in cluster {}", volumeWWN, clusterInfo.getName());
                        String volumeName = volumeInfo.getVolumeName();
                        storageVolumeInfo = clusterInfo.getStorageVolume(volumeInfo);
                        if (storageVolumeInfo != null) {
                            // The storage volume requested for an operation is
                            // already claimed. For now, we just log a warning so
                            // that stale VPLEX artifacts associated with this
                            // storage volume can be easily identified and purged.
                            s_logger.warn("The claimed storage volume {} has WWN {}", volumeName, volumeWWN);
                        }
                        continue;
                    }
                    volumeFound = true;
                    s_logger.info("Found storage volume {}", storageVolumeInfo.toString());
                    storageVolumeInfo.setClusterId(clusterInfo.getName());
                    storageVolumeInfoMap.put(volumeInfo, storageVolumeInfo);
                    break;
                }
            }

            if (!volumeFound) {

                throw VPlexApiException.exceptions.couldNotFindStorageVolumeMatchingWWNOrITL(volumeWWN, storageSystemNativeGuid);
            }
        }

        return storageVolumeInfoMap;
    }

    /**
     * Attempts to find the storage volume with the passed name.
     * 
     * @param storageVolumeName The name of the storage volume.
     * 
     * @return A VPlexStorageVolumeInfo representing the storage volume with the passed name or
     *         null.
     * 
     * @throws VPlexApiException When an error occurs finding the storage volume.
     */
    VPlexStorageVolumeInfo findStorageVolume(String storageVolumeName)
            throws VPlexApiException {

        VPlexStorageVolumeInfo storageVolumeInfo = null;
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            String clusterName = clusterInfo.getName();
            s_logger.info("Find storage volume {} on cluster {}", storageVolumeName, clusterName);
            List<VPlexStorageVolumeInfo> storageVolumeInfoList = getStorageVolumeInfoForCluster(clusterName, false);
            for (VPlexStorageVolumeInfo clusterVolumeInfo : storageVolumeInfoList) {
                if (clusterVolumeInfo.getName().equals(storageVolumeName)) {
                    storageVolumeInfo = clusterVolumeInfo;
                    storageVolumeInfo.setClusterId(clusterName);
                    break;
                }
            }

            // We found the extent.
            if (storageVolumeInfo != null) {
                break;
            }
        }

        return storageVolumeInfo;
    }

    /**
     * Finds the extents created for the passed VPlex storage volumes.
     * 
     * @param storageVolumeInfoList The storage volumes whose extents to find.
     * 
     * @return A List of the VPlex extents
     * 
     * @throws VPlexApiException When an error occurs finding the extents.
     */
    List<VPlexExtentInfo> findExtents(List<VPlexStorageVolumeInfo> storageVolumeInfoList) throws VPlexApiException {

        List<VPlexExtentInfo> extentInfoList = new ArrayList<VPlexExtentInfo>();
        Iterator<VPlexStorageVolumeInfo> storageVolumeIter = storageVolumeInfoList.iterator();
        while (storageVolumeIter.hasNext()) {
            VPlexStorageVolumeInfo storageVolumeInfo = storageVolumeIter.next();
            boolean extentFound = false;
            int retryCount = 0;
            while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                try {
                    String storageVolumeName = storageVolumeInfo.getName();
                    s_logger.info("Find extent for volume {}", storageVolumeName);
                    List<VPlexExtentInfo> clusterExtentInfoList = getExtentInfoForCluster
                            (storageVolumeInfo.getClusterId());
                    for (VPlexExtentInfo extentInfo : clusterExtentInfoList) {
                        s_logger.info("Extent Info: {}", extentInfo.toString());
                        StringBuilder nameBuilder = new StringBuilder();
                        nameBuilder.append(VPlexApiConstants.EXTENT_PREFIX);
                        nameBuilder.append(storageVolumeName);
                        nameBuilder.append(VPlexApiConstants.EXTENT_SUFFIX);
                        if (extentInfo.getName().equals(nameBuilder.toString())) {
                            s_logger.info("Found extent for volume {}", storageVolumeName);
                            extentFound = true;
                            extentInfo.setStorageVolumeInfo(storageVolumeInfo);
                            extentInfo.setClusterId(storageVolumeInfo.getClusterId());
                            extentInfoList.add(extentInfo);
                            break;
                        }
                    }

                    if (!extentFound) {
                        s_logger.warn("Extent not found on try {} of {}", retryCount,
                                VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                        if (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                            VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                        } else {
                            throw VPlexApiException.exceptions
                                    .cantFindExtentForClaimedVolume(storageVolumeName);
                        }
                    } else {
                        break;
                    }
                } catch (VPlexApiException vae) {
                    s_logger.error(String.format("Exception finding extent on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    if (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                        VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                    } else {
                        throw vae;
                    }
                } catch (Exception e) {
                    s_logger.error(String.format("Exception finding extent on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    if (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                        VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                    } else {
                        throw e;
                    }
                }
            }
        }

        return extentInfoList;
    }

    /**
     * Attempts to find the extent with the passed name.
     * 
     * @param extentName The name of the extent.
     * 
     * @return A VPlexExtentInfo representing the extent with the passed name or
     *         null.
     * 
     * @throws VPlexApiException When an error occurs finding the extent.
     */
    VPlexExtentInfo findExtent(String extentName) throws VPlexApiException {

        VPlexExtentInfo extentInfo = null;
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            String clusterName = clusterInfo.getName();
            s_logger.info("Find extent {} on cluster {}", extentName, clusterName);
            List<VPlexExtentInfo> extentInfoList = getExtentInfoForCluster(clusterName);
            for (VPlexExtentInfo clusterExtentInfo : extentInfoList) {
                if (clusterExtentInfo.getName().equals(extentName)) {
                    extentInfo = clusterExtentInfo;
                    extentInfo.setClusterId(clusterName);
                    break;
                }
            }

            // We found the extent.
            if (extentInfo != null) {
                break;
            }
        }

        return extentInfo;
    }

    /**
     * Gets the extents on the cluster with the passed name.
     * 
     * @param clusterName The name of the cluster.
     * 
     * @return A list of VPlexExtentInfo instances for the extents found.
     * 
     * @throws VPlexApiException When an error occurs getting the extents on the
     *             cluster.
     */
    private List<VPlexExtentInfo> getExtentInfoForCluster(String clusterName)
            throws VPlexApiException {

        ClientResponse response = null;
        try {
            // Get the URI for the extent info request and make the request.
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
            uriBuilder.append(clusterName);
            uriBuilder.append(VPlexApiConstants.URI_EXTENTS.toString());
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    URI.create(uriBuilder.toString()));
            s_logger.info("Extents Request URI is {}", requestURI.toString());
            response = _vplexApiClient.get(requestURI);
            String responseStr = response.getEntity(String.class);
            s_logger.info("Response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                throw new VPlexApiException(String.format(
                        "Failed getting info for VPlex extents with status: %s",
                        response.getStatus()));
            }

            // Successful Response
            List<VPlexExtentInfo> clusterExtentInfoList = VPlexApiUtils
                    .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                            VPlexExtentInfo.class);
            return clusterExtentInfoList;
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing extent information: %s", e.getMessage()), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Find the local devices for the passed VPlex extents.
     * 
     * @param extentInfoList The extents for which to find the local devices.
     * 
     * @return A list of the local device info for the passed extents.
     * 
     * @throws VPlexApiException When an error occurs finding the devices.
     */
    List<VPlexDeviceInfo> findLocalDevices(List<VPlexExtentInfo> extentInfoList)
            throws VPlexApiException {

        List<VPlexDeviceInfo> deviceInfoList = new ArrayList<VPlexDeviceInfo>();
        Iterator<VPlexExtentInfo> extentIter = extentInfoList.iterator();
        while (extentIter.hasNext()) {
            VPlexExtentInfo extentInfo = extentIter.next();
            int retryCount = 0;
            boolean deviceFound = false;
            while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                try {
                    VPlexStorageVolumeInfo storageVolumeInfo = extentInfo.getStorageVolumeInfo();
                    String baseDeviceName = storageVolumeInfo.getName();
                    StringBuilder deviceNameBuilder = new StringBuilder();
                    deviceNameBuilder.append(VPlexApiConstants.DEVICE_PREFIX);
                    deviceNameBuilder.append(baseDeviceName);
                    s_logger.info("Find device with name {}", deviceNameBuilder.toString());

                    // Get the devices on the cluster for this extent.
                    List<VPlexDeviceInfo> clusterDeviceInfoList = getLocalDeviceInfoOnCluster
                            (storageVolumeInfo.getClusterId());
                    for (VPlexDeviceInfo deviceInfo : clusterDeviceInfoList) {
                        s_logger.info("Device Info: {}", deviceInfo.toString());
                        if (deviceInfo.getName().equals(deviceNameBuilder.toString())) {
                            s_logger.info("Found device for extent {}", extentInfo.getName());
                            deviceFound = true;
                            List<VPlexExtentInfo> deviceExtentInfoList = new ArrayList<VPlexExtentInfo>();
                            deviceExtentInfoList.add(extentInfo);
                            deviceInfo.setExtentInfo(deviceExtentInfoList);
                            deviceInfo.setCluster(storageVolumeInfo.getClusterId());
                            deviceInfoList.add(deviceInfo);
                            break;
                        }
                    }

                    if (!deviceFound) {
                        s_logger.warn("Local device not found on try {} of {}", retryCount,
                                VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                        if (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                            VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                        } else {
                            throw VPlexApiException.exceptions
                                    .cantFindLocalDeviceForExtent(extentInfo.getName());
                        }
                    } else {
                        break;
                    }
                } catch (VPlexApiException vae) {
                    s_logger.error(String.format("Exception finding local device on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    if (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                        VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                    } else {
                        throw vae;
                    }
                } catch (Exception e) {
                    s_logger.error(String.format("Exception finding local device on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    if (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
                        VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                    } else {
                        throw e;
                    }
                }
            }
        }

        return deviceInfoList;
    }

    /**
     * Attempts to find the local device with the passed name.
     * 
     * @param deviceName The name of the local device.
     * 
     * @return A VPlexDeviceInfo representing the local device with the passed
     *         name or null.
     * 
     * @throws VPlexApiException When an error occurs finding the local device.
     */
    VPlexDeviceInfo findLocalDevice(String deviceName) throws VPlexApiException {

        VPlexDeviceInfo deviceInfo = null;
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            String clusterName = clusterInfo.getName();
            s_logger.info("Find device {} on cluster {}", deviceName, clusterName);
            List<VPlexDeviceInfo> deviceInfoList = getLocalDeviceInfoOnCluster(clusterName);
            for (VPlexDeviceInfo clusterDeviceInfo : deviceInfoList) {
                if (clusterDeviceInfo.getName().equals(deviceName)) {
                    deviceInfo = clusterDeviceInfo;
                    deviceInfo.setCluster(clusterName);
                    break;
                }
            }

            // We found the device.
            if (deviceInfo != null) {
                break;
            }
        }

        return deviceInfo;
    }

    /**
     * Gets the devices on the cluster with the passed name.
     * 
     * @param clusterName The name of the cluster.
     * 
     * @return A list of VPlexDeviceInfo instances for the devices found.
     * 
     * @throws VPlexApiException When an error occurs getting the devices on the
     *             cluster.
     */
    private List<VPlexDeviceInfo> getLocalDeviceInfoOnCluster(String clusterName)
            throws VPlexApiException {

        ClientResponse response = null;
        try {
            // Get the URI for the device info request and make the request.
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
            uriBuilder.append(clusterName);
            uriBuilder.append(VPlexApiConstants.URI_DEVICES.toString());
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    URI.create(uriBuilder.toString()));
            s_logger.info("Devices Request URI is {}", requestURI.toString());
            response = _vplexApiClient.get(requestURI);
            String responseStr = response.getEntity(String.class);
            s_logger.info("Response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                throw new VPlexApiException(String.format(
                        "Failed getting info for VPlex devices with status: %s",
                        response.getStatus()));
            }

            // Successful Response
            List<VPlexDeviceInfo> clusterDeviceInfoList = VPlexApiUtils
                    .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                            VPlexDeviceInfo.class);
            return clusterDeviceInfoList;
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing device information: %s", e.getMessage()), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Finds the distributed device with the passed name.
     * 
     * @param deviceName The name of the distributed device to find.
     * 
     * @return A reference to the distributed device info or null if not found.
     * 
     * @throws VPlexApiException When an error occurs finding the device.
     */
    VPlexDistributedDeviceInfo findDistributedDevice(String deviceName)
            throws VPlexApiException {
        return findDistributedDevice(deviceName, false);
    }

    /**
     * Finds the distributed device with the passed name.
     * 
     * @param deviceName The name of the distributed device to find.
     * @param retry Indicates retry should occur if the first attempt to find
     *            the distributed device fails.
     * 
     * @return A reference to the distributed device info or null if not found.
     * 
     * @throws VPlexApiException When an error occurs finding the device.
     */
    VPlexDistributedDeviceInfo findDistributedDevice(String deviceName, boolean retry)
            throws VPlexApiException {

        s_logger.info("Find distributed device with name {}", deviceName);

        int retryCount = 0;
        VPlexDistributedDeviceInfo distributedDeviceInfo = null;
        while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
            try {
                List<VPlexDistributedDeviceInfo> deviceInfoList = getDistributedDeviceInfo();
                for (VPlexDistributedDeviceInfo deviceInfo : deviceInfoList) {
                    s_logger.info("Distributed Device Info: {}", deviceInfo.toString());
                    if (deviceInfo.getName().equals(deviceName)) {
                        s_logger.info("Found distributed device {}", deviceName);
                        distributedDeviceInfo = deviceInfo;
                        break;
                    }
                }

                if ((distributedDeviceInfo != null) || (!retry) ||
                        (retryCount >= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    break;
                } else {
                    s_logger.warn("Distributed device not found on try {} of {}", retryCount,
                            VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                }
            } catch (VPlexApiException vae) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding distributed device on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw vae;
                }
            } catch (Exception e) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding distributed device on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw e;
                }
            }
        }

        return distributedDeviceInfo;
    }

    /**
     * Find the virtual volume containing the passed name or name fragment.
     * In some cases, the passed name fragment could be a storage volume
     * used by the virtual volume.
     * 
     * @param volumeNameSubstr The name or name fragment of the virtual volume.
     * @param fetchAtts true to fetch the virtual volume attributes.
     * 
     * @return A reference to the virtual volume info.
     * 
     * @throws VPlexApiException When an error occurs finding the virtual
     *             volume.
     */
    VPlexVirtualVolumeInfo findVirtualVolume(String volumeNameSubstr, boolean fetchAtts)
            throws VPlexApiException {

        if (volumeNameSubstr == null) {
            throw VPlexApiException.exceptions.cantFindRequestedVolumeNull();
        }

        // Find the virtual volume.
        VPlexVirtualVolumeInfo virtualVolumeInfo = null;
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            virtualVolumeInfo = findVirtualVolume(clusterInfo.getName(),
                    volumeNameSubstr, fetchAtts);
            if (virtualVolumeInfo != null) {
                break;
            }
        }

        // Throw an exception if we can't find the volume.
        if (virtualVolumeInfo == null) {
            throw VPlexApiException.exceptions.cantFindRequestedVolume(volumeNameSubstr);
        }

        return virtualVolumeInfo;
    }

    /**
     * Find the virtual volume containing the passed name or name fragment.
     * In some cases, the passed name fragment could be a storage volume
     * used by the virtual volume.
     * 
     * @param clusterId The id of the cluster on which to find the virtual
     *            volume.
     * @param volumeNameSubstr The name or name fragment of the virtual volume.
     * @param fetchAtts true to fetch the virtual volume attributes.
     * 
     * @return A reference to the virtual volume info or null if not found.
     * 
     * @throws VPlexApiException When an error occurs finding the virtual
     *             volume.
     */
    VPlexVirtualVolumeInfo findVirtualVolume(String clusterId, String volumeNameSubstr,
            Boolean fetchAtts) throws VPlexApiException {
        return findVirtualVolume(clusterId, volumeNameSubstr, fetchAtts, false);
    }

    /**
     * Find the virtual volume containing the passed name or name fragment.
     * In some cases, the passed name fragment could be a storage volume
     * used by the virtual volume.
     * 
     * @param clusterId The id of the cluster on which to find the virtual
     *            volume.
     * @param volumeNameSubstr The name or name fragment of the virtual volume.
     * @param fetchAtts true to fetch the virtual volume attributes.
     * @param retry Indicates retry should occur if the first attempt to find
     *            the virtual volume fails.
     * 
     * @return A reference to the virtual volume info or null if not found.
     * 
     * @throws VPlexApiException When an error occurs finding the virtual
     *             volume.
     */
    VPlexVirtualVolumeInfo findVirtualVolume(String clusterId, String volumeNameSubstr,
            Boolean fetchAtts, boolean retry) throws VPlexApiException {

        if (volumeNameSubstr == null) {
            throw VPlexApiException.exceptions.cantFindRequestedVolumeNull();
        }

        s_logger.info("Find virtual volume containing {}", volumeNameSubstr);

        int retryCount = 0;
        while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
            try {
                List<VPlexVirtualVolumeInfo> clusterVolumeInfoList = getVirtualVolumesForCluster(clusterId);
                for (VPlexVirtualVolumeInfo volumeInfo : clusterVolumeInfoList) {
                    s_logger.info("Virtual volume Info: {}", volumeInfo.toString());
                    // We use contains as at times the passed name is only
                    // a portion of the virtual volume name for example, it
                    // may be the name of one of the storage volumes used by
                    // the virtual volume.
                    if (volumeInfo.getName().contains(volumeNameSubstr)) {
                        s_logger.info("Found virtual volume {}", volumeInfo.getName());
                        return volumeInfo;
                    }
                }

                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.warn("Virtual volume not found on try {} of {}", retryCount,
                            VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    break;
                }
            } catch (VPlexApiException vae) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding virtual volume on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw vae;
                }
            } catch (Exception e) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding virtual volume on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw e;
                }
            }
        }

        return null;
    }

    /**
     * Find the virtual volume(s) containing the passed name in the
     * virtualVolumeInfos list.
     * 
     * @param clusterInfoList A list of VPlexClusterInfo specifying the info for the VPlex
     *            clusters.
     * @param virtualVolumeInfos List of virtual volumes to find.
     * @param fetchAtts true to fetch the virtual volume attributes.
     * @param retry Indicates retry should occur if the first attempt to find
     *            the virtual volume fails.
     * 
     * @return A map of virtual volume name to the virtual volume info.
     * 
     * @throws VPlexApiException When an error occurs finding the virtual
     *             volume.
     */
    Map<String, VPlexVirtualVolumeInfo> findVirtualVolumes(List<VPlexClusterInfo> clusterInfoList,
            List<VPlexVirtualVolumeInfo> virtualVolumeInfos,
            boolean fetchAtts, boolean retry) throws VPlexApiException {

        if (virtualVolumeInfos == null) {
            throw VPlexApiException.exceptions.cantFindRequestedVolumeNull();
        }

        StringBuffer volumeNameStrBuf = new StringBuffer();

        // Make a map of virtual volume name to VPlexVirtualVolumeInfo
        Map<String, VPlexVirtualVolumeInfo> virtualVolumesToFind = new HashMap<String, VPlexVirtualVolumeInfo>();
        for (VPlexVirtualVolumeInfo virtualVolumeInfo : virtualVolumeInfos) {
            volumeNameStrBuf.append(virtualVolumeInfo.getName()).append(" ");
            virtualVolumesToFind.put(virtualVolumeInfo.getName(), virtualVolumeInfo);
        }
        s_logger.info("Find virtual volume(s) containing {}", volumeNameStrBuf.toString());

        // Make a map of virtual volume name to VPlexVirtualVolumeInfo for the virtual volume found on VPLEX.
        Map<String, VPlexVirtualVolumeInfo> foundVirtualVolumes = new HashMap<String, VPlexVirtualVolumeInfo>();

        int retryCount = 0;
        while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
            try {
                // Make a map of VPLEX cluster to virtual volumes found on that cluster.
                Map<String, List<VPlexVirtualVolumeInfo>> clusterToVirtualVolumeMap = new HashMap<String, List<VPlexVirtualVolumeInfo>>();
                for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                    List<VPlexVirtualVolumeInfo> clusterVolumeInfoList = getVirtualVolumesForCluster(clusterInfo.getName());
                    clusterToVirtualVolumeMap.put(clusterInfo.getName(), clusterVolumeInfoList);
                }

                List<VPlexVirtualVolumeInfo> virtualVolumeToFindList = new ArrayList<VPlexVirtualVolumeInfo>();
                for (Map.Entry<String, VPlexVirtualVolumeInfo> entry : virtualVolumesToFind.entrySet()) {
                    virtualVolumeToFindList.add(entry.getValue());
                }

                for (VPlexVirtualVolumeInfo virtualVolumeInfo : virtualVolumeToFindList) {
                    List<VPlexVirtualVolumeInfo> clusterVolumeInfoList =
                            clusterToVirtualVolumeMap.get(virtualVolumeInfo.getClusters().get(0));
                    for (VPlexVirtualVolumeInfo volumeInfo : clusterVolumeInfoList) {
                        s_logger.info("Virtual volume Info: {}", volumeInfo.toString());
                        if (volumeInfo.getName().equals(virtualVolumeInfo.getName())) {
                            s_logger.info("Found virtual volume {}", volumeInfo.getName());
                            foundVirtualVolumes.put(virtualVolumeInfo.getName(), volumeInfo);
                            // Remove the found virtual volume from the virtualVolumesToFind Map
                            virtualVolumesToFind.remove(virtualVolumeInfo.getName());
                        }
                    }
                }

                if (!foundVirtualVolumes.isEmpty() && foundVirtualVolumes.size() == virtualVolumeInfos.size()) {
                    return foundVirtualVolumes;
                }

                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.warn(String.format("Virtual volumes %s not found on try %d of %d",
                            geAllVolumeNamesFromMap(virtualVolumesToFind),
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES));
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    break;
                }
            } catch (VPlexApiException vae) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding virtual volumes on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    if (!foundVirtualVolumes.isEmpty()) {
                        return foundVirtualVolumes;
                    } else {
                        throw vae;
                    }
                }
            } catch (Exception e) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding virtual volumes on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    if (!foundVirtualVolumes.isEmpty()) {
                        return foundVirtualVolumes;
                    } else {
                        throw e;
                    }
                }
            }
        }

        return null;
    }

    /**
     * This method returns all volume names from the map.
     * 
     * @param virtualVolumesToFind Map of Volume name to volume info
     * @return returns all volume names from the Map
     */
    private String geAllVolumeNamesFromMap(Map<String, VPlexVirtualVolumeInfo> virtualVolumesToFind) {
        StringBuffer volumesBuffer = new StringBuffer();
        if (!virtualVolumesToFind.isEmpty()) {
            Set<String> volumeNames = virtualVolumesToFind.keySet();
            for (String volumeName : volumeNames) {
                volumesBuffer.append(volumeName).append(" ");
            }
        }
        return volumesBuffer.toString();
    }

    /**
     * Get the storage system info for the cluster with the passed name.
     * 
     * @param clusterName The name of the cluster.
     * 
     * @return A list of VPlexStorageSystemInfo specifying the storage system
     *         info for the cluster with the passed name.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    private List<VPlexStorageSystemInfo> getStorageSystemInfoForCluster(String clusterName)
            throws VPlexApiException {

        // Get the URI for the storage system info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_STORAGE_SYSTEMS.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Storage Systems Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(String.format(
                    "Failed getting storage system info for cluster %s with status: %s",
                    clusterName, status));
        }

        // Successful Response
        try {
            List<VPlexStorageSystemInfo> storageSystemInfoList = VPlexApiUtils.getChildrenFromResponse(
                    uriBuilder.toString(), responseStr, VPlexStorageSystemInfo.class);
            for (VPlexStorageSystemInfo storageSystemInfo : storageSystemInfoList) {
                storageSystemInfo.buildUniqueId();
                storageSystemInfo.setClusterId(clusterName);
            }

            return storageSystemInfoList;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing storage system information: %s", e.getMessage()), e);
        }
    }

    /**
     * Get the storage volume info for the cluster with the passed name.
     * 
     * @param clusterName The name of the cluster.
     * 
     * @return A list of VPlexStorageVolumeInfo specifying the storage volume
     *         info for the cluster with the passed name.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    private List<VPlexStorageVolumeInfo> getStorageVolumeInfoForCluster(String clusterName, boolean isITLFetch)
            throws VPlexApiException {

        // Get the URI for the storage volume info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);

        String responseJsonFormat = null;
        if (isITLFetch) {
            uriBuilder.append(VPlexApiConstants.URI_STORAGE_VOLUMES_DETAILS.toString());
            responseJsonFormat = VPlexApiConstants.ACCEPT_JSON_FORMAT_1;
        } else {
            uriBuilder.append(VPlexApiConstants.URI_STORAGE_VOLUMES.toString());
            responseJsonFormat = VPlexApiConstants.ACCEPT_JSON_FORMAT_0;
        }

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Storage Volumes Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI, responseJsonFormat);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.
                    failedGettingStorageVolumeInfo(clusterName, String.valueOf(status));
        }

        // Successful Response
        try {
            if (isITLFetch) {
                return VPlexApiUtils.getResourcesFromResponseContext(
                        uriBuilder.toString(), responseStr, VPlexStorageVolumeInfo.class);
            } else {
                return VPlexApiUtils.getChildrenFromResponse(
                        uriBuilder.toString(), responseStr, VPlexStorageVolumeInfo.class);
            }
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedProcessingStorageVolumeResponse(e.getMessage(), e);
        }
    }

    /**
     * Get the system volume info for the cluster with the passed name.
     * 
     * @param clusterName The cluster name.
     * 
     * @return A list of VPlexSystemVolumeInfo specifying the system volume
     *         information for the cluster.
     * 
     * @throws VPlexApiException When an error occurs getting the system volume
     *             information for the cluster.
     */
    private List<VPlexSystemVolumeInfo> getSystemVolumeInfoForCluster(String clusterName)
            throws VPlexApiException {

        // Get the URI for the logging volume info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_SYSTEM_VOLUMES.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Logging Volumes Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(String.format(
                    "Failed getting logging volume info for cluster %s with status: %s",
                    clusterName, status));
        }

        // Successful Response
        try {
            List<VPlexSystemVolumeInfo> systemVolumeInfoList = VPlexApiUtils.getChildrenFromResponse(
                    uriBuilder.toString(), responseStr, VPlexSystemVolumeInfo.class);
            for (VPlexSystemVolumeInfo systemVolumeInfo : systemVolumeInfoList) {
                updateSystemVolumeInfo(clusterName, systemVolumeInfo);
            }

            return systemVolumeInfoList;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing logging volume information: %s", e.getMessage()), e);
        }
    }

    /**
     * Updates the attribute info for the passed system volume.
     * 
     * @param clusterName The name cluster containing the passed system volume.
     * @param systemVolumeInfo The system volume to update.
     * 
     * @throws VPlexApiException When an error occurs updating the system volume
     *             attribute info.
     */
    private void updateSystemVolumeInfo(String clusterName,
            VPlexSystemVolumeInfo systemVolumeInfo) throws VPlexApiException {

        // Get the URI for the system volume info request and make the request.
        String systemVolumeName = systemVolumeInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_SYSTEM_VOLUMES.toString());
        uriBuilder.append(systemVolumeName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("System Volume Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(
                    String.format("Failed getting info for system volume %s in cluster %s with status: %s",
                            systemVolumeName, clusterName, status));
        }

        // Now parse this response to populate the system volume details in the passed
        // system volume info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, systemVolumeInfo);
            s_logger.info("Updated System Volume Info {}", systemVolumeInfo.toString());
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing system volume information: %s", e.getMessage()), e);
        }
    }

    /**
     * Gets the information for the VPlex engines.
     * 
     * @return A list of VPlexEngineInfo specifying the info for the VPlex
     *         engines.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    private List<VPlexEngineInfo> getEngineInfo() throws VPlexApiException {

        // Get the URI for the engine info request and make the request.
        URI requestURI = _vplexApiClient.getBaseURI().resolve(VPlexApiConstants.URI_ENGINES);
        s_logger.info("Engines Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(String.format(
                    "Failed getting info for VPlex engines with status: %s",
                    status));
        }

        // Successful Response
        try {
            List<VPlexEngineInfo> engineInfoList = VPlexApiUtils.getChildrenFromResponse(
                    VPlexApiConstants.URI_ENGINES.toString(), responseStr,
                    VPlexEngineInfo.class);
            for (VPlexEngineInfo engineInfo : engineInfoList) {
                s_logger.info("Engine Info: {}", engineInfo.toString());
                engineInfo
                        .setDirectorInfo(getDirectorInfoForEngine(engineInfo.getName()));
            }

            return engineInfoList;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing engine information: %s", e.getMessage()), e);
        }
    }

    /**
     * Gets the information for the VPlex directors for the passed engine.
     * 
     * @param engineName The name of the VPlex engine.
     * 
     * @return A list of VPlexDirectorInfo specifying the info for the VPlex
     *         directors.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    private List<VPlexDirectorInfo> getDirectorInfoForEngine(String engineName)
            throws VPlexApiException {

        // Get the URI for the director info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_ENGINES.toString());
        uriBuilder.append(engineName);
        uriBuilder.append(VPlexApiConstants.URI_DIRECTORS.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Directors Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(String.format(
                    "Failed getting director info for VPlex engine %s with status: %s",
                    engineName, status));
        }

        // Successful Response
        try {
            List<VPlexDirectorInfo> directorInfoList = VPlexApiUtils.getChildrenFromResponse(
                    uriBuilder.toString(), responseStr, VPlexDirectorInfo.class);
            for (VPlexDirectorInfo directorInfo : directorInfoList) {
                updateDirectorInfo(engineName, directorInfo);
                s_logger.info("Director Info: {}", directorInfo.toString());
                directorInfo.setPortInfo(getPortInfoForDirector(engineName,
                        directorInfo));
            }

            return directorInfoList;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing director information: %s", e.getMessage()), e);
        }
    }

    /**
     * Updates the attribute info for the passed VPlex director.
     * 
     * @param engineName The name engine containing the passed VPlex director.
     * @param directorInfo The VPlex director to update.
     * 
     * @throws VPlexApiException When an error occurs updating the director
     *             attribute info.
     */
    private void updateDirectorInfo(String engineName, VPlexDirectorInfo directorInfo)
            throws VPlexApiException {

        // Get the URI for the director info request and make the request.
        String directorName = directorInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_ENGINES.toString());
        uriBuilder.append(engineName);
        uriBuilder.append(VPlexApiConstants.URI_DIRECTORS.toString());
        uriBuilder.append(directorName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Director Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(
                    String.format("Failed getting info for director %s in engine %s with status: %s",
                            directorName, engineName, status));
        }

        // Now parse this response to populate the director details in the passed
        // director info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, directorInfo);
            s_logger.info("Updated Director Info {}", directorInfo.toString());

        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing director information: %s", e.getMessage()), e);
        }
    }

    /**
     * Gets the information for the VPlex ports for the passed director.
     * 
     * @param directorInfo The VPlex director info.
     * 
     * @return A list of VPlexPortInfo specifying the info for the VPlex ports.
     * 
     * @throws VPlexApiException If a VPlex request returns a failed status or
     *             an error occurs processing the response.
     */
    private List<VPlexPortInfo> getPortInfoForDirector(String engineName,
            VPlexDirectorInfo directorInfo) throws VPlexApiException {

        // Get the URI for the port info request and make the request.
        String directorName = directorInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_ENGINES.toString());
        uriBuilder.append(engineName);
        uriBuilder.append(VPlexApiConstants.URI_DIRECTORS.toString());
        uriBuilder.append(directorName);
        uriBuilder.append(VPlexApiConstants.URI_DIRECTOR_PORTS.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Director Ports Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(
                    String.format("Failed getting port info for VPlex director %s in engine %s with status: %s",
                            directorName, engineName, status));
        }

        // Successful Response
        try {
            List<VPlexPortInfo> portInfoList = VPlexApiUtils.getChildrenFromResponse(uriBuilder.toString(),
                    responseStr, VPlexPortInfo.class);
            for (VPlexPortInfo portInfo : portInfoList) {
                s_logger.info("Port Info: {}", portInfo.toString());
                portInfo.setDirectorInfo(directorInfo);
                updatePortInfo(engineName, directorName, portInfo);
            }

            return portInfoList;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing director port information: %s", e.getMessage()), e);
        }
    }

    /**
     * Updates the attribute info for the passed VPlex port.
     * 
     * @param engineName The name engine containing the passed VPlex director.
     * @param directorName The name of the director containing the passed port.
     * @param portInfo The VPlex port to update.
     * 
     * @throws VPlexApiException When an error occurs updating the port
     *             attribute info.
     */
    private void updatePortInfo(String engineName, String directorName,
            VPlexPortInfo portInfo) throws VPlexApiException {

        // Get the URI for the port info request and make the request.
        String portName = portInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_ENGINES.toString());
        uriBuilder.append(engineName);
        uriBuilder.append(VPlexApiConstants.URI_DIRECTORS.toString());
        uriBuilder.append(directorName);
        uriBuilder.append(VPlexApiConstants.URI_DIRECTOR_PORTS.toString());
        uriBuilder.append(portName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Port Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(
                    String.format("Failed getting info for port %s on VPlex director %s in engine %s with status: %s",
                            portName, directorName, engineName, status));
        }

        // Now parse this response to populate the port details in the passed
        // port info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, portInfo);
            s_logger.info("Updated Port Info {}", portInfo.toString());

        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing port information: %s", e.getMessage()), e);
        }
    }

    /**
     * Gets all initiators on the cluster with the passed name.
     * 
     * @param clusterName The name of the cluster.
     * 
     * @return A list of VPlexInitiatorInfo instances specifying the initiator
     *         information.
     * 
     * @throws VPlexApiException When an error occurs getting the initiators on
     *             the cluster.
     */
    List<VPlexInitiatorInfo> getInitiatorInfoForCluster(String clusterName)
            throws VPlexApiException {

        // Get the URI for the initiator info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_INITIATORS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Initiators Request URI is {}", requestURI.toString());
        ClientResponse response =
                _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status == VPlexApiConstants.SUCCESS_STATUS) {
            try {
                return VPlexApiUtils.getResourcesFromResponseContext(
                        uriBuilder.toString(), responseStr, VPlexInitiatorInfo.class);
            } catch (Exception e) {
                throw VPlexApiException.exceptions.errorProcessingInitiatorInformation(e.getLocalizedMessage());
            }
        } else if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            // return an empty list rather than an error
            s_logger.info("VPLEX returned a 404 Not Found for this context, returning an empty list instead.");
            return new ArrayList<VPlexInitiatorInfo>();
        } else {
            throw VPlexApiException.exceptions
                .failedGettingInitiatorInfoForCluster(clusterName, String.valueOf(status));
        }
    }

    /**
     * Updates the attribute data for the passed initiator on the passed
     * cluster.
     * 
     * @param clusterName The name of the cluster.
     * @param initiatorInfo The initiator whose attributes are to be set.
     * 
     * @throws VPlexApiException When an error occurs updating the initiator
     *             attributes.
     */
    private void updateInitiatorInfo(String clusterName, VPlexInitiatorInfo initiatorInfo)
            throws VPlexApiException {

        // Get the URI for the initiator info request and make the request.
        String initiatorName = initiatorInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_INITIATORS.toString());
        uriBuilder.append(initiatorName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Initiator Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(
                    String.format("Failed getting info for initiator %s in cluster %s with status: %s",
                            initiatorName, clusterName, status));
        }

        // Now parse this response to populate the initiator details in the passed
        // initiator info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, initiatorInfo);
            s_logger.info("Updated Initiator Info {}", initiatorInfo.toString());

        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing initiator information: %s", e.getMessage()), e);
        }
    }

    /**
     * Executes an initiator discovery on the passed cluster.
     * 
     * @param clusterInfo The cluster on which initiators are to be discovered.
     * 
     * @throws VPlexApiException When an error occurs executing the discovery.
     */
    void discoverInitiatorsOnCluster(VPlexClusterInfo clusterInfo)
            throws VPlexApiException {

        ClientResponse response = null;
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_INITIATOR_DISCOVERY);
            s_logger.info("Initiator discovery URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_C, clusterInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Initiator discovery POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Initiator discovery response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    throw new VPlexApiException(String.format(
                            "Request initiator discovery failed with Status: %s",
                            response.getStatus()));
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Exception during initiator discovery: %s", e.getMessage()), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Gets information for the target FE ports on the cluster with the passed
     * name.
     * 
     * @param clusterName The name of the cluster.
     * 
     * @return A list of VPlexTargetInfo instances specifying the target
     *         information.
     * 
     * @throws VPlexApiException When an error occurs getting the target
     *             information for the cluster.
     */
    List<VPlexTargetInfo> getTargetInfoForCluster(String clusterName)
            throws VPlexApiException {

        // Get the URI for the initiator info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_TARGETS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Targets Request URI is {}", requestURI.toString());
        ClientResponse response =
                _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();

        if (status == VPlexApiConstants.SUCCESS_STATUS) {
            try {
                return VPlexApiUtils.getResourcesFromResponseContext(
                        uriBuilder.toString(), responseStr, VPlexTargetInfo.class);
            } catch (Exception e) {
                throw VPlexApiException.exceptions.errorProcessingTargetPortInformation(String.valueOf(status));
            }
        } else if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            // return an empty list rather than an error
            s_logger.info("VPLEX returned a 404 Not Found for this context, returning an empty list instead.");
            return new ArrayList<VPlexTargetInfo>();
        } else {
            throw VPlexApiException.exceptions.failedGettingTargetPortInfo(String.valueOf(status));
        }
    }

    /**
     * Finds the storage view with the passed name.
     * 
     * @param viewName The name of the storage view to be found.
     * 
     * @return A VPlexStorageViewInfo instance specifying the storage view
     *         information or null when not found.
     * 
     * @throws VPlexApiException When an error occurs finding the storage view.
     */
    VPlexStorageViewInfo findStorageView(String viewName) throws VPlexApiException {
        return findStorageView(viewName, false);
    }

    /**
     * Finds the storage view with the passed name.
     * 
     * @param viewName The name of the storage view to be found.
     * @param includeDetails true if the request should include storage details
     * 
     * @return A VPlexStorageViewInfo instance specifying the storage view
     *         information or null when not found.
     * 
     * @throws VPlexApiException When an error occurs finding the storage view.
     */
    VPlexStorageViewInfo findStorageView(String viewName, boolean includeDetails) throws VPlexApiException {

        VPlexStorageViewInfo storageViewInfo = null;
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            storageViewInfo = findStorageViewOnCluster(viewName, clusterInfo.getName(), includeDetails);
            if (storageViewInfo != null) {
                break;
            }
        }
        return storageViewInfo;
    }

    /**
     * Finds the storage view with the passed name on the cluster with the
     * passed cluster name.
     * 
     * @param viewName The name of the storage view to be found.
     * @param clusterName The names of the cluster on which to find the storage
     *            view.
     * 
     * @return A VPlexStorageViewInfo instance specifying the storage view
     *         information or null when not found.
     * 
     * @throws VPlexApiException When an error occurs finding the storage view.
     */
    VPlexStorageViewInfo findStorageViewOnCluster(String viewName, String clusterName, Boolean includeDetails)
            throws VPlexApiException {
        return findStorageViewOnCluster(viewName, clusterName, includeDetails, false);
    }

    /**
     * Finds the storage view with the passed name on the cluster with the
     * passed cluster name.
     * 
     * @param viewName The name of the storage view to be found.
     * @param clusterName The names of the cluster on which to find the storage
     *            view.
     * @param includeDetails true to fetch the storage view attributes.
     * @param retry Indicates retry should occur if the first attempt to find
     *            the storage view fails.
     * 
     * @return A VPlexStorageViewInfo instance specifying the storage view
     *         information or null when not found.
     * 
     * @throws VPlexApiException When an error occurs finding the storage view.
     */
    VPlexStorageViewInfo findStorageViewOnCluster(String viewName, String clusterName,
            Boolean includeDetails, boolean retry) throws VPlexApiException {

        // Get the URI for the storage view info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_STORAGE_VIEWS.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Storage views request URI is {}", requestURI.toString());

        int retryCount = 0;
        VPlexStorageViewInfo storageViewInfo = null;
        while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
            try {
                ClientResponse response = _vplexApiClient.get(requestURI);
                String responseStr = response.getEntity(String.class);
                s_logger.info("Response is {}", responseStr);
                int status = response.getStatus();
                response.close();
                if (status != VPlexApiConstants.SUCCESS_STATUS) {
                    throw VPlexApiException.exceptions.getStorageViewsFailed(String.format(
                            "Failed getting storage view info for cluster %s with status: %s",
                            clusterName, status));
                }

                // Successful Response
                List<VPlexStorageViewInfo> storageViewInfoList = VPlexApiUtils
                        .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                                VPlexStorageViewInfo.class);
                storageViewInfo = null;
                for (VPlexStorageViewInfo clusterStorageViewInfo : storageViewInfoList) {
                    s_logger.info("Storage View Info: {}", clusterStorageViewInfo.toString());
                    if (clusterStorageViewInfo.getName().equals(viewName)) {
                        storageViewInfo = clusterStorageViewInfo;
                        storageViewInfo.setClusterId(clusterName);

                        // if true, the StorageViewInfo objects will include extended details
                        // that are not needed in most cases, e.g. initiators, ports, and virtual volumes
                        if (includeDetails) {
                            updateStorageViewInfo(storageViewInfo);
                        }
                        break;
                    }
                }

                if ((storageViewInfo != null) || (!retry)
                        || (retryCount >= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    break;
                } else {
                    s_logger.warn("Storage view not found on try {} of {}", retryCount,
                            VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                }
            } catch (VPlexApiException vae) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error("Exception finding storage view on try {} of {}", retryCount,
                            VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw vae;
                }
            } catch (Exception e) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error("Exception finding storage view on try {} of {}", retryCount,
                            VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw VPlexApiException.exceptions
                            .getStorageViewsFailed(String.format(
                                    "Exception getting storage view: %s", e.getMessage()));
                }
            }
        }

        return storageViewInfo;
    }

    /**
     * Updates a VPlexStorageViewInfo object with detailed attributes
     * that are not needed in most situations, e.g. initiators, ports,
     * and virtual volumes.
     * 
     * @param storageViewInfo
     */
    void updateStorageViewInfo(VPlexStorageViewInfo storageViewInfo) {
        // Get the URI for the storage view info request and make the request.
        String storageViewName = storageViewInfo.getName();
        String clusterName = storageViewInfo.getClusterId();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(storageViewInfo.getClusterId());
        uriBuilder.append(VPlexApiConstants.URI_STORAGE_VIEWS.toString());
        uriBuilder.append(storageViewName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Storage View Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.getStorageViewsFailed(String.format("Failed getting info for storage "
                    + "view %s in cluster %s with status: %s", storageViewName, clusterName,
                    status));
        }

        // Now parse this response to populate the storage view details in the
        // passed storage view info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, storageViewInfo);
            updateStorageViewInitiatorPWWN(storageViewInfo);
        } catch (Exception e) {
            throw VPlexApiException.exceptions.getStorageViewsFailed(String.format("Failed getting info for storage "
                    + "view %s in cluster %s with status: %s", storageViewName, clusterName,
                    status));
        }
    }

    /**
     * Updates a VPlexStorageViewInfo object with the initiators PWWN
     * based on the initiators.
     * 
     * @param storageViewInfo The reference to VPlexStorageViewInfo
     */
    void updateStorageViewInitiatorPWWN(VPlexStorageViewInfo storageViewInfo) {
        List<String> initiators = storageViewInfo.getInitiators();
        List<VPlexInitiatorInfo> initiatorsInfoList = new ArrayList<VPlexInitiatorInfo>();
        for (String initiator : initiators) {
            VPlexInitiatorInfo initiatorInfo = new VPlexInitiatorInfo();
            initiatorInfo.setName(initiator);
            updateInitiatorInfo(storageViewInfo.getClusterId(), initiatorInfo);
            initiatorsInfoList.add(initiatorInfo);
        }

        List<String> initiatorPWWNs = new ArrayList<String>();
        for (VPlexInitiatorInfo initiatorInfo : initiatorsInfoList) {
            String pwwn = initiatorInfo.getPortWwn();
            if (pwwn.startsWith("0x")) {
                pwwn = pwwn.substring(2);
            }
            pwwn = pwwn.toUpperCase();
            initiatorPWWNs.add(pwwn);
        }
        storageViewInfo.setInitiatorPwwns(initiatorPWWNs);
        s_logger.info("Updated Storage View Info {}", storageViewInfo.toString());
    }

    /**
     * Gets the detailed Storage View info for a given VPLEX cluster.
     * 
     * @param clusterName the cluster name to filter results by
     * @return a list of Storage View infos for a given VPLEX cluster
     * @throws VPlexApiException
     */
    List<VPlexStorageViewInfo> getStorageViews(String clusterName) throws VPlexApiException {

        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_STORAGE_VIEWS.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Storage views request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.getStorageViewsFailed(String.format(
                    "Failed getting storage views: %s", status));
        }

        try {
            List<VPlexStorageViewInfo> storageViews = VPlexApiUtils
                    .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                            VPlexStorageViewInfo.class);

            List<VPlexStorageViewInfo> detailedStorageViews = new ArrayList<VPlexStorageViewInfo>();
            for (VPlexStorageViewInfo sv : storageViews) {
                VPlexStorageViewInfo svDetailed = findStorageViewOnCluster(sv.getName(), clusterName, true);
                if (svDetailed != null) {
                    detailedStorageViews.add(svDetailed);
                }
            }

            return detailedStorageViews;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.getStorageViewsFailed(String.format(
                    "Error processing storage views: %s", e.getMessage()));
        }
    }

    /**
     * Returns a Set of storage view names for a given initiator name.
     * 
     * @param clusterName the VPLEX cluster to look in
     * @param initiatorName the initiator name
     * @return a Set of storage view names
     */
    private Set<String> findStorageViewNamesForInitiator(String clusterName,
            String initiatorName) {
        Set<String> viewNames = new HashSet<String>();
        ClientResponse response = null;
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_FIND_STORAGE_VIEW);
        s_logger.info("Find storage view URI is {}", requestURI.toString());
        Map<String, String> argsMap = new HashMap<String, String>();
        argsMap.put(VPlexApiConstants.ARG_DASH_C, clusterName);
        argsMap.put(VPlexApiConstants.ARG_DASH_I, initiatorName);
        JSONObject postDataObject = VPlexApiUtils
                .createPostData(argsMap, false);
        s_logger.info("Create storage view POST data is {}",
                postDataObject.toString());
        response = _vplexApiClient.post(requestURI, postDataObject.toString());
        String responseStr = response.getEntity(String.class);
        s_logger.info("Find storage view response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                s_logger.info("Get storage views for initaitor is completing asynchronously");
                responseStr = _vplexApiClient.waitForCompletion(response);
                s_logger.info("Task Response is {}", responseStr);
            } else {
                throw VPlexApiException.exceptions.getStorageViewsFailed(String
                        .format("Failed getting storage views: %s", status));
            }
        }
        try {
            String customData = VPlexApiUtils
                    .getCustomDataFromResponse(responseStr);
            s_logger.info("Custom data from find storage view is {}",
                    customData);

            // custom-data can look something like this:
            // "Views including inititator *1000*:\nView V1_cluster123_host1hostcom_001.\n"
            // so we're splitting on line breaks and taking the content of lines starting
            // with "View " (also removing any trailing periods).
            String[] lines = customData.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].replaceAll("^View ", "").replaceAll(
                        "\\.$", "");
                viewNames.add(line);
            }
        } catch (Exception e) {
            throw VPlexApiException.exceptions.getStorageViewsFailed(String
                    .format("Error processing storage views: %s",
                            e.getMessage()));
        }
        return viewNames;
    }

    /**
     * Returns a list of VPlexStorageViewInfo objects representing
     * storage views that contain the given initiator names.
     * 
     * @param clusterName the VPLEX cluster to look in
     * @param initiatorNames the initiator names to look for
     * @return a list of VPlexStorageViewInfo objects
     */
    public List<VPlexStorageViewInfo> getStorageViewsContainingInitiators(
            String clusterName, List<String> initiatorNames) {
        Set<String> viewNames = new HashSet<String>();
        for (String initiatorName : initiatorNames) {
            viewNames.addAll(findStorageViewNamesForInitiator(clusterName,
                    initiatorName));
        }
        List<VPlexStorageViewInfo> detailedStorageViews = new ArrayList<VPlexStorageViewInfo>();
        for (String viewName : viewNames) {
            VPlexStorageViewInfo svDetailed = findStorageViewOnCluster(
                    viewName, clusterName, true);
            if (svDetailed != null) {
                detailedStorageViews.add(svDetailed);
            } else {
                s_logger.warn("could not find details for storage view {} on cluster {}",
                        viewName, clusterName);
            }
        }
        return detailedStorageViews;
    }

    /**
     * Gets all the detailed Storage View infos for the entire VPLEX device.
     * 
     * @return list of all Storage View infos for a given VPLEX instance
     * @throws VPlexApiException
     */
    List<VPlexStorageViewInfo> getStorageViews() throws VPlexApiException {
        return getStorageViewsForCluster(VPlexApiConstants.WILDCARD.toString());
    }

    /**
     * Gets all the detailed Storage View infos for the give VPLEX cluster.
     * 
     * @param clusterName name of the VPLEX cluster to look at, or you can send
     *            a wildcard (*) to get info from both clusters.
     * @return list of all Storage View infos for a given VPLEX instance
     * @throws VPlexApiException
     */
    List<VPlexStorageViewInfo> getStorageViewsForCluster(String clusterName) throws VPlexApiException {

        s_logger.info("Getting all storage view information from VPLEX at " + _vplexApiClient.getBaseURI().toString());
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_STORAGE_VIEWS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Storage views request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status == VPlexApiConstants.SUCCESS_STATUS) {
            try {
                List<VPlexStorageViewInfo> storageViews = VPlexApiUtils
                        .getResourcesFromResponseContext(uriBuilder.toString(), responseStr,
                                VPlexStorageViewInfo.class);

                // update storage views with wwpn info
                Map<String, String> initInfoMap = getInitiatorNameToWwnMap(clusterName);
                for (VPlexStorageViewInfo sv : storageViews) {
                    for (String initName : sv.getInitiators()) {
                        String initWwn = initInfoMap.get(initName);
                        sv.getInitiatorPwwns().add(initWwn);
                    }
                    sv.refreshMaps();
                }

                return storageViews;
            } catch (Exception e) {
                throw VPlexApiException.exceptions.errorProcessingStorageViewInformation(e.getLocalizedMessage());
            }
        } else if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            // return an empty list rather than an error
            s_logger.info("VPLEX returned a 404 Not Found for this context, returning an empty list instead.");
            return new ArrayList<VPlexStorageViewInfo>();
        } else {
            throw VPlexApiException.exceptions.failedGettingStorageViewInfo(String.valueOf(status));
        }
    }

    /**
     * Finds the migrations with the passed names.
     * 
     * @param migrationNames The names of the migrations to find.
     * 
     * @return A list of references to the VPlex migration infos.
     * 
     * @throws VPlexApiException If an error occurs trying to find a migration
     *             or a migration is simply not found.
     */
    List<VPlexMigrationInfo> findMigrations(List<String> migrationNames)
            throws VPlexApiException {

        List<VPlexMigrationInfo> migrationInfoList = new ArrayList<VPlexMigrationInfo>();
        for (String migrationName : migrationNames) {
            try {
                // First look in the device migrations and if not found, then
                // look in the extent migrations.
                VPlexMigrationInfo migrationInfo = findMigration(migrationName,
                        VPlexApiConstants.URI_DEVICE_MIGRATIONS);
                migrationInfo.setIsDeviceMigration(true);
                migrationInfoList.add(migrationInfo);
            } catch (VPlexApiException vae) {
                s_logger.info("Migration {} not found with device migrations");

                // Try looking in the extent migrations.
                // look in the extent migrations.
                VPlexMigrationInfo migrationInfo = findMigration(migrationName,
                        VPlexApiConstants.URI_EXTENT_MIGRATIONS);
                migrationInfo.setIsDeviceMigration(false);
                migrationInfoList.add(migrationInfo);
            }
        }
        return migrationInfoList;
    }

    /**
     * Find the migration with the passed name.
     * 
     * @param migrationName The name of the migration to find.
     * @param baseMigrationPath The base path for this migration.
     * 
     * @return A VPlex migration info.
     * 
     * @throws VPlexApiException If an error occurs trying to find a migration
     *             or a migration is simply not found.
     */
    VPlexMigrationInfo findMigration(String migrationName, URI baseMigrationPath)
            throws VPlexApiException {
        return findMigration(migrationName, baseMigrationPath, false);
    }

    /**
     * Find the migration with the passed name.
     * 
     * @param migrationName The name of the migration to find.
     * @param baseMigrationPath The base path for this migration.
     * @param retry Indicates retry should occur if the first attempt to find
     *            the migration fails.
     * 
     * @return A VPlex migration info.
     * 
     * @throws VPlexApiException If an error occurs trying to find a migration
     *             or a migration is simply not found.
     */
    VPlexMigrationInfo findMigration(String migrationName, URI baseMigrationPath,
            boolean retry) throws VPlexApiException {

        int retryCount = 0;
        VPlexMigrationInfo migrationInfo = null;
        while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
            try {
                // Get the URI for the migration info request and make the request.
                URI requestURI = _vplexApiClient.getBaseURI().resolve(baseMigrationPath);
                s_logger.info("Find migration request URI is {}", requestURI.toString());
                ClientResponse response = _vplexApiClient.get(requestURI);
                String responseStr = response.getEntity(String.class);
                s_logger.info("Response is {}", responseStr);
                int status = response.getStatus();
                response.close();
                if (status != VPlexApiConstants.SUCCESS_STATUS) {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.getMigrationsFailureStatus(
                            String.valueOf(status), cause);
                }

                // Successful Response
                List<VPlexMigrationInfo> allMigrationInfos = VPlexApiUtils
                        .getChildrenFromResponse(baseMigrationPath.toString(), responseStr,
                                VPlexMigrationInfo.class);
                migrationInfo = null;
                for (VPlexMigrationInfo mInfo : allMigrationInfos) {
                    s_logger.info("Migration Info: {}", mInfo.toString());
                    if (mInfo.getName().equals(migrationName)) {
                        migrationInfo = mInfo;
                        break;
                    }
                }

                if ((migrationInfo != null) || (!retry) || (retryCount >= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    // Throw an exception if not found.
                    if (migrationInfo == null) {
                        // Not found.
                        throw VPlexApiException.exceptions.cantFindMigrationWithName(migrationName);
                    } else {
                        // Now we update the migration info so it contains the status
                        // of the migration.
                        updateMigrationInfo(migrationInfo, baseMigrationPath);
                        break;
                    }
                } else {
                    s_logger.warn("Migration not found on try {} of {}",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                }
            } catch (VPlexApiException vae) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding migration on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw vae;
                }
            } catch (Exception e) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding migration on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw VPlexApiException.exceptions.failureFindingMigrationWithName(migrationName, e);
                }
            }
        }

        return migrationInfo;
    }

    /**
     * Updates the attribute info for the passed VPlex migration.
     * 
     * @param portInfo The VPlex migration to update.
     * @param baseMigrationPath The base path for these migrations.
     * 
     * @throws VPlexApiException When an error occurs updating the migration
     *             attribute info.
     */
    private void updateMigrationInfo(VPlexMigrationInfo migrationInfo,
            URI baseMigrationPath) throws VPlexApiException {

        // Get the URI for the migration info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(baseMigrationPath.toString());
        uriBuilder.append(migrationInfo.getName());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(uriBuilder.toString());
        s_logger.info("Migration Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(
                    String.format("Failed getting info for migration %s with status: %s",
                            migrationInfo.getName(), status));
        }

        // Now parse this response to populate the migration details in the passed
        // migration info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, migrationInfo);
            s_logger.info("Updated Migration Info {}", migrationInfo.toString());
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error updating migration information: %s", e.getMessage()), e);
        }
    }

    /**
     * Updates virtual volume details.
     * 
     * @param clusterName the VPLEX cluster name
     * @param virtualVolumeInfo the virtual volume to update
     * 
     * @return boolean indicating whether or not the volume
     *         information was found on the device
     * 
     * @throws VPlexApiException
     */
    boolean updateVirtualVolumeInfo(String clusterName,
            VPlexVirtualVolumeInfo virtualVolumeInfo) throws VPlexApiException {

        // Get the URI for the virtual volume info request and make the request.
        String virtualVolumeName = virtualVolumeInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_VIRTUAL_VOLUMES.toString());
        uriBuilder.append(virtualVolumeName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Virtual Volume Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            s_logger.info("requested volume {} not found on vplex cluster {}",
                    virtualVolumeName, clusterName);
            return false;
        }
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw new VPlexApiException(String.format("Failed getting info for virtual "
                    + "volume %s in cluster %s with status: %s", virtualVolumeName,
                    clusterName, status));
        }

        // Now parse this response to populate the virtual volume details in the
        // passed system volume info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, virtualVolumeInfo);
            s_logger
                    .info("Updated Virtual Volume Info {}", virtualVolumeInfo.toString());
        } catch (Exception e) {
            throw new VPlexApiException(String.format(
                    "Error processing system volume information: %s", e.getMessage()), e);
        }

        return true;
    }

    /**
     * Gets all consistency groups on the VPLEX.
     * 
     * NOTE: We want the list to be unique, but consistency group names
     * must only be unique in the cluster. So two cluster could potentially
     * have consistency groups with the same name. However, a consistency
     * group created on one cluster can be given visibility to both clusters.
     * In this case, we would find a group on the other cluster with the
     * same name, but it would be the same actual consistency group. So,
     * in cases were there is a name conflict across clusters we must check
     * the visibility to determine if it is the same consistency group or
     * a different one.
     * 
     * @return A list of VPlexConsistencyGroupInfo instances corresponding to
     *         the consistency groups on the VPLEX.
     * 
     * @throws VPlexApiException When an error occurs get the consistency
     *             groups.
     */

    List<VPlexConsistencyGroupInfo> getConsistencyGroups()
            throws VPlexApiException {
        List<VPlexConsistencyGroupInfo> cgInfoList = new ArrayList<VPlexConsistencyGroupInfo>();
        Map<String, VPlexConsistencyGroupInfo> cgMap = new HashMap<String, VPlexConsistencyGroupInfo>();

        for (String clusterName : _vplexApiClient.getClusterIdToNameMap().values()) {
            List<VPlexConsistencyGroupInfo> clusterCgs = getConsistencyGroupsOnCluster(clusterName);
            for (VPlexConsistencyGroupInfo clusterCGInfo : clusterCgs) {
                clusterCGInfo.setClusterName(clusterName);
                String cgName = clusterCGInfo.getName();
                if (!cgMap.containsKey(cgName)) {
                    cgInfoList.add(clusterCGInfo);
                    cgMap.put(clusterCGInfo.getName(), clusterCGInfo);
                } else {
                    VPlexConsistencyGroupInfo cgInMap = cgMap.get(cgName);
                    if (!clusterCGInfo.hasClusterVisibility(cgInMap.getVisibility())) {
                        // The consistency group does not have the same visibility
                        // as the group with the same name already processed, so
                        // it is a different consistency group.
                        cgInfoList.add(clusterCGInfo);
                    }
                }
            }
        }

        return cgInfoList;
    }

    /**
     * Gets consistency group info for the given VPLEX cluster.
     * 
     * @param clusterName the name of the VPLEX cluster to look at
     * @return a list of consistency group info objects for the given cluster
     * 
     * @throws VPlexApiException
     */
    List<VPlexConsistencyGroupInfo> getConsistencyGroupsOnCluster(String clusterName)
            throws VPlexApiException {

        s_logger.info("Getting all consistency groups from VPLEX at " + _vplexApiClient.getBaseURI().toString());

        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_CGS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Get consistency groups request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status == VPlexApiConstants.SUCCESS_STATUS) {
            try {
                return VPlexApiUtils
                        .getResourcesFromResponseContext(uriBuilder.toString(), responseStr,
                                VPlexConsistencyGroupInfo.class);
            } catch (Exception e) {
                throw VPlexApiException.exceptions.errorProcessingConsistencyGroupInformation(e.getLocalizedMessage());
            }
        } else if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            // return an empty list rather than an error
            s_logger.info("VPLEX returned a 404 Not Found for this context, returning an empty list instead.");
            return new ArrayList<VPlexConsistencyGroupInfo>();
        } else {
            throw VPlexApiException.exceptions.failedGettingConsistencyGroupInfo(String.valueOf(status));
        }
    }

    /**
     * Finds the consistency group with the passed name.
     * 
     * @param cgName The name of the consistency group to find.
     * @param clusterInfoList The cluster info.
     * @param fetchAtts true to get the CG attributes.
     * 
     * @return A reference to the VPlexConsistencyGroupInfo
     * 
     * @throws VPlexApiException When an error occurs finding the consistency
     *             group or it is not found.
     */
    VPlexConsistencyGroupInfo findConsistencyGroup(String cgName,
            List<VPlexClusterInfo> clusterInfoList, boolean fetchAtts) throws VPlexApiException {
        return findConsistencyGroup(cgName, clusterInfoList, fetchAtts, false);
    }

    /**
     * Finds the consistency group with the passed name.
     * 
     * @param cgName The name of the consistency group to find.
     * @param clusterInfoList The cluster info.
     * @param fetchAtts true to get the CG attributes.
     * @param retry Indicates retry should occur if the first attempt to find
     *            the consistency group fails.
     * 
     * @return A reference to the VPlexConsistencyGroupInfo
     * 
     * @throws VPlexApiException When an error occurs finding the consistency
     *             group or it is not found.
     */
    VPlexConsistencyGroupInfo findConsistencyGroup(String cgName,
            List<VPlexClusterInfo> clusterInfoList, boolean fetchAtts, boolean retry)
            throws VPlexApiException {

        int retryCount = 0;
        VPlexConsistencyGroupInfo cgInfo = null;
        while (++retryCount <= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES) {
            try {
                cgInfo = null;
                for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                    String clusterId = clusterInfo.getName();
                    List<VPlexConsistencyGroupInfo> allCGInfos = getConsistencyGroupsOnCluster(clusterId);
                    for (VPlexConsistencyGroupInfo info : allCGInfos) {
                        s_logger.info("Consistency Group Info: {}", info.toString());
                        if (info.getName().equals(cgName)) {
                            cgInfo = info;
                            cgInfo.setClusterName(clusterInfo.getName());
                            break;
                        }
                    }

                    // Found it.
                    if (cgInfo != null) {
                        break;
                    }
                }

                if ((cgInfo != null) || (!retry) || (retryCount >= VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    // Throw an exception if not found.
                    if (cgInfo == null) {
                        // Not found.
                        throw VPlexApiException.exceptions.didNotFindCGWithName(cgName);
                    } else if (fetchAtts) {
                        updateConsistencyGroupInfo(cgInfo);
                    }
                    break;
                } else {
                    s_logger.warn("Consistency group not found on try {} of {}",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                }
            } catch (VPlexApiException vae) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding consistency group on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), vae);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw vae;
                }
            } catch (Exception e) {
                if ((retry) && (retryCount < VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES)) {
                    s_logger.error(String.format("Exception finding consistency group on try %d of %d",
                            retryCount, VPlexApiConstants.FIND_NEW_ARTIFACT_MAX_TRIES), e);
                    VPlexApiUtils.pauseThread(VPlexApiConstants.FIND_NEW_ARTIFACT_SLEEP_TIME_MS);
                } else {
                    throw VPlexApiException.exceptions.failureFindingCGWithName(cgName, e);
                }
            }
        }

        return cgInfo;
    }

    /**
     * Gets the attributes for the passed consistency group.
     * 
     * @param cgInfo The consistency group info.
     * 
     * @throws VPlexApiException When an error occurs getting the attributes for
     *             the consistency group.
     */
    void updateConsistencyGroupInfo(VPlexConsistencyGroupInfo cgInfo)
            throws VPlexApiException {

        // Get the URI for the consistency group info request and make the
        // request.
        String cgName = cgInfo.getName();
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(cgInfo.getClusterName());
        uriBuilder.append(VPlexApiConstants.URI_CGS.toString());
        uriBuilder.append(cgName);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Consistency group Info Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failureUpdatingCGStatus(cgName,
                    cgInfo.getClusterName(), String.valueOf(status));
        }

        // Now parse this response to populate the consistency group details in
        // the passed consistency group info.
        try {
            VPlexApiUtils.setAttributeValues(responseStr, cgInfo);
            s_logger.info("Updated Consistency Group Info {}", cgInfo.toString());
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedUpdatingCG(cgName,
                    cgInfo.getClusterName(), e);
        }
    }

    /**
     * Causes the VPLEX to "forget" about the volumes identified by the
     * passed native volume information. Typically called when the calling
     * application has deleted backend volumes and wants the VPLEX to disregard
     * these volumes.
     * 
     * @param nativeVolumeInfoList The native volume information for the
     *            storage volumes to be forgotten.
     */
    void forgetVolumes(List<VolumeInfo> nativeVolumeInfoList) {

        // For the volumes to be forgotten, map them by their
        // storage system Guids.
        Map<String, Set<String>> systemVolumesMap = new HashMap<String, Set<String>>();
        for (VolumeInfo volumeInfo : nativeVolumeInfoList) {
            String systemGuid = volumeInfo.getStorageSystemNativeGuid();
            Set<String> systemVolumes = null;
            if (systemVolumesMap.containsKey(systemGuid)) {
                systemVolumes = systemVolumesMap.get(systemGuid);
            } else {
                systemVolumes = new HashSet<String>();
                systemVolumesMap.put(systemGuid, systemVolumes);
            }
            systemVolumes.add(volumeInfo.getVolumeWWN());
        }

        // Rediscover the storage systems with volumes to be forgotten.
        // When forgetting volumes, they have typically just been
        // removed from the export group that exposes the volumes to
        // the VPLEX. We need to rediscover the storage systems so the
        // VPLEX sees that the storage volumes went away.
        rediscoverStorageSystems(new ArrayList<String>(systemVolumesMap.keySet()));

        // Sleep for a bit to be sure the VPlex completes the
        // discovery prior to calling expand. Gets around
        // an issue with the VPlex software not returning an
        // Async code.
        VPlexApiUtils.pauseThread(60000);

        // Now find the context paths for the logical units that
        // correspond to the volumes to be forgotten.
        Set<String> logUnitsPaths = findLogicalUnits(systemVolumesMap);

        // Now tell the VPLEX to forget these logical units.
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_FORGET_LOG_UNIT);
            s_logger.info("Forget logical units URI is {}", requestURI.toString());
            StringBuilder argBuilder = new StringBuilder();
            for (String logUnitPath : logUnitsPaths) {
                if (argBuilder.length() != 0) {
                    argBuilder.append(",");
                }
                argBuilder.append(logUnitPath);
            }
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_U, argBuilder.toString());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Forget logical units POST data is {}",
                    postDataObject.toString());
            ClientResponse response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Forget logical units response is {}", responseStr);
            int status = response.getStatus();
            response.close();
            if (status != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Forget volumes is completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    s_logger.error("Request to forget logical units failed with Status: {}",
                            response.getStatus());
                    return;
                }
            }
            s_logger.info("Successfully forgot logical units");
        } catch (Exception e) {
            s_logger.error("Exception forgetting logical units: %s", e.getMessage(), e);
        }
    }

    /**
     * Finds the context paths of the logical units corresponding to the passed
     * volumes.
     * 
     * @param systemVolumesMap A map of storage volume WWNs key'd by storage
     *            system.
     * 
     * @return The context paths of the passed volumes.
     */
    private Set<String> findLogicalUnits(Map<String, Set<String>> systemVolumesMap) {

        Set<String> logicalUnitPaths = new HashSet<String>();
        // Get the cluster info.
        List<VPlexClusterInfo> clusterInfoList = getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            // Get the storage systems for the cluster.
            List<VPlexStorageSystemInfo> systemInfoList = getStorageSystemInfoForCluster(clusterInfo
                    .getName());
            // Cycle over the storage systems and determine if it
            // is one with storage volumes to be forgotten.
            for (VPlexStorageSystemInfo systemInfo : systemInfoList) {
                for (Entry<String, Set<String>> entry : systemVolumesMap.entrySet()) {
                    String systemGuid = entry.getKey();
                    if (systemInfo.matches(systemGuid)) {
                        // Get all logical units for this storage
                        // system.
                        Set<String> volumeWWNs = entry.getValue();
                        StringBuilder uriBuilder = new StringBuilder();
                        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
                        uriBuilder.append(clusterInfo.getName());
                        uriBuilder.append(VPlexApiConstants.URI_STORAGE_SYSTEMS.toString());
                        uriBuilder.append(systemInfo.getName());
                        uriBuilder.append(VPlexApiConstants.URI_LOGICAL_UNITS);
                        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                                URI.create(uriBuilder.toString()));
                        s_logger.info("Find logical units request URI is {}",
                                requestURI.toString());
                        ClientResponse response = _vplexApiClient.get(requestURI);
                        String responseStr = response.getEntity(String.class);
                        s_logger.info("Response is {}", responseStr);
                        int status = response.getStatus();
                        response.close();
                        if (status != VPlexApiConstants.SUCCESS_STATUS) {
                            s_logger.error("Failed getting logical units for context {}",
                                    uriBuilder.toString());
                        }

                        // Successful Response
                        List<VPlexLogicalUnitInfo> logUnitInfoList = VPlexApiUtils
                                .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                                        VPlexLogicalUnitInfo.class);

                        // Cycle over the logical units and find the ones
                        // corresponding to the storage volumes to be
                        // forgotten for this storage system.
                        for (VPlexLogicalUnitInfo logUnitInfo : logUnitInfoList) {
                            String logUnitName = logUnitInfo.getName();
                            int indexWWNStart = logUnitName.indexOf(":") + 1;
                            String logUnitWWN = logUnitName.substring(indexWWNStart)
                                    .toUpperCase();
                            if (volumeWWNs.contains(logUnitWWN)) {
                                // Add the logical unit context path
                                // to the list.
                                logicalUnitPaths.add(logUnitInfo.getPath());
                            }
                        }
                        break;
                    }
                }
            }
        }

        return logicalUnitPaths;
    }

    /**
     * Gets the virtual volume info for a given VPLEX cluster.
     * 
     * @param clusterName the name of the VPLEX cluster to look at
     * 
     * @return a list of VPlexVirtualVolumeInfo objects for the given VPLEX cluster
     * 
     * @throws VPlexApiException
     */
    List<VPlexVirtualVolumeInfo> getVirtualVolumesForCluster(String clusterName)
            throws VPlexApiException {

        s_logger.info("Getting all virtual volume information from VPLEX at "
                + _vplexApiClient.getBaseURI().toString());

        // Get the URI for the virtual volume request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(clusterName);
        uriBuilder.append(VPlexApiConstants.URI_VIRTUAL_VOLUMES.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Virtual volumes Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI, VPlexApiConstants.ACCEPT_JSON_FORMAT_1);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status == VPlexApiConstants.SUCCESS_STATUS) {
            try {
                return VPlexApiUtils.getResourcesFromResponseContext(
                        uriBuilder.toString(), responseStr, VPlexVirtualVolumeInfo.class);
            } catch (Exception e) {
                throw VPlexApiException.exceptions.errorProcessingVirtualVolumeInformation(e.getLocalizedMessage());
            }
        } else if (status == VPlexApiConstants.NOT_FOUND_STATUS) {
            // return an empty list rather than an error
            s_logger.info("VPLEX returned a 404 Not Found for this context, returning an empty list instead.");
            return new ArrayList<VPlexVirtualVolumeInfo>();
        } else {
            throw VPlexApiException.exceptions.failedGettingVirtualVolumeInfo(String.valueOf(status));
        }
    }

    /**
     * Discovers and sets the component structure for the passed list
     * of distributed virtual volumes.
     * 
     * @param distVirtualVolumesMap A map of distributed virtual volumes
     *            keyed by the name of the distributed device that supports the
     *            virtual volume.
     */
    void setSupportingComponentsForDistributedVirtualVolumes(
            Map<String, VPlexVirtualVolumeInfo> distVirtualVolumesMap) {
        // Discover and set the component structure for the distributed
        // virtual volume. This is called when doing a deep discovery
        // of the virtual volumes. If we get an error trying to get the
        // component structure for the volume, we simply log an error.
        // This allows the volume discovery to continue, and only the
        // volumes for which the structure could be determined will
        // be returned.
        String virtualVolumeName = null;
        try {
            List<VPlexDistributedDeviceInfo> ddInfoList = getDistributedDeviceInfo();
            for (VPlexDistributedDeviceInfo ddInfo : ddInfoList) {
                String ddName = ddInfo.getName();
                if (distVirtualVolumesMap.containsKey(ddName)) {
                    VPlexVirtualVolumeInfo virtualVolumeForDevice = distVirtualVolumesMap
                            .get(ddName);
                    virtualVolumeName = virtualVolumeForDevice.getName();
                    virtualVolumeForDevice.setSupportingDeviceInfo(ddInfo);
                    // use try/catch and move onto the next one on error
                    setSupportingComponentsForDistributedDevice(ddInfo);
                }
            }
        } catch (Exception e) {
            s_logger.error("An exception occured dicovering the component structure for " +
                    "distributed virtual volume {}", virtualVolumeName, e);
        }
    }

    /**
     * Discovers and sets the component structure for the passed list of local
     * virtual volumes on the cluster with the passed id.
     * 
     * @param localVirtualVolumesMap A map of local virtual volumes keyed by the
     *            name of the top level local device that supports the virtual
     *            volume.
     */
    void setSupportingComponentsForLocalVirtualVolumes(String clusterId,
            Map<String, VPlexVirtualVolumeInfo> localVirtualVolumesMap) {
        // Discover and set the component structure for the local
        // virtual volume. This is called when doing a deep discovery
        // of the virtual volumes. If we get an error trying to get the
        // component structure for the volume, we simply log an error.
        // This allows the volume discovery to continue, and only the
        // volumes for which the structure could be determined will
        // be returned.
        String virtualVolumeName = null;
        try {
            List<VPlexDeviceInfo> deviceInfoList = getLocalDeviceInfoOnCluster(clusterId);
            for (VPlexDeviceInfo deviceInfo : deviceInfoList) {
                String deviceName = deviceInfo.getName();
                if (localVirtualVolumesMap.containsKey(deviceName)) {
                    deviceInfo.setCluster(clusterId);
                    VPlexVirtualVolumeInfo virtualVolumeForDevice = localVirtualVolumesMap.get(deviceName);
                    virtualVolumeName = virtualVolumeForDevice.getName();
                    virtualVolumeForDevice.setSupportingDeviceInfo(deviceInfo);
                    // use try/catch and move onto the next one on error
                    setSupportingComponentsForLocalDevice(deviceInfo);
                }
            }
        } catch (Exception e) {
            s_logger.error("An exception occured dicovering the component structure for " +
                    "local virtual volume {}", virtualVolumeName, e);
        }
    }

    /**
     * Gets the distributed devices for the VPLEX.
     * 
     * @return A list of VPlexDistributedDeviceInfo representing the distributed
     *         devices.
     * 
     * @throws VPlexApiException When an error occurs getting the distributed
     *             device information.
     */
    List<VPlexDistributedDeviceInfo> getDistributedDeviceInfo()
            throws VPlexApiException {
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_DISTRIBUTED_DEVICES);
        s_logger.info("Distributed devices Request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions
                    .failureGettingDistributedDevicesStatus(String.valueOf(status));
        }

        try {
            List<VPlexDistributedDeviceInfo> ddInfoList = VPlexApiUtils
                    .getChildrenFromResponse(
                            VPlexApiConstants.URI_DISTRIBUTED_DEVICES.toString(), responseStr,
                            VPlexDistributedDeviceInfo.class);
            return ddInfoList;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedGettingDistributedDevices(e);
        }
    }

    /**
     * Discovers and sets the supporting components (i.e., top level local
     * devices) for the passed distributed device.
     * 
     * @param ddInfo The distributed device information.
     * 
     * @throws VPlexApiException When an error occurs discovering the supporting
     *             components for the distributed device.
     */
    void setSupportingComponentsForDistributedDevice(VPlexDistributedDeviceInfo ddInfo)
            throws VPlexApiException {
        List<VPlexDeviceInfo> childDeviceInfoList = new ArrayList<VPlexDeviceInfo>();
        List<VPlexDistributedDeviceComponentInfo> componentsInfoList = getDistributedDeviceComponents(ddInfo);
        for (VPlexDistributedDeviceComponentInfo componentInfo : componentsInfoList) {
            updateDistributedDeviceComponent(componentInfo);
            VPlexDeviceInfo childDeviceInfo = new VPlexDeviceInfo();
            childDeviceInfo.setName(componentInfo.getName());
            childDeviceInfo.setPath(componentInfo.getPath());
            childDeviceInfo.setType(VPlexResourceInfo.ResourceType.LOCAL_DEVICE.getResourceType());
            childDeviceInfo.setCluster(componentInfo.getCluster());
            childDeviceInfoList.add(childDeviceInfo);
            setSupportingComponentsForLocalDevice(childDeviceInfo);
        }
        ddInfo.setLocalDeviceInfo(childDeviceInfoList);
    }

    /**
     * Gets the supporting components for the passed distributed device.
     * 
     * @param ddInfo The distributed device information.
     * 
     * @return A list of VPlexDistributedDeviceComponentInfo representing the
     *         supporting components of the distributed device.
     * 
     * @throws VPlexApiException When an error occurs getting the distributed
     *             device components.
     */
    List<VPlexDistributedDeviceComponentInfo> getDistributedDeviceComponents(
            VPlexDistributedDeviceInfo ddInfo) throws VPlexApiException {
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.VPLEX_PATH);
        uriBuilder.append(ddInfo.getPath());
        uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICE_COMP);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Get distributed device components request URI is {}",
                requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Get distributed device components response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions
                    .failureGettingDistDeviceComponentsStatus(ddInfo.getPath(),
                            String.valueOf(status));
        }

        try {
            List<VPlexDistributedDeviceComponentInfo> componentInfoList = VPlexApiUtils
                    .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                            VPlexDistributedDeviceComponentInfo.class);
            return componentInfoList;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedGettingDistDeviceComponents(
                    ddInfo.getPath(), e);
        }
    }

    /**
     * Gets the attributes for the passed distributed device component.
     * 
     * @param componentInfo The distributed device component info
     * 
     * @throws VPlexApiException When an error occurs getting the attributes for
     *             the distributed device component.
     */
    void updateDistributedDeviceComponent(
            VPlexDistributedDeviceComponentInfo componentInfo) throws VPlexApiException {
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.VPLEX_PATH);
        uriBuilder.append(componentInfo.getPath());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Distributed device component request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.updateDistDeviceComponentFailureStatus(
                    componentInfo.getPath(), String.valueOf(status));
        }

        try {
            VPlexApiUtils.setAttributeValues(responseStr, componentInfo);
            s_logger.info("Updated Distributed Device Component Info {}", componentInfo.toString());
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedUpdateDistDeviceComponentInfo(
                    componentInfo.getPath(), e);
        }
    }

    /**
     * Discovers and sets the supporting components (i.e., other local
     * devices and extents) for the passed local device.
     * 
     * @param deviceInfo The local device information.
     * 
     * @throws VPlexApiException When an error occurs discovering the supporting
     *             components for the local device.
     */
    void setSupportingComponentsForLocalDevice(VPlexDeviceInfo deviceInfo)
            throws VPlexApiException {
        List<VPlexExtentInfo> extentInfoList = new ArrayList<VPlexExtentInfo>();
        List<VPlexDeviceInfo> childDeviceInfoList = new ArrayList<VPlexDeviceInfo>();
        List<VPlexLocalDeviceComponentInfo> componentInfoList = getLocalDeviceComponents(deviceInfo);
        for (VPlexLocalDeviceComponentInfo componentInfo : componentInfoList) {
            updateLocalDeviceComponent(componentInfo);
            String componentType = componentInfo.getComponentType();
            if (VPlexResourceInfo.ResourceType.EXTENT.getResourceType().equals(
                    componentType)) {
                VPlexExtentInfo extentInfo = new VPlexExtentInfo();
                extentInfo.setName(componentInfo.getName());
                extentInfo.setPath(componentInfo.getPath());
                extentInfo.setType(componentType);
                extentInfo.setClusterId(deviceInfo.getCluster());
                extentInfoList.add(extentInfo);
                setSupportingComponentsForExtent(extentInfo);
            } else {
                VPlexDeviceInfo childDeviceInfo = new VPlexDeviceInfo();
                childDeviceInfo.setName(componentInfo.getName());
                childDeviceInfo.setPath(componentInfo.getPath());
                childDeviceInfo.setType(componentType);
                childDeviceInfo.setCluster(deviceInfo.getCluster());
                childDeviceInfoList.add(childDeviceInfo);
                setSupportingComponentsForLocalDevice(childDeviceInfo);
            }
        }
        deviceInfo.setExtentInfo(extentInfoList);
        deviceInfo.setChildDeviceInfo(childDeviceInfoList);
    }

    /**
     * Gets the supporting components for the passed local device.
     * 
     * @param localDeviceInfo The local device information.
     * 
     * @return A list of VPlexLocalDeviceComponentInfo representing the
     *         supporting components of the local device.
     * 
     * @throws VPlexApiException When an error occurs getting the local device
     *             components.
     */
    List<VPlexLocalDeviceComponentInfo> getLocalDeviceComponents(
            VPlexResourceInfo localDeviceInfo) throws VPlexApiException {
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.VPLEX_PATH);
        uriBuilder.append(localDeviceInfo.getPath());
        uriBuilder.append(VPlexApiConstants.URI_COMPONENTS);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Get components for local device URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Get components for local device response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions
                    .failureGettingComponentsForLocalDeviceStatus(localDeviceInfo.getPath(),
                            String.valueOf(status));
        }

        try {
            List<VPlexLocalDeviceComponentInfo> componentInfoList = VPlexApiUtils
                    .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                            VPlexLocalDeviceComponentInfo.class);
            return componentInfoList;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedGettingComponentsForLocalDevice(
                    localDeviceInfo.getPath(), e);
        }
    }

    /**
     * Gets the attributes for the passed local device component.
     * 
     * @param componentInfo The local device component info
     * 
     * @throws VPlexApiException When an error occurs getting the attributes for
     *             the local device component.
     */
    void updateLocalDeviceComponent(VPlexLocalDeviceComponentInfo componentInfo)
            throws VPlexApiException {
        // Get the URI for the cluster info request and make the request.
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.VPLEX_PATH);
        uriBuilder.append(componentInfo.getPath());
        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Local device component request URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.updatelLocalDeviceComponentFailureStatus(
                    componentInfo.getPath(), String.valueOf(status));
        }

        try {
            VPlexApiUtils.setAttributeValues(responseStr, componentInfo);
            s_logger.info("Updated Local Device Component Info {}", componentInfo.toString());
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedUpdateLocalDeviceComponentInfo(
                    componentInfo.getPath(), e);
        }
    }

    /**
     * Discovers and sets the supporting components (i.e., storage volume) for
     * the passed local device.
     * 
     * @param extentInfo The extent information.
     * 
     * @throws VPlexApiException When an error occurs discovering the supporting
     *             components for the extent.
     */
    void setSupportingComponentsForExtent(VPlexExtentInfo extentInfo)
            throws VPlexApiException {
        // An extent should have a single component which is the storage volume
        // on which it was built.
        List<VPlexResourceInfo> componentInfoList = getExtentComponents(extentInfo);
        if (componentInfoList.size() == 1) {
            VPlexResourceInfo componentInfo = componentInfoList.get(0);
            VPlexStorageVolumeInfo storageVolumeInfo = new VPlexStorageVolumeInfo();
            storageVolumeInfo.setName(componentInfo.getName());
            storageVolumeInfo.setPath(componentInfo.getPath());
            storageVolumeInfo.setType(VPlexResourceInfo.ResourceType.STORAGE_VOLUME
                    .getResourceType());
            storageVolumeInfo.setClusterId(extentInfo.getClusterId());
            extentInfo.setStorageVolumeInfo(storageVolumeInfo);
        } else {
            throw VPlexApiException.exceptions.moreThanOneComponentForExtent(extentInfo
                    .getPath());
        }
    }

    /**
     * Gets the supporting components for the passed extent.
     * 
     * @param extentInfo The extent information.
     * 
     * @return A list of VPlexResourceInfo representing the supporting
     *         components of the extent.
     * 
     * @throws VPlexApiException When an error occurs getting the extent
     *             components.
     */
    List<VPlexResourceInfo> getExtentComponents(VPlexExtentInfo extentInfo)
            throws VPlexApiException {
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(VPlexApiConstants.VPLEX_PATH);
        uriBuilder.append(extentInfo.getPath());
        uriBuilder.append(VPlexApiConstants.URI_COMPONENTS);
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(uriBuilder.toString()));
        s_logger.info("Get components for extent URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.get(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Get components for extent response is {}", responseStr);
        int status = response.getStatus();
        response.close();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failureGettingExtentComponentsStatus(
                    extentInfo.getName(), String.valueOf(status));
        }

        try {
            List<VPlexResourceInfo> componentInfoList = VPlexApiUtils
                    .getChildrenFromResponse(uriBuilder.toString(), responseStr,
                            VPlexResourceInfo.class);
            return componentInfoList;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedGettingExtentComponents(
                    extentInfo.getName(), e);
        }
    }

    /**
     * Returns a map of Initiator WWN to Initiator Name
     * 
     * @param clusterName VPlex cluster ID
     * @return map of Initiator WWN to Initiator Name
     */
    Map<String, String> getInitiatorWwnToNameMap(String clusterName) {
        Map<String, String> result = new HashMap<String, String>();

        List<VPlexInitiatorInfo> initiatorInfoList = getInitiatorInfoForCluster(clusterName);

        for (VPlexInitiatorInfo initiatorInfo : initiatorInfoList) {
            if (initiatorInfo.getName() != null
                    && initiatorInfo.getPortWwn() != null) {
                result.put(initiatorInfo.getPortWwn(), initiatorInfo.getName());
            }
        }
        return result;
    }

    /**
     * Returns a map of Initiator Name to Initiator WWN
     * 
     * @param clusterName VPlex cluster ID
     * @return map of Initiator Name to Initiator WWN
     */
    Map<String, String> getInitiatorNameToWwnMap(String clusterName) {
        Map<String, String> result = new HashMap<String, String>();

        List<VPlexInitiatorInfo> initiatorInfoList;
        initiatorInfoList = getInitiatorInfoForCluster(clusterName);

        for (VPlexInitiatorInfo initiatorInfo : initiatorInfoList) {
            if (initiatorInfo.getName() != null
                    && initiatorInfo.getPortWwn() != null) {
                result.put(initiatorInfo.getName(), initiatorInfo.getPortWwn());
            }
        }
        return result;
    }

    /**
     * Attempts to refresh the given VPLEX contexts.
     * 
     * @param contexts The list of context paths to be refreshed.
     */
    void refreshContexts(List<String> contexts) {
        // Create the request.
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_REFRESH_CONTEXT);
        s_logger.info("Refresh contexts URI is {}", requestURI.toString());

        // Build the post data specifying the contexts to be refreshed.
        Map<String, String> argsMap = new HashMap<String, String>();
        StringBuilder argsBuilder = new StringBuilder();
        for (String context : contexts) {
            if (argsBuilder.length() != 0) {
                argsBuilder.append(",");
            }
            argsBuilder.append(context);
        }
        argsMap.put(VPlexApiConstants.ARG_DASH_C, argsBuilder.toString());
        JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
        s_logger.info("Refresh contexts POST data is {}", postDataObject.toString());

        // Execute the request to refresh the passed contexts.
        ClientResponse response = null;
        try {
            response = _vplexApiClient.post(requestURI, postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Refresh contexts response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Refresh contexts is completing asynchrounously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    s_logger.warn("Refresh of contexts {} failed", contexts);
                }
            }
            s_logger.info("Refresh contexts was successful");
        } catch (Exception e) {
            s_logger.warn("Exception during context refresh", e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Returns a List of VPlexStorageVolumeInfo storage volumes for the given
     * device name, locality (virtual volume type), and cluster name. If it's
     * determined the top-level device is mirrored (i.e., has two child devices
     * in a RAID-1 configuration), then the VPLEX API request URI will go one
     * level deeper.
     * 
     * @param deviceName the top-level device name to query on
     * @param virtualVolumeType the virtual volume type (local or distributed)
     * @param clusterName the cluster name
     * @param hasMirror if the top level device is mirrored
     * 
     * @return a list of VPlexStorageVolumeInfo storage volumes comprising the device
     * @throws VPlexApiException
     */
    public List<VPlexStorageVolumeInfo> getStorageVolumesForDevice(
            String deviceName, String virtualVolumeType, String clusterName, boolean hasMirror) throws VPlexApiException {

        long start = System.currentTimeMillis();
        s_logger.info("Getting backend storage volume wwn info for {} volume {} from VPLEX at "
                + _vplexApiClient.getBaseURI().toString(), virtualVolumeType, deviceName);

        StringBuilder uriBuilder = new StringBuilder();
        if (VPlexApiConstants.LOCAL_VIRTUAL_VOLUME.equals(virtualVolumeType)) {
            // format /vplex/clusters/*/devices
            // /DEVICE_NAME/components/*/components/*
            uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
            if (null != clusterName && !clusterName.isEmpty()) {
                uriBuilder.append(clusterName);
            } else {
                uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            }
            uriBuilder.append(VPlexApiConstants.URI_DEVICES.toString());
            uriBuilder.append(deviceName);
            uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
            uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
            uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            if (hasMirror) {
                uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
                uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            }
        } else if (VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME.equals(virtualVolumeType)) {
            // format /vplex/distributed-storage/distributed-devices
            // /DEVICE_NAME/distributed-device-components/*/components/*/components/*
            uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICES.toString());
            uriBuilder.append(deviceName);
            uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICE_COMP.toString());
            uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
            uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
            uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            if (hasMirror) {
                uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
                uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
            }
        } else {
            String reason = "invalid VPLEX locality for device " + deviceName + ": " + virtualVolumeType;
            s_logger.error(reason);
            throw VPlexApiException.exceptions.failedGettingStorageVolumeInfoForIngestion(reason);
        }

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Storage Volume Request URI is {}", requestURI.toString());

        ClientResponse response = _vplexApiClient.get(requestURI,
                VPlexApiConstants.ACCEPT_JSON_FORMAT_1,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_DEFAULT_VALUE);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            s_logger.error(responseStr);
            throw VPlexApiException.exceptions.failedGettingStorageVolumeInfoForIngestion(String.valueOf(status));
        }

        // Successful Response
        try {
            List<VPlexStorageVolumeInfo> storageVolumeInfoList =
                    VPlexApiUtils.getResourcesFromResponseContext(uriBuilder.toString(),
                            responseStr, VPlexStorageVolumeInfo.class);

            s_logger.info("TIMER: getStorageVolumesForDevice took {}ms",
                    System.currentTimeMillis() - start);

            return storageVolumeInfoList;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedGettingStorageVolumeInfoForIngestion(e.getLocalizedMessage());
        }
    }

    /**
     * Returns the top-level supporting device name for a given storage volume native id,
     * wwn, and backend array serial number. Uses the storage-volume used-by VPLEX CLI
     * command to do a reverse look up of the parent device for a storage volume
     * based on the native id, wwn, and array serial number.
     * 
     * @param volumeNativeId the storage volume's native id
     * @param wwn the storage volume's wwn
     * @param backendArraySerialNum the serial number of the backend array
     * 
     * @return the name of the top level device for the given storage volume
     * @throws VPlexApiException
     */
    @Deprecated
    public String getDeviceForStorageVolume(String volumeNativeId,
            String wwn, String backendArraySerialNum) throws VPlexApiException {

        long start = System.currentTimeMillis();
        s_logger.info("Getting device name for array {} volume {} (wwn: {}) from VPLEX at "
                + _vplexApiClient.getBaseURI().toString(), backendArraySerialNum, volumeNativeId);

        StringBuilder contextArgBuilder = new StringBuilder();
        // format /storage-volume+used-by
        // payload {"args":"-d \/clusters\/*\/storage-elements\/storage-volumes\/*APM00140844981*01735"}
        contextArgBuilder.append(VPlexApiConstants.URI_CLUSTERS_RELATIVE.toString());
        contextArgBuilder.append(VPlexApiConstants.WILDCARD.toString());
        contextArgBuilder.append(VPlexApiConstants.URI_STORAGE_VOLUMES.toString());
        String contextArg = contextArgBuilder.toString();

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(VPlexApiConstants.URI_STORAGE_VOLUME_USED_BY.toString()));

        s_logger.info("Find device for storage volume request URI is {}", requestURI.toString());

        // the following will try to find a storage-volume used-by structure
        // based on several possible storage-volume element name patterns as defined
        // in the getVolumeNamePattern method below. this is a best effort.
        // this uses the regex/wildcard name matching feature for context names
        // to try to find a storage volume based on the native id, wwn, and array serial number

        // the max number of patterns possibly returned by getVolumeNamePattern
        int numPatterns = 4;
        boolean success = false;
        String responseStr = "";
        for (int i = 0; i < numPatterns; i++) {
            String pattern = getVolumeNamePattern(i, volumeNativeId, wwn, backendArraySerialNum);
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_D, contextArg + pattern);

            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Find device for storage volume POST data is {}",
                    postDataObject.toString());
            ClientResponse response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            responseStr = response.getEntity(String.class);
            s_logger.info("Find device for storage volume response is {}", responseStr);
            int status = response.getStatus();
            response.close();

            if (status != VPlexApiConstants.SUCCESS_STATUS) {
                // try another pattern
                continue;
            } else {
                success = true;
                break;
            }
        }

        if (!success) {
            throw VPlexApiException.exceptions
                    .failedGettingDeviceNameForStorageVolume("no volume name patterns worked");
        }

        // Successful Response
        String customData = VPlexApiUtils.getCustomDataFromResponse(responseStr);

        // this custom data parsing hackage is very uncomfortable...
        // the response payload comes back in a really grungy format like this:
        // /clusters/cluster-1/devices/device_VAPM00140844981-01735:\n
        // extent_VAPM00140844981-01735_1\n
        // VAPM00140844981-01735\n\n
        String deviceName = null;
        try {
            s_logger.info("custom data is " + customData);
            String[] lines = customData.split(":\n");
            s_logger.info("device context is: " + lines[0]);
            String[] subLines = lines[0].split("/");
            deviceName = subLines[subLines.length - 1];
            s_logger.info("returning device name: " + deviceName);
        } catch (Exception ex) {
            String reason = "could not parse custom data string to find device name: " + customData;
            s_logger.error(reason);
            throw VPlexApiException.exceptions
                    .failedGettingDeviceNameForStorageVolume(reason);
        }

        s_logger.info("TIMER: getDeviceForStorageVolume took {}ms",
                System.currentTimeMillis() - start);

        return deviceName;
    }

    /**
     * A storage-volume name pattern generator.
     * 
     * @param i the pattern number to get
     * @param volumeNativeId the volume's native id
     * @param wwn the volume's WWN
     * @param backendArraySerialNum the backend array serial number
     * @return
     */
    @Deprecated
    private String getVolumeNamePattern(int i, String volumeNativeId,
            String wwn, String backendArraySerialNum) {
        String pattern = "";

        switch (i) {
            case 0:
                // *[serialnum]*[deviceid] (this is the standard ViPR claimed volume format)
                pattern = VPlexApiConstants.WILDCARD
                        + backendArraySerialNum
                        + VPlexApiConstants.WILDCARD
                        + volumeNativeId;
                break;
            case 1:
                // *[wwn] (seems by default vols get VPD83T3:wwn for a name)
                pattern = VPlexApiConstants.WILDCARD
                        + wwn
                        + VPlexApiConstants.WILDCARD;
                break;
            case 2:
                // *[wwn].toLowerCase (the used-by command seems to be case-sensitive,
                // and many vol names are lower case)
                pattern = VPlexApiConstants.WILDCARD
                        + wwn.toLowerCase()
                        + VPlexApiConstants.WILDCARD;
                break;
            case 3:
                // *[wwn].substring(5) (for cases where the wwn was too long for
                // the 63-char limit, like ViPR claimed Xtremio vols)
                pattern = VPlexApiConstants.WILDCARD
                        + wwn.substring(5).toLowerCase()
                        + VPlexApiConstants.WILDCARD;
                break;
        }

        return pattern;
    }

    /**
     * Returns a VPlexDistributedDeviceInfo object for the given device. For each leg,
     * the device geometry (RAID configuration) is analyzed to ensure an acceptable component
     * type for ingestion is present. RAID-0 is acceptable as is. If RAID-1 is found,
     * then the children will need to be analyzed to make sure they are composed
     * of only RAID-0 devices (by calling getDeviceComponentInfoForIngestion).
     * RAID-C volumes at this level will be rejected.
     * 
     * @param deviceName the name of the device
     * 
     * @return a VPlexResourceInfo object for the device name
     * @throws VPlexApiException
     */
    public VPlexDistributedDeviceInfo getDeviceStructureForDistributedIngestion(
            String deviceName) throws VPlexApiException {

        long start = System.currentTimeMillis();
        s_logger.info("Getting device structure info for device {} from VPLEX at "
                + _vplexApiClient.getBaseURI().toString(), deviceName);

        StringBuilder uriBuilder = new StringBuilder();
        // format /vplex/distributed-storage/distributed-devices
        // /DEVICE_NAME/distributed-device-components/*
        uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICES.toString());
        uriBuilder.append(deviceName);
        uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICE_COMP.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Distributed Device Info Request URI is {}", requestURI.toString());

        ClientResponse response = _vplexApiClient.get(requestURI,
                VPlexApiConstants.ACCEPT_JSON_FORMAT_1,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_DEFAULT_VALUE);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failedGettingDeviceStructure(String.valueOf(status));
        }

        VPlexDistributedDeviceInfo parentDevice = new VPlexDistributedDeviceInfo();
        parentDevice.setName(deviceName);

        // Successful Response
        List<VPlexDeviceInfo> deviceInfoList =
                VPlexApiUtils.getResourcesFromResponseContext(uriBuilder.toString(),
                        responseStr, VPlexDeviceInfo.class);

        for (VPlexDeviceInfo componentDevice : deviceInfoList) {
            switch (componentDevice.getGeometry().toLowerCase()) {
                case VPlexApiConstants.ARG_GEOMETRY_RAID0:
                    s_logger.info("top-level device geometry is raid-0 for component {}, no further info needed",
                            componentDevice.getName());
                    break;
                case VPlexApiConstants.ARG_GEOMETRY_RAID1:
                    s_logger.info("top-level device geometry is raid-1 for component {}, need to find mirror info",
                            componentDevice.getName());
                    List<VPlexDeviceInfo> childDeviceInfos =
                            getDeviceComponentInfoForIngestion(componentDevice);
                    componentDevice.setChildDeviceInfo(childDeviceInfos);
                    break;
                case VPlexApiConstants.ARG_GEOMETRY_RAIDC:
                default:
                    String reason = "invalid component device geometry "
                            + componentDevice.getGeometry() + " for component " + componentDevice.getName();
                    s_logger.error(reason);
                    throw VPlexApiException.exceptions.deviceStructureIsIncompatibleForIngestion(reason);
            }
        }

        parentDevice.setGeometry(VPlexApiConstants.ARG_GEOMETRY_RAID1);
        parentDevice.setLocalDeviceInfo(deviceInfoList);
        if (!deviceInfoList.isEmpty()) {
            s_logger.info("found these distributed component devices for VPLEX device {}:", parentDevice.getName());
            for (VPlexDeviceInfo info : deviceInfoList) {
                s_logger.info(info.toString());
            }
        }

        s_logger.info("TIMER: getDeviceStructureForDistributedIngestion took {}ms",
                System.currentTimeMillis() - start);

        return parentDevice;
    }

    /**
     * Returns a VPlexDeviceInfo object for the given device. The device geometry
     * (RAID configuration) is analyzed to ensure an acceptable component type
     * for ingestion is present. RAID-0 is acceptable as is. If RAID-1 is found,
     * then the children will need to be analyzed to make sure they are composed
     * of only RAID-0 devices (by calling getDeviceComponentInfoForIngestion).
     * RAID-C volumes at this level will be rejected.
     * 
     * @param deviceName the name of the device
     * 
     * @return a VPlexResourceInfo object for the device name
     * @throws VPlexApiException
     */
    public VPlexDeviceInfo getDeviceStructureForLocalIngestion(
            String deviceName) throws VPlexApiException {

        long start = System.currentTimeMillis();
        s_logger.info("Getting device structure info for device {} from VPLEX at "
                + _vplexApiClient.getBaseURI().toString(), deviceName);

        StringBuilder uriBuilder = new StringBuilder();
        // format /vplex/clusters/*/devices/DEVICE_NAME
        uriBuilder.append(VPlexApiConstants.URI_CLUSTERS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        uriBuilder.append(VPlexApiConstants.URI_DEVICES.toString());
        uriBuilder.append(deviceName);

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Local Device Info Request URI is {}", requestURI.toString());

        ClientResponse response = _vplexApiClient.get(requestURI,
                VPlexApiConstants.ACCEPT_JSON_FORMAT_1,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_DEFAULT_VALUE);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failedGettingDeviceStructure(String.valueOf(status));
        }

        VPlexDeviceInfo device = null;

        // Successful Response
        List<VPlexDeviceInfo> deviceInfoList =
                VPlexApiUtils.getResourcesFromResponseContext(uriBuilder.toString(),
                        responseStr, VPlexDeviceInfo.class);
        if (deviceInfoList.size() == 1) {
            device = deviceInfoList.get(0);

            switch (device.getGeometry().toLowerCase()) {
                case VPlexApiConstants.ARG_GEOMETRY_RAID0:
                    s_logger.info("top-level device geometry is raid-0 for device {}, no further info needed",
                            device.getName());
                    break;
                case VPlexApiConstants.ARG_GEOMETRY_RAID1:
                    s_logger.info("top-level device geometry is raid-1 for device {}, finding children",
                            device.getName());
                    List<VPlexDeviceInfo> componentDeviceInfoList =
                            getDeviceComponentInfoForIngestion(device);
                    device.setChildDeviceInfo(componentDeviceInfoList);
                    break;
                case VPlexApiConstants.ARG_GEOMETRY_RAIDC:
                default:
                    String reason = "invalid component device geometry "
                            + device.getGeometry() + " for component " + device.getName();
                    s_logger.error(reason);
                    throw VPlexApiException.exceptions.deviceStructureIsIncompatibleForIngestion(reason);
            }
        } else {
            String reason = "invalid top level device configuration";
            s_logger.error(reason);
            throw VPlexApiException.exceptions.deviceStructureIsIncompatibleForIngestion(reason);
        }

        if (!deviceInfoList.isEmpty()) {
            s_logger.info("found this local device component info for VPLEX device {}:", deviceName);
            for (VPlexDeviceInfo info : deviceInfoList) {
                s_logger.info(info.toString());
            }
        }

        s_logger.info("TIMER: getDeviceStructureForLocalIngestion took {}ms",
                System.currentTimeMillis() - start);

        return device;
    }

    /**
     * Returns a List of child VPlexDeviceInfo components for a given
     * VPlexDeviceInfo parent device. The device geometry (RAID configuration)
     * is analyzed to ensure an acceptable component type for ingestion
     * is present. Only RAID-0 is acceptable for a child device. RAID-1 and
     * RAID-C at this level will be rejected for ingestion purposes.
     * 
     * @param parentDevice the parent VPlexDeviceInfo
     * 
     * @return a List of child VPlexDeviceInfo objects for the parent
     * @throws VPlexApiException
     */
    public List<VPlexDeviceInfo> getDeviceComponentInfoForIngestion(
            VPlexDeviceInfo parentDevice) throws VPlexApiException {

        long start = System.currentTimeMillis();
        s_logger.info("Getting device component info for {} from VPLEX at "
                + _vplexApiClient.getBaseURI().toString(), parentDevice.getName());

        StringBuilder uriBuilder = new StringBuilder();
        // /vplex/clusters/cluster-1/devices/device_VAPM00140844981-01736/components/*
        uriBuilder.append(VPlexApiConstants.VPLEX_PATH);
        uriBuilder.append(parentDevice.getPath());
        uriBuilder.append(VPlexApiConstants.URI_COMPONENTS.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Child Device Component Info Request URI is {}", requestURI.toString());

        ClientResponse response = _vplexApiClient.get(requestURI,
                VPlexApiConstants.ACCEPT_JSON_FORMAT_1,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_DEFAULT_VALUE);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failedGettingDeviceStructure(String.valueOf(status));
        }

        // Successful Response
        List<VPlexDeviceInfo> deviceInfoList =
                VPlexApiUtils.getResourcesFromResponseContext(uriBuilder.toString(),
                        responseStr, VPlexDeviceInfo.class);

        for (VPlexDeviceInfo device : deviceInfoList) {
            switch (device.getGeometry().toLowerCase()) {
                case VPlexApiConstants.ARG_GEOMETRY_RAID0:
                    s_logger.info("component device geometry is raid-0 for device {}, no further info needed",
                            device.getName());
                    break;
                case VPlexApiConstants.ARG_GEOMETRY_RAID1:
                case VPlexApiConstants.ARG_GEOMETRY_RAIDC:
                default:
                    String reason = "invalid component device geometry "
                            + device.getGeometry() + " for component " + device.getName();
                    s_logger.error(reason);
                    throw VPlexApiException.exceptions.deviceStructureIsIncompatibleForIngestion(reason);
            }
        }

        if (!deviceInfoList.isEmpty()) {
            s_logger.info("found these child devices for VPLEX device {}:", parentDevice.getName());
            for (VPlexDeviceInfo info : deviceInfoList) {
                s_logger.info(info.toString());
            }
        }

        s_logger.info("TIMER: getDeviceComponentInfoForIngestion took {}ms",
                System.currentTimeMillis() - start);

        return deviceInfoList;
    }

    /**
     * Returns a Map of distributed device component context
     * paths from the VPLEX API to VPLEX cluster names.
     * 
     * @return a Map of distributed device component context
     *         paths from the VPLEX API to VPLEX cluster names
     * 
     * @throws VPlexApiException
     */
    public Map<String, String> getDistributedDevicePathToClusterMap()
            throws VPlexApiException {

        long start = System.currentTimeMillis();
        s_logger.info("Getting distributed device path to cluster id map from VPLEX at "
                + _vplexApiClient.getBaseURI().toString());

        StringBuilder uriBuilder = new StringBuilder();
        // format /vplex/distributed-storage/distributed-devices/*/distributed-device-components/*
        uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICES.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());
        uriBuilder.append(VPlexApiConstants.URI_DISTRIBUTED_DEVICE_COMP.toString());
        uriBuilder.append(VPlexApiConstants.WILDCARD.toString());

        URI requestURI = _vplexApiClient.getBaseURI().resolve(URI.create(uriBuilder.toString()));
        s_logger.info("Distributed Device Component Info Request URI is {}", requestURI.toString());

        ClientResponse response = _vplexApiClient.get(requestURI,
                VPlexApiConstants.ACCEPT_JSON_FORMAT_1,
                VPlexApiConstants.CACHE_CONTROL_MAXAGE_DEFAULT_VALUE);
        String responseStr = response.getEntity(String.class);
        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            throw VPlexApiException.exceptions.failedGettingDeviceStructure(String.valueOf(status));
        }

        // Successful Response
        List<VPlexDeviceInfo> deviceInfoList =
                VPlexApiUtils.getResourcesFromResponseContext(uriBuilder.toString(),
                        responseStr, VPlexDeviceInfo.class);

        Map<String, String> distributedDevicePathToClusterMap = new HashMap<String, String>();
        for (VPlexDeviceInfo device : deviceInfoList) {
            distributedDevicePathToClusterMap.put(device.getPath(), device.getCluster());
        }

        s_logger.info("TIMER: getDistributedDevicePathToClusterMap took {}ms",
                System.currentTimeMillis() - start);

        return distributedDevicePathToClusterMap;
    }

    /**
     * Calls the VPLEX CLI "drill-down" command for the given device name.
     * 
     * @param deviceName a device name to check with the drill-down command
     * @return the String drill-down command response from the VPLEX API
     * @throws VPlexApiException if the device structure is incompatible with ViPR
     */
    public String getDrillDownInfoForDevice(String deviceName) throws VPlexApiException {

        ClientResponse response = null;
        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_DRILL_DOWN);
        s_logger.info("Drill-down command URI is {}", requestURI.toString());

        Map<String, String> argsMap = new HashMap<String, String>();
        argsMap.put(VPlexApiConstants.ARG_DASH_R, deviceName);
        JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
        s_logger.info("Drill-down command POST data is {}", postDataObject.toString());

        response = _vplexApiClient.post(requestURI, postDataObject.toString());
        String responseStr = response.getEntity(String.class);
        s_logger.info("Drill-down command response is {}", responseStr);

        int status = response.getStatus();
        response.close();

        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                s_logger.info("Drill-down command is completing asynchronously");
                responseStr = _vplexApiClient.waitForCompletion(response);
                s_logger.info("Task Response is {}", responseStr);
            } else {
                throw VPlexApiException.exceptions.failedToExecuteDrillDownCommand(deviceName, responseStr);
            }
        }

        String customData = VPlexApiUtils.getCustomDataFromResponse(responseStr);
        return customData;
    }
}
