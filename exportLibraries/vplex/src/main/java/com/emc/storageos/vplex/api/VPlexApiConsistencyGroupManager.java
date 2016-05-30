/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse;

/**
 * VPlexApiConsistencyGroupManager provides methods for managing consistency groups.
 */
public class VPlexApiConsistencyGroupManager {

    // Logger reference.
    private static Logger s_logger = LoggerFactory.getLogger(VPlexApiConsistencyGroupManager.class);

    // A reference to the API client.
    private final VPlexApiClient _vplexApiClient;

    /**
     * Package protected constructor.
     * 
     * @param client A reference to the API client.
     */
    VPlexApiConsistencyGroupManager(VPlexApiClient client) {
        _vplexApiClient = client;
    }

    /**
     * Creates a consistency group with the passed name on the cluster with the
     * passed name.
     * 
     * @param cgName The name for the consistency group.
     * @param clusterName The name of the cluster on which the group is created.
     * @param isDistributed true if the CG will hold distributed volumes.
     * 
     * @throws VPlexApiException When an error occurs creating the consistency
     *             group.
     */
    void createConsistencyGroup(String cgName, String clusterName,
            boolean isDistributed) throws VPlexApiException {
        s_logger.info("Request to create consistency group {} on cluster {}", cgName,
                clusterName);

        // Find the cluster so we can get the cluster path.
        VPlexClusterInfo clusterInfo = null;
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        for (VPlexClusterInfo info : clusterInfoList) {
            if (info.getName().equals(clusterName)) {
                clusterInfo = info;
                break;
            }
        }

        // Error if not found.
        if (clusterInfo == null) {
            throw VPlexApiException.exceptions.failedToFindCluster(clusterName);
        }

        // Create the consistency group on the cluster.
        ClientResponse response = null;
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_CREATE_CG);
            s_logger.info("Create consistency group URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_N, cgName);
            argsMap.put(VPlexApiConstants.ARG_DASH_C, clusterInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Create consistency group POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Create consistency group response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Consistency group creation completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions
                            .createConsistencyGroupFailureStatus(cgName,
                                    String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Successfully created consistency group");

            // Find the consistency group
            VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName,
                    Collections.singletonList(clusterInfo), false, true);

            // Set the consistency group properties.
            setAutoResumeAtLoser(cgInfo, true);
            if (isDistributed) {
                // For distributed the visibility and storage-at-clusters must
                // be set to both clusters. The detach rule is set to winner
                // at the cluster on which the consistency group is created.
                s_logger.info("Is Distributed");
                setConsistencyGroupVisibility(cgInfo, clusterInfoList);
                setConsistencyGroupStorageClusters(cgInfo, clusterInfoList);
                setDetachRuleWinner(cgInfo, clusterInfo);
            } else {
                setConsistencyGroupStorageClusters(cgInfo, Collections.singletonList(clusterInfo));
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedCreatingConsistencyGroup(cgName, e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Sets the visibility of the consistency group to the clusters in the
     * passed list.
     * 
     * @param cgInfo A reference to the consistency group info.
     * @param clusterInfoList The list of clusters for which the CG should have
     *            visibility.
     * 
     * @throws VPlexApiException When an error occurs setting the consistency
     *             group visibility.
     */
    void setConsistencyGroupVisibility(VPlexConsistencyGroupInfo cgInfo,
            List<VPlexClusterInfo> clusterInfoList) throws VPlexApiException {

        ClientResponse response = null;
        try {
            // Build the request path.
            int count = 0;
            StringBuilder pathBuilder = new StringBuilder();
            pathBuilder.append(VPlexApiConstants.VPLEX_PATH);
            pathBuilder.append(cgInfo.getPath());
            pathBuilder.append("?");
            pathBuilder.append(VPlexConsistencyGroupInfo.CGAttribute.VISIBILITY
                    .getAttributeName());
            pathBuilder.append("=");
            for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                if (count > 0) {
                    pathBuilder.append(",");
                }
                pathBuilder.append(clusterInfo.getPath());
                count++;
            }

            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    URI.create(pathBuilder.toString()));
            s_logger.info("Set CG visibility URI is {}", requestURI.toString());
            response = _vplexApiClient.put(requestURI);
            String responseStr = response.getEntity(String.class);
            s_logger.info("Set CG visibility response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Set CG visibility is completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.setCGVisibilityFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedSettingCGVisibility(
                    cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Sets the "storage-at-clusters" attribute of the consistency group to the
     * clusters in the passed list.
     * 
     * @param cgInfo A reference to the consistency group info.
     * @param clusterInfoList The list of clusters for which the CG has storage.
     * 
     * @throws VPlexApiException When an error occurs setting the clusters
     *             at which the consistency group has storage.
     */
    void setConsistencyGroupStorageClusters(VPlexConsistencyGroupInfo cgInfo,
            List<VPlexClusterInfo> clusterInfoList) throws VPlexApiException {

        // Build the request path.
        ClientResponse response = null;
        try {
            int count = 0;
            StringBuilder pathBuilder = new StringBuilder();
            pathBuilder.append(VPlexApiConstants.VPLEX_PATH);
            pathBuilder.append(cgInfo.getPath());
            pathBuilder.append("?");
            pathBuilder.append(VPlexConsistencyGroupInfo.CGAttribute.STORAGE_AT_CLUSTER
                    .getAttributeName());
            pathBuilder.append("=");
            if (clusterInfoList.isEmpty()) {
                pathBuilder.append("''");
            } else {
                for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                    if (count > 0) {
                        pathBuilder.append(",");
                    }
                    pathBuilder.append(clusterInfo.getPath());
                    count++;
                }
            }

            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    URI.create(pathBuilder.toString()));
            s_logger.info("Set CG storage clusters URI is {}", requestURI.toString());
            response = _vplexApiClient.put(requestURI);
            String responseStr = response.getEntity(String.class);
            s_logger.info("Set CG storage clusters response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Set CG storage clusters is completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.setCGStorageAtClustersFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedSettingCGStorageAtClusters(
                    cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Sets the detach rule for the passed consistency group to "winner", where
     * the passed cluster is the winning cluster.
     * 
     * @param cgInfo A reference to the consistency group info.
     * @param clusterInfo The info for the cluster to be the winner.
     * 
     * @throws VPlexApiException When an error occurs setting the detach rule to
     *             winner for the consistency group.
     */
    void setDetachRuleWinner(VPlexConsistencyGroupInfo cgInfo, VPlexClusterInfo clusterInfo)
            throws VPlexApiException {

        ClientResponse response = null;
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_CG_DETACH_RULE_WINNER);
            s_logger.info("Set CG detach rule winner URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_C, clusterInfo.getPath());
            argsMap.put(VPlexApiConstants.ARG_DASH_D, Integer.valueOf(VPlexApiConstants.DETACH_DELAY).toString());
            argsMap.put(VPlexApiConstants.ARG_DASH_G, cgInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Set CG detach rule winner is {}", postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Set CG detach rule winner response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Set CG detach rule winner is completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.setDetachRuleWinnerFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedSettingDetachRuleWinner(
                    cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Sets the RP enabled tag on the consistency group to the clusters in the
     * passed list.
     * 
     * @param cgName The consistency group to update.
     * @param clusterInfoList The list of clusters
     * 
     * @throws VPlexApiException When an error occurs setting the consistency
     *             group visibility.
     */
    void setConsistencyGroupRPEnabled(String cgName,
            List<VPlexClusterInfo> clusterInfoList, boolean isRPEnabled) throws VPlexApiException {

        // Find the consistency group
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName,
                clusterInfoList, true);

        // Build the request path.
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(VPlexApiConstants.VPLEX_PATH);
        pathBuilder.append(cgInfo.getPath());
        pathBuilder.append("?");
        pathBuilder.append(VPlexApiConstants.ATTRIBUTE_CG_RP_ENABLED);
        pathBuilder.append("=");
        pathBuilder.append(isRPEnabled);

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(pathBuilder.toString()));
        s_logger.info("Set RP enabled  URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.put(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Set RP enabled response is {}", responseStr);
        int status = response.getStatus();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            if (status == VPlexApiConstants.ASYNC_STATUS) {
                s_logger.info("Set RP enabled is completing asynchronously");
                _vplexApiClient.waitForCompletion(response);
                response.close();
            } else {
                response.close();
                String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                throw VPlexApiException.exceptions.setRPEnabledFailureStatus(
                        cgInfo.getName(), String.valueOf(response.getStatus()), cause);
            }
        }
    }

    /**
     * Sets the detach rule for the passed consistency group to
     * "no-automatic-winner".
     * 
     * @param cgInfo A reference to the consistency group info.
     * @param clusterInfo The info for the cluster to be the winner.
     * 
     * @throws VPlexApiException When an error occurs setting the detach rule to
     *             winner for the consistency group.
     */
    void setDetachRuleNoAutomaticWinner(VPlexConsistencyGroupInfo cgInfo)
            throws VPlexApiException {
        ClientResponse response = null;
        try {
            // Build the request path.
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_CG_DETACH_RULE_NO_AUTO_WINNER);
            s_logger.info("Set CG detach rule no automatic winner URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_G, cgInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Set CG detach rule no automatic winner is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Set CG detach rule no automatic winner response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Set CG detach rule no automatic winner is completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.setDetachRuleNoAutoWinnerFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedSettingDetachRuleNoAutoWinner(
                    cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Sets the auto-resume-at-loser flag under the advanced context
     * of the requested Consistency Group.
     * 
     * NOTE: as of VPLEX API version 5.5, this call is likely no
     * longer necessary because the default value on new CG creation
     * will be set to true by the VPLEX. Prior to 5.5, it is false
     * by default. See also CTRL-10193.
     * 
     * @param cgInfo The consistency group to update.
     * @param autoResume the value to set auto-resume-at-loser to
     * 
     * @throws VPlexApiException When an error occurs updating
     *             the consistency group visibility.
     */
    void setAutoResumeAtLoser(VPlexConsistencyGroupInfo cgInfo, boolean autoResume) throws VPlexApiException {

        // Build the request path.
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(VPlexApiConstants.VPLEX_PATH);
        pathBuilder.append(cgInfo.getPath());
        pathBuilder.append(VPlexApiConstants.URI_CGS_ADVANCED);
        pathBuilder.append(VPlexApiConstants.QUESTION_MARK);
        pathBuilder.append(VPlexApiConstants.ATTRIBUTE_CG_AUTO_RESUME);
        pathBuilder.append(VPlexApiConstants.EQUALS);
        pathBuilder.append(autoResume);

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(pathBuilder.toString()));
        s_logger.info("Set auto-resume-at-loser URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.put(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Set auto-resume-at-loser response is {}", responseStr);
        int status = response.getStatus();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            if (status == VPlexApiConstants.ASYNC_STATUS) {
                s_logger.info("Set auto-resume-at-loser is completing asynchronously");
                _vplexApiClient.waitForCompletion(response);
                response.close();
            } else {
                response.close();
                String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                throw VPlexApiException.exceptions.setCGAutoResumeFailureStatus(
                        cgInfo.getName(), String.valueOf(response.getStatus()), cause);
            }
        }
    }

    /**
     * Adds the volumes with the passed names to the consistency group with the
     * passed name.
     * 
     * @param cgName The name of the consistency group to which the volumes are
     *            added.
     * @param virtualVolumeNames The names of the virtual volumes to be added to
     *            the consistency group.
     * 
     * @throws VPlexApiException When an error occurs adding the volumes to the
     *             consistency group.
     */
    void addVolumesToConsistencyGroup(String cgName,
            List<String> virtualVolumeNames) throws VPlexApiException {
        s_logger.info("Request to add volumes {} to a consistency group {}",
                virtualVolumeNames, cgName);

        // Find the virtual volumes with the passed names.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        Map<String, List<VPlexVirtualVolumeInfo>> clusterToVirtualVolumes = new HashMap<String, List<VPlexVirtualVolumeInfo>>();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            List<VPlexVirtualVolumeInfo> clusterVolumeInfoList = discoveryMgr.getVirtualVolumesForCluster(clusterInfo.getName());
            clusterToVirtualVolumes.put(clusterInfo.getName(), clusterVolumeInfoList);
        }

        List<VPlexVirtualVolumeInfo> virtualVolumeInfoList = new ArrayList<VPlexVirtualVolumeInfo>();
        List<String> notFoundVirtualVolumeNames = new ArrayList<String>();
        for (String virtualVolumeName : virtualVolumeNames) {
            s_logger.info("Find virtual volume {}", virtualVolumeName);
            VPlexVirtualVolumeInfo virtualVolumeInfo = null;
            for (String clusterId : clusterToVirtualVolumes.keySet()) {
                List<VPlexVirtualVolumeInfo> clusterVolumeInfoList = clusterToVirtualVolumes.get(clusterId);
                for (VPlexVirtualVolumeInfo volumeInfo : clusterVolumeInfoList) {
                    s_logger.info("Virtual volume Info: {}", volumeInfo.toString());
                    if (volumeInfo.getName().equals(virtualVolumeName)) {
                        s_logger.info("Found virtual volume {}", volumeInfo.getName());
                        virtualVolumeInfo = volumeInfo;
                        break;
                    }
                }

                if (virtualVolumeInfo != null) {
                    break;
                }
            }

            if (virtualVolumeInfo == null) {
                notFoundVirtualVolumeNames.add(virtualVolumeName);
            } else {
                virtualVolumeInfoList.add(virtualVolumeInfo);
            }
        }

        if (!notFoundVirtualVolumeNames.isEmpty()) {
            throw VPlexApiException.exceptions.cantFindRequestedVolume(notFoundVirtualVolumeNames.toString());
        }
        // Find the consistency group
        VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName,
                clusterInfoList, false);

        // Add the virtual volumes to the consistency group.
        ClientResponse response = null;
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_ADD_VOLUMES_TO_CG);
            s_logger.info("Add volumes to consistency group URI is {}",
                    requestURI.toString());
            StringBuilder argBuilder = new StringBuilder();
            for (VPlexVirtualVolumeInfo virtualVolumeInfo : virtualVolumeInfoList) {
                if (argBuilder.length() != 0) {
                    argBuilder.append(",");
                }
                argBuilder.append(virtualVolumeInfo.getPath());
            }
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_V, argBuilder.toString());
            argsMap.put(VPlexApiConstants.ARG_DASH_G, cgInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Add volumes to consistency group POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Add volumes to consistency group response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    // We've seen cases where we send the request to add volumes to the CG over to
                    // VPLEX and it completes the operation successfully but never returns with
                    // a response. Specifically, we see this if we strart 2 concurrent Metropoint
                    // volume create orders at the same time.
                    // Ideally, we want to work with the VPLEX team to debug and figure out why
                    // this is happening and fix it, but we need to get a fix into a patch in the
                    // short term. The short term solution is to query the VPLEX for the existence
                    // of the volumes in the CG after VPLEX returns with the async status response.
                    // We'll check again after waiting for the asynchronous request to time out
                    if (!areVolumesInCG(cgName, clusterInfoList, virtualVolumeNames)) {
                        s_logger.info("Add volumes to consistency group completing asynchronously");
                        try {
                            _vplexApiClient.waitForCompletion(response);
                        } catch (VPlexApiException ex) {
                            // check for the volumes in the CG once more before failing
                            if (!areVolumesInCG(cgName, clusterInfoList, virtualVolumeNames)) {
                                throw ex;
                            }
                        }
                    }
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.addVolumesToCGFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Successfully added volumes to consistency group");
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedAddingVolumesToCG(cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * checks for the presence of a list of virtual volumes in a CG
     * 
     * @param cgName
     * @param clusterInfoList
     * @param virtualVolumeNames
     * @return true if all volumes are in the CG
     */
    private boolean areVolumesInCG(String cgName, List<VPlexClusterInfo> clusterInfoList, List<String> virtualVolumeNames) {
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName, clusterInfoList, true);
        for (String vvolName : virtualVolumeNames) {
            if (!cgInfo.getVirtualVolumes().contains(vvolName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deletes the consistency group with the passed name.
     * 
     * @param cgName The name of the consistency group to be deleted.
     * 
     * @throws VPlexApiException When an error occurs deleting the consistency group.
     */
    void deleteConsistencyGroup(String cgName) throws VPlexApiException {
        s_logger.info("Request to delete consistency group {}", cgName);

        // Find the consistency group.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName,
                clusterInfoList, false);
        // COP-17138 If the consistency group still has virtual volumes in the VPLEX, don't delete it in the VPLEX
        discoveryMgr.updateConsistencyGroupInfo(cgInfo);
        if (cgInfo.getVirtualVolumes().isEmpty()) {
            deleteConsistencyGroup(cgInfo);
        } else {
            s_logger.info("The consistency group {} still has virtual volumes in VPLEX, not deleting it in the VPLEX", cgName);
        }
    }

    /**
     * Deletes the passed consistency group.
     * 
     * @param cgInfo A reference to the consistency group info.
     * 
     * @throws VPlexApiException When an error occurs deleting the consistency
     *             group.
     */
    private void deleteConsistencyGroup(VPlexConsistencyGroupInfo cgInfo)
            throws VPlexApiException {
        // Delete the consistency group.
        ClientResponse response = null;
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_DELETE_CG);
            s_logger.info("Delete consistency group URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_G, cgInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Delete consistency group POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Delete consistency group response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Consistency group deletion completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.deleteCGFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Successfully deleted consistency group");
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedDeleteCG(cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Removes the volumes with the passed names from the consistency group with
     * the passed name. If the removal of the volumes results in an empty group,
     * delete the consistency group if the passed flag so indicates.
     * 
     * @param virtualVolumeNames The names of the virtual volumes to be removed
     *            from the consistency group.
     * @param cgName The name of the consistency group from which the volume is
     *            removed.
     * @param deleteCGWhenEmpty true to delete the consistency group if the
     *            group is empty after removing the volumes, false otherwise.
     * 
     * @return true if the consistency group was deleted, false otherwise.
     * 
     * @throws VPlexApiException When an error occurs removing the volumes from
     *             the consistency group.
     */
    boolean removeVolumesFromConsistencyGroup(List<String> virtualVolumeNames,
            String cgName, boolean deleteCGWhenEmpty) throws VPlexApiException {
        s_logger.info("Request to remove volumes {} from consistency group {}",
                virtualVolumeNames.toString(), cgName);

        boolean cgDeleted = false;

        // Find the virtual volumes with the passed names.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        List<VPlexVirtualVolumeInfo> virtualVolumeInfoList = new ArrayList<VPlexVirtualVolumeInfo>();
        for (String virtualVolumeName : virtualVolumeNames) {
            VPlexVirtualVolumeInfo virtualVolumeInfo = null;
            for (VPlexClusterInfo clusterInfo : clusterInfoList) {
                virtualVolumeInfo = discoveryMgr.findVirtualVolume(clusterInfo.getName(),
                        virtualVolumeName, false);
                if (virtualVolumeInfo != null) {
                    break;
                }
            }
            if (virtualVolumeInfo == null) {
                throw VPlexApiException.exceptions.cantFindRequestedVolume(virtualVolumeName);
            }
            virtualVolumeInfoList.add(virtualVolumeInfo);
        }

        // Find the consistency group.
        VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName,
                clusterInfoList, false);

        // Remove the virtual volume from the consistency group.
        ClientResponse response = null;
        try {
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_REMOVE_VOLUMES_FROM_CG);
            s_logger.info("Remove volumes from consistency group URI is {}",
                    requestURI.toString());
            StringBuilder argBuilder = new StringBuilder();
            for (VPlexVirtualVolumeInfo virtualVolumeInfo : virtualVolumeInfoList) {
                if (argBuilder.length() != 0) {
                    argBuilder.append(",");
                }
                argBuilder.append(virtualVolumeInfo.getPath());
            }
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_V, argBuilder.toString());
            argsMap.put(VPlexApiConstants.ARG_DASH_G, cgInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Remove volumes from consistency group POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Remove volumes from consistency group response is {}",
                    responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger
                            .info("Remove volumes from consistency group completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.removeVolumesFromCGFailureStatus(
                            cgInfo.getName(), String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Successfully removed volumes from consistency group");

            // If the flag so indicates
            if (deleteCGWhenEmpty) {
                discoveryMgr.updateConsistencyGroupInfo(cgInfo);
                if (cgInfo.getVirtualVolumes().isEmpty()) {
                    s_logger.info("Deleting empty consistency group {}", cgName);
                    try {
                        deleteConsistencyGroup(cgInfo);
                        cgDeleted = true;
                    } catch (Exception e) {
                        s_logger
                                .error("Exception deleting consistency group {}:{}", cgName, e.getMessage());
                    }
                }
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedRemovingVolumesFromCG(cgInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return cgDeleted;
    }
    
    /**
     * Sets the read-only flag on the consistency group to the clusters in the
     * passed list. Note: This flag only supported in VPlex 5.5 and beyond, and it
     * requires a patch (not yet released as of 2016May26) to work on Consistency Groups
     * with distributed virtual volumes. It throws vplexFirmwareUpdateNeeded
     * if the firmware does not fully support thre read-only operation.
     * 
     * @param cgName The consistency group to update.
     * @param clusterInfoList The list of clusters
     * 
     * @throws VPlexApiException When an error occurs setting the consistency
     *             group visibility.
     */
    void setConsistencyGroupReadOnly(String cgName,
            List<VPlexClusterInfo> clusterInfoList, boolean isReadOnly) throws VPlexApiException {

        // Find the consistency group
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexConsistencyGroupInfo cgInfo = discoveryMgr.findConsistencyGroup(cgName,
                clusterInfoList, true);

        // Build the request path.
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(VPlexApiConstants.VPLEX_PATH);
        pathBuilder.append(cgInfo.getPath());
        pathBuilder.append("?");
        pathBuilder.append(VPlexApiConstants.ATTRIBUTE_CG_READ_ONLY);
        pathBuilder.append("=");
        pathBuilder.append(isReadOnly);

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                URI.create(pathBuilder.toString()));
        s_logger.info("Set read-only in CG  URI is {}", requestURI.toString());
        ClientResponse response = _vplexApiClient.put(requestURI);
        String responseStr = response.getEntity(String.class);
        s_logger.info("Set read-only response is {}", responseStr);
        int status = response.getStatus();
        if (status != VPlexApiConstants.SUCCESS_STATUS) {
            if (status == VPlexApiConstants.ASYNC_STATUS) {
                s_logger.info("Set read-only in CG is completing asynchronously");
                _vplexApiClient.waitForCompletion(response);
                response.close();
            } else {
                response.close();
                if (responseStr.contains(VPlexApiConstants.CG_READ_ONLY_INVALID_ATTRIBUTE) 
                        || responseStr.contains(VPlexApiConstants.CG_CANNOT_MAKE_READ_ONLY)) {
                    throw VPlexApiException.exceptions
                        .vplexFirmwareUpdateNeeded(VPlexApiConstants.CG_READ_ONLY_ATTRIBUTE_NOT_SUPPORTED);
                }
                String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                throw VPlexApiException.exceptions.setConsistencyGroupReadOnlyFailureStatus(
                        cgInfo.getName(), String.valueOf(response.getStatus()), cause);
            }
        }
    }

}
