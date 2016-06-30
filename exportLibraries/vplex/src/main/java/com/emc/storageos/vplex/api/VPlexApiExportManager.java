/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vplex.api.VPlexInitiatorInfo.Initiator_Type;
import com.emc.storageos.vplex.api.clientdata.PortInfo;
import com.sun.jersey.api.client.ClientResponse;

/**
 * VPlexApiExportManager provides methods managing export and unexport operations.
 */
public class VPlexApiExportManager {

    private static final String RECOVERPOINT_INITIATOR_PREFIX = "500124";

    // Logger reference.
    private static Logger s_logger = LoggerFactory.getLogger(VPlexApiExportManager.class);

    // A reference to the API client.
    private VPlexApiClient _vplexApiClient;

    /**
     * Package protected constructor.
     * 
     * @param client A reference to the API client.
     */
    VPlexApiExportManager(VPlexApiClient client) {
        _vplexApiClient = client;
    }

    /**
     * Creates a VPlex storage view so that the passed initiators have access to
     * the passed virtual volumes, via the passed target ports (i.e., the VPlex
     * front-end ports). Note that target ports are required to create a storage
     * view. Initiator ports and virtual volumes are optional and can be added
     * separately.
     * 
     * @param viewName A unique name for the storage view.
     * @param targetPortInfo The info for the target ports.
     * @param initiatorPortInfo The info for the initiator ports.
     * @param virtualVolumeMap Map of virtual volume names to LUN ID.
     * 
     * @return A reference to a VPlexStorageViewInfo specifying the storage view
     *         information.
     * 
     * @throws VPlexApiException When an error occurs creating the storage view.
     */
    VPlexStorageViewInfo createStorageView(String viewName,
            List<PortInfo> targetPortInfo, List<PortInfo> initiatorPortInfo,
            Map<String, Integer> virtualVolumeMap) throws VPlexApiException {

        s_logger.info("Request to create storage view with name {}", viewName);

        // Find the target VPlex front end ports.
        List<VPlexTargetInfo> targetInfoList = new ArrayList<VPlexTargetInfo>();
        VPlexClusterInfo clusterInfo = findTargets(targetPortInfo, targetInfoList, true);
        if (targetInfoList.size() != targetPortInfo.size()) {
            throw VPlexApiException.exceptions.failedToFindAllRequestedTargets();
        }
        s_logger.info("Found targets ports for storage view");

        // Create storage view with targets.
        createStorageView(viewName, clusterInfo, targetInfoList);
        s_logger.info("Storage view {} created", viewName);

        // Now we need to find the storage view we just created.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageViewOnCluster(
                viewName, clusterInfo.getName(), false, true);
        if (storageViewInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindStorageView(viewName);
        }
        s_logger.info("Found storage view");

        // Find, register, and add the initiators when specified.
        if ((initiatorPortInfo != null) && !initiatorPortInfo.isEmpty()) {
            s_logger.info("Adding initiators to new storage view");
            List<VPlexInitiatorInfo> initiatorInfoList = findInitiators(clusterInfo,
                    initiatorPortInfo);
            if (initiatorInfoList.size() != initiatorPortInfo.size()) {
                s_logger.info("Could not find all of the requested initiators on VPlex.");
                initiatorInfoList = buildInitiatorInfoList(initiatorInfoList, initiatorPortInfo, clusterInfo);
            }

            // Register the initiators that are not registered.
            registerInitiators(clusterInfo, initiatorInfoList);
            s_logger.info("Registered initiators");

            // Add the initiators to storage view.
            addStorageViewInitiators(storageViewInfo, initiatorInfoList);
            s_logger.info("Initiators added to new storage view");
        }

        // Finally, add the virtual volumes to the storage view when specified.
        if ((virtualVolumeMap != null) && (virtualVolumeMap.size() != 0)) {
            s_logger.info("Adding virtual volumes to new storage view");
            addStorageViewVirtualVolumes(storageViewInfo, virtualVolumeMap);
            s_logger.info("Virtual volumes added to new storage view");
        }

        s_logger.info("Storage view {} creation was successful", viewName);

        return storageViewInfo;
    }

    /**
     * Delete the storage view with the passed name.
     * 
     * @param viewName The name of the storage view to be deleted.
     * @param viewFound An out parameter indicating whether or
     *            not the storage view was actually found on
     *            the VPLEX device during this process.
     * 
     * @throws VPlexApiException When an error occurs deleting the storage view.
     */
    void deleteStorageView(String viewName, Boolean[] viewFound) throws VPlexApiException {

        s_logger.info("Request to delete storage view {}", viewName);

        // Find the storage view with the passed name.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = null;
        try {
            storageViewInfo = discoveryMgr.findStorageView(viewName);
        } catch (Exception e) {
            s_logger.error("Exception trying to find VPLEX storage view.", e);
            viewFound[0] = false;
            throw e;
        }

        if (storageViewInfo == null) {
            s_logger.warn("Storage view {} not found. Nothing to delete.", viewName);
            viewFound[0] = false;
            return;
        } else {
            s_logger.info("Storage view {} was found on the VPLEX device.", viewName);
            viewFound[0] = true;
        }

        // Now delete it.
        ClientResponse response = null;
        try {
            // Build the destroy storage view request and make the request.
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_DESTROY_STORAGE_VIEW);
            s_logger.info("Delete storage view URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_V, viewName);
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Delete storage view POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Delete storage view response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Storage view deletion completing asyncronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions
                            .deleteStorageViewFailureStatus(viewName,
                                    String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Deleted storage view {}", viewName);
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedDeleteStorageView(viewName, e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Adds the initiators identified by the passed port information to the
     * storage view with the passed name.
     * 
     * @param viewName The name of the storage view.
     * @param initiatorPortInfo The port information for the initiators to be
     *            added.
     * 
     * @throws VPlexApiException When an error occurs adding the initiators.
     */
    void addInitiatorsToStorageView(String viewName,
            List<PortInfo> initiatorPortInfo) throws VPlexApiException {

        // In case of a VPlex cross connect initiators will be in both the clusters.
        // So first find the storage view in both the VPlex clusters and then find
        // Initiators in a specific cluster.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageView(viewName);
        if (storageViewInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindStorageView(viewName);
        }

        // Find the initiators in a cluster where storage view is found.
        List<VPlexInitiatorInfo> initiatorInfoList = findInitiatorsOnCluster(storageViewInfo.getClusterId(),
                initiatorPortInfo, null);

        VPlexClusterInfo clusterInfo = discoveryMgr.findClusterInfo(storageViewInfo.getClusterId());

        if (clusterInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindCluster(storageViewInfo.getClusterId());
        }

        if (initiatorInfoList.size() != initiatorPortInfo.size()) {
            s_logger.info("Could not find all of the requested initiators on VPLex.");
            initiatorInfoList = buildInitiatorInfoList(initiatorInfoList, initiatorPortInfo, clusterInfo);
        }

        // Register the initiators that are not registered.
        registerInitiators(clusterInfo, initiatorInfoList);

        // Add the initiators to storage view.
        addStorageViewInitiators(storageViewInfo, initiatorInfoList);
    }

    /**
     * Adds additional targets (Vplex front end ports) to an existing Storage View.
     * 
     * @param viewName -- The name of the existing Storage View.
     * @param targetPortInfo -- The port information for the target ports to be
     *            added to the view.
     * @throws VPlexApiException When an error occurs adding the targets.
     */
    void addTargetsToStorageView(String viewName,
            List<PortInfo> targetPortInfo) throws VPlexApiException {

        List<VPlexTargetInfo> targetInfoList = new ArrayList<VPlexTargetInfo>();
        VPlexClusterInfo clusterInfo = findTargets(targetPortInfo, targetInfoList, true);
        if (targetInfoList.size() != targetPortInfo.size()) {
            throw VPlexApiException.exceptions.failedToFindAllRequestedTargets();
        }
        s_logger.info("Found targets ports for storage view");

        // Find the storage view.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageViewOnCluster(
                viewName, clusterInfo.getName(), false);
        if (storageViewInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindStorageView(viewName);
        }
        modifyStorageViewTargets(storageViewInfo, targetInfoList, false);
    }

    /**
     * Removes targets (Vplex front end ports) from an existing Storage View.
     * 
     * @param viewName -- The name of the existing Storage View.
     * @param targetPortInfo -- The port information for the target ports to be
     *            added to the view.
     * @throws VPlexApiException When an error occurs adding the targets.
     */
    void removeTargetsFromStorageView(String viewName,
            List<PortInfo> targetPortInfo) throws VPlexApiException {

        List<VPlexTargetInfo> targetInfoList = new ArrayList<VPlexTargetInfo>();
        VPlexClusterInfo clusterInfo = findTargets(targetPortInfo, targetInfoList, true);
        if (targetInfoList.size() != targetPortInfo.size()) {
            throw VPlexApiException.exceptions.failedToFindAllRequestedTargets();
        }
        s_logger.info("Found targets ports for storage view");

        // Find the storage view.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageViewOnCluster(
                viewName, clusterInfo.getName(), false);
        if (storageViewInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindStorageView(viewName);
        }
        modifyStorageViewTargets(storageViewInfo, targetInfoList, true);
    }

    /**
     * Removes the initiators identified by the passed port information from the
     * storage view with the passed name.
     * 
     * @param viewName The name of the storage view.
     * @param initiatorPortInfo The port information for the initiators to be
     *            removed.
     * 
     * @throws VPlexApiException When an error occurs removing the initiators.
     */
    void removeInitiatorsFromStorageView(String viewName,
            List<PortInfo> initiatorPortInfo) throws VPlexApiException {

        // In case of a VPlex cross connect initiators will be in both the clusters.
        // So first find the storage view in both the VPlex clusters and then find
        // Initiators in a specific cluster.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageView(viewName);
        if (storageViewInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindStorageView(viewName);
        }

        // Find the initiators in a cluster where storage view is found.
        List<VPlexInitiatorInfo> initiatorInfoList = findInitiatorsOnCluster(storageViewInfo.getClusterId(),
                initiatorPortInfo, null);
        if (initiatorInfoList.size() != initiatorPortInfo.size()) {
            StringBuffer notFoundInitiators = new StringBuffer();
            for (PortInfo portInfo : initiatorPortInfo) {
                if (notFoundInitiators.length() == 0) {
                    notFoundInitiators.append(portInfo.getPortWWN());
                } else {
                    notFoundInitiators.append(" ,").append(portInfo.getPortWWN());
                }
            }
            throw VPlexApiException.exceptions.couldNotFindInitiators(notFoundInitiators.toString());
        }

        // Remove the initiators from storage view.
        removeStorageViewInitiators(storageViewInfo, initiatorInfoList);
    }

    /**
     * Adds the virtual volumes with the passed names to the storage view with
     * the passed name.
     * 
     * @param virtualVolumeMap Map of virtual volume names to LUN ID.
     * 
     * @return A reference to a VPlexStorageViewInfo specifying the storage view
     *         information.
     * 
     * @throws VPlexApiException When an error occurs adding the virtual
     *             volumes.
     */
    VPlexStorageViewInfo addVirtualVolumesToStorageView(String viewName,
            Map<String, Integer> virtualVolumeMap) throws VPlexApiException {

        // Find the virtual volume with the passed name.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageView(viewName);
        if (storageViewInfo == null) {
            throw VPlexApiException.exceptions.couldNotFindStorageView(viewName);
        }

        // Add the virtual volumes to the storage view.
        addStorageViewVirtualVolumes(storageViewInfo, virtualVolumeMap);

        return storageViewInfo;
    }

    /**
     * Removes the virtual volumes with the passed names from the storage view
     * with the passed name.
     * 
     * @param virtualVolumeNames The names of the virtual volumes to be removed.
     * 
     * @throws VPlexApiException When an error occurs removing the virtual
     *             volumes.
     */
    void removeVirtualVolumesFromStorageView(String viewName,
            List<String> virtualVolumeNames) throws VPlexApiException {

        // Find the virtual volume with the passed name.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        VPlexStorageViewInfo storageViewInfo = discoveryMgr.findStorageView(viewName, true);
        if (storageViewInfo == null) {
            // if the storage view doesn't exist, there can't be any volumes in it
            // not an error; just return
            return;
        }

        // if the storage view is empty, return with no error; nothing to remove
        if (storageViewInfo.getVirtualVolumes() == null || storageViewInfo.getVirtualVolumes().isEmpty()) {
            return;
        }

        List<String> vvolsInSV = new ArrayList<String>();
        for (String vplexVirtVol : storageViewInfo.getVirtualVolumes()) {
            s_logger.info("virtual volume from vplex: ", vplexVirtVol);
            StringTokenizer tokenizer = new StringTokenizer(vplexVirtVol, ",");
            // the virtual volume name is the second token
            if (tokenizer.countTokens() >= 2) {
                tokenizer.nextToken();
                String vplexVirtVolName = tokenizer.nextToken();
                vvolsInSV.add(vplexVirtVolName);
            } else {
                s_logger.warn("unexpected format for virtual volume " + vplexVirtVol +
                        "; expecting a comma separated string with the volume name in the second token");
            }
        }

        // exclude any volumes that aren't in the storage view
        List<String> volsToRemove = new ArrayList<String>();
        for (String volToRemove : virtualVolumeNames) {
            if (vvolsInSV.contains(volToRemove)) {
                volsToRemove.add(volToRemove);
            }
        }

        if (!volsToRemove.isEmpty()) {
            // Remove the virtual volumes from the storage view.
            removeStorageViewVirtualVolumes(storageViewInfo, volsToRemove);
        }
    }

    /**
     * Finds the target FE ports corresponding to the ports identified by the
     * passed port information.
     * 
     * @param targetPortInfo The target port information.
     * @param targetInfoList Out param containing the target information.
     * @param allTargetsOnSameCluster Whether or not all targets are on the same
     *            cluster.
     * 
     * @return The cluster on which the targets were found.
     * 
     * @throws VPlexApiException When an error occurs attempting to find the
     *             targets corresponding to the passed port information.
     */
    private VPlexClusterInfo findTargets(List<PortInfo> targetPortInfo,
            List<VPlexTargetInfo> targetInfoList, boolean allTargetsOnSameCluster)
            throws VPlexApiException {

        VPlexClusterInfo targetClusterInfo = null;
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {

            List<VPlexTargetInfo> clusterTargetInfoList = discoveryMgr
                    .getTargetInfoForCluster(clusterInfo.getName());

            for (PortInfo portInfo : targetPortInfo) {
                String portWWN = portInfo.getPortWWN();
                for (VPlexTargetInfo clusterTargetInfo : clusterTargetInfoList) {
                    // TBD: Check node WWNs as well? The are optional.
                    if (portWWN.equals(clusterTargetInfo.getPortWwn())) {
                        targetInfoList.add(clusterTargetInfo);
                        targetClusterInfo = clusterInfo;
                        break;
                    }
                }
            }

            // If all targets are on the same cluster and
            // if we found one, we are done.
            if ((allTargetsOnSameCluster) && (!targetInfoList.isEmpty())) {
                break;
            }
        }

        return targetClusterInfo;
    }

    /**
     * Finds the initiator ports on the VPlex corresponding to the ports
     * specified in the passed port information. Note that the functions
     * presumes that all initiators to be found are on the same VPlex cluster.
     * This function will execute an initiator discovery if some of the
     * initiators are not found. After the discovery it will then try to find
     * the initiators that could not be found initially.
     * 
     * @param clusterInfo The cluster of which to find the initiators.
     * @param initiatorPortInfo The initiator port information.
     * 
     * @return A list of VPlexInitiatorInfo instances specifying the information
     *         for the found initiators.
     * 
     * @throws VPlexApiException When an error occurs finding the initiators.
     */
    private List<VPlexInitiatorInfo> findInitiators(VPlexClusterInfo clusterInfo,
            List<PortInfo> initiatorPortInfo) throws VPlexApiException {

        // A list of the ports for which we could not find initiators.
        // Initially all of the initiators. Found ports are removed from
        // the list.
        List<PortInfo> unfoundInitiatorList = new ArrayList<PortInfo>();
        unfoundInitiatorList.addAll(initiatorPortInfo);

        // Find the initiators for the passed ports on the passed cluster.
        String clusterName = clusterInfo.getName();
        List<VPlexInitiatorInfo> initiatorInfoList = findInitiatorsOnCluster(clusterName,
                initiatorPortInfo, unfoundInitiatorList);

        // If the skip discovery flag is true, we return whether or not
        // all initiators were found. For example, we may be finding
        // initiators to remove from a storage view, in which case the
        // initiators should already be discovered. If we can't find them
        // then that's a problem. However, if we are adding initiators
        // to a storage view, these could be new initiators that are
        // not yet discovered, in which case we can execute a discovery
        // and try to find them again.
        if (initiatorInfoList.size() == initiatorPortInfo.size()) {
            return initiatorInfoList;
        }

        // We found some initiators on the clusters, but not
        // all of them. Do an initiator discovery on that cluster
        // and try to find the remaining.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        discoveryMgr.discoverInitiatorsOnCluster(clusterInfo);
        initiatorInfoList.addAll(findInitiatorsOnCluster(clusterName,
                unfoundInitiatorList, null));

        return initiatorInfoList;
    }

    /**
     * Finds the initiator ports on the VPlex corresponding to the ports
     * specified in the passed port information. Note that the functions
     * presumes that all initiators to be found are on the same VPlex cluster.
     * 
     * @param initiatorPortInfo The initiator port information.
     * @param initiatorInfoList Out param contains the found initiators.
     * 
     * @return The cluster on which the initiators were found.
     * 
     * @throws VPlexApiException When an error occurs finding the initiators.
     */
    private VPlexClusterInfo findInitiators(List<PortInfo> initiatorPortInfo,
            List<VPlexInitiatorInfo> initiatorInfoList) throws VPlexApiException {

        // Loop over the clusters looking for the initiators corresponding
        // to the passed port information.
        VPlexClusterInfo initiatorClusterInfo = null;
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexClusterInfo> clusterInfoList = discoveryMgr.getClusterInfoLite();
        for (VPlexClusterInfo clusterInfo : clusterInfoList) {
            initiatorInfoList.addAll(findInitiatorsOnCluster(clusterInfo.getName(),
                    initiatorPortInfo, null));

            // We presume that all initiators are on a single cluster,
            // so exit the loop if we find at least one.
            if (!initiatorInfoList.isEmpty()) {
                initiatorClusterInfo = clusterInfo;
                break;
            }
        }

        return initiatorClusterInfo;
    }

    /**
     * Tries to find initiators on the the passed cluster corresponding to the
     * passed port information.
     * 
     * @param clusterName The name of the cluster.
     * @param initiatorPortInfo The port information.
     * @param unfoundInitiatorList Out param captures ports for which initiators
     *            were not found. Can be null.
     * 
     * @return A list of VPlexInitiatorInfo representing the found initiators.
     * 
     * @throws VPlexApiException When an error occurs finding initiators on the
     *             cluster.
     */
    private List<VPlexInitiatorInfo> findInitiatorsOnCluster(String clusterName,
            List<PortInfo> initiatorPortInfo, List<PortInfo> unfoundInitiatorPortInfo)
            throws VPlexApiException {

        List<VPlexInitiatorInfo> initiatorInfoList = new ArrayList<VPlexInitiatorInfo>();

        // Get the initiators on the passed cluster.
        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        List<VPlexInitiatorInfo> clusterInitiatorInfoList = discoveryMgr
                .getInitiatorInfoForCluster(clusterName);

        // Loop over the port information trying to match the port with
        // an initiator found on the cluster.
        for (PortInfo portInfo : initiatorPortInfo) {
            String portWWN = portInfo.getPortWWN();
            for (VPlexInitiatorInfo clusterInitiatorInfo : clusterInitiatorInfoList) {
                // TBD: Check node WWNs as well? They are optional.
                if (portWWN.equals(clusterInitiatorInfo.getPortWwn())) {
                    initiatorInfoList.add(clusterInitiatorInfo);
                    if (unfoundInitiatorPortInfo != null) {
                        unfoundInitiatorPortInfo.remove(portInfo);
                    }

                    // Make sure a registration name is set. If the caller
                    // passed a specific port name, then it was or will be
                    // registered with that name. Otherwise, we give it a
                    // default name.
                    String portName = portInfo.getName();
                    if ((portName != null) && (portName.length() != 0)) {
                        clusterInitiatorInfo.setRegistrationName(portName);
                    } else {
                        clusterInitiatorInfo
                                .setRegistrationName(VPlexApiConstants.REGISTERED_INITIATOR_PREFIX
                                        + clusterInitiatorInfo.getPortWwnRaw());
                    }

                    // Also, if the caller passed an initiator type make sure
                    // it is set so that it can be passed when the initiator
                    // is registered.
                    Initiator_Type initiatorType = Initiator_Type.valueOfType(portInfo
                            .getType());
                    if (initiatorType == null) {
                        s_logger.info("Initiator port type {} not found, using default",
                                portInfo.getType());
                        initiatorType = Initiator_Type.DEFAULT;
                    }
                    clusterInitiatorInfo.setInitiatorType(initiatorType);

                    break;
                }
            }
        }

        return initiatorInfoList;
    }

    /**
     * Register the passed initiators if they have yet to be registered.
     * 
     * @param clusterInfo The cluster on which the initiators are registered.
     * @param initiatorInfoList The initiators to be registered.
     * 
     * @throws VPlexApiException When an error occurs registering an initiator.
     */
    private void registerInitiators(VPlexClusterInfo clusterInfo,
            List<VPlexInitiatorInfo> initiatorInfoList) throws VPlexApiException {

        for (VPlexInitiatorInfo initiatorInfo : initiatorInfoList) {
            // If the name of the initiator does not starts with
            // UNREGISTERED- then the initiator is already registered.
            if (!initiatorInfo.getName().startsWith(
                    VPlexApiConstants.UNREGISTERED_INITIATOR_PREFIX)) {
                continue;
            }

            String initiatorName = initiatorInfo.getName();
            s_logger.info("Registering initiator {}", initiatorName);

            ClientResponse response = null;
            try {
                // Build the value for the port argument for the request.
                StringBuilder argBuilder = new StringBuilder();
                argBuilder.append(initiatorInfo.getPortWwnRaw());
                String nodeWWN = initiatorInfo.getNodeWwnRaw();
                String portWWN = initiatorInfo.getPortWwnRaw();
                if ((nodeWWN != null) && (nodeWWN.length() != 0)) {
                    argBuilder.append(VPlexApiConstants.INITIATOR_REG_DELIM);
                    argBuilder.append(nodeWWN);
                }

                // Build the register initiator request and make the request.
                URI requestURI = _vplexApiClient.getBaseURI().resolve(
                        VPlexApiConstants.URI_REGISTER_INITIATOR);
                s_logger.info("Register initiator URI is {}", requestURI.toString());
                Map<String, String> argsMap = new HashMap<String, String>();
                argsMap.put(VPlexApiConstants.ARG_DASH_I, initiatorInfo.getRegistrationName());
                argsMap.put(VPlexApiConstants.ARG_DASH_P, argBuilder.toString());
                argsMap.put(VPlexApiConstants.ARG_DASH_C, clusterInfo.getPath());

                if (isRecoverPointInitiator(portWWN)) {
                    argsMap.put(VPlexApiConstants.ARG_DASH_T, "recoverpoint");
                }

                Initiator_Type initiatorType = initiatorInfo.getInitiatorType();
                if (initiatorType != Initiator_Type.DEFAULT) {
                    argsMap.put(VPlexApiConstants.ARG_DASH_T, initiatorType.getType());
                }

                JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
                s_logger.info("Register initiator POST data is {}",
                        postDataObject.toString());
                response = _vplexApiClient.post(requestURI,
                        postDataObject.toString());
                String responseStr = response.getEntity(String.class);
                s_logger.info("Register initiator response is {}", responseStr);
                if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                    if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                        s_logger.info("Initiator registration completing asynchronously");
                        _vplexApiClient.waitForCompletion(response);
                    } else {
                        String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                        throw VPlexApiException.exceptions
                                .registerInitiatorFailureStatus(initiatorName,
                                        String.valueOf(response.getStatus()), cause);
                    }
                }

                // Update the initiator info to reflect the registration name
                // for the initiator.
                initiatorInfo.updateOnRegistration();

                s_logger.info(String.format("Successfully registered initiator %s", initiatorInfo.getName()));
            } catch (VPlexApiException vae) {
                throw vae;
            } catch (Exception e) {
                throw VPlexApiException.exceptions.failedRegisterInitiator(initiatorName, e);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    /**
     * Determines if the given port WWN is a RecoverPoint initiator.
     * 
     * @param portWWN the port WWN
     * @return true if the WWN is a RecoverPoint initiator, false otherwise.
     */
    private boolean isRecoverPointInitiator(String portWWN) {
        // TODO: RP-TEAM - this is NOT how we should be determing if the initiator type is RP.
        // the error that i got when i tried to register an initiator said something like the portWWN contains EMC Recoverpoint Vendor ID.
        // I wasnt able to decipher any kind of info from that as the portWWN is nothing but a WWN.
        // What we can possibly do here ? - I think we can tinker with the VplexInitiatorInfo class and add "type" field in there to make
        // this more cleaner but i am really not sure
        // if that would give anything. From experience with the initiators registration on the VPLEX manually, i dont even think that there
        // is way to find out from the VPLEX UI other
        // than the fact that "we" know that any initiator that begins with the prefix below is an RP initiator. Something to investigate.

        return portWWN.contains(RECOVERPOINT_INITIATOR_PREFIX);
    }

    /**
     * Unregister the passed initiators identified by the passed port
     * information.
     * 
     * @param initiatorPortInfo The initiator information.
     * 
     * @throws VPlexApiException When an error occurs unregistering an
     *             initiator.
     */
    void unregisterInitiators(List<PortInfo> initiatorPortInfo) throws VPlexApiException {

        // Find the initiators.
        List<VPlexInitiatorInfo> initiatorInfoList = new ArrayList<VPlexInitiatorInfo>();
        findInitiators(initiatorPortInfo, initiatorInfoList);

        // Make sure we found them all.
        if (initiatorInfoList.size() != initiatorPortInfo.size()) {
            throw VPlexApiException.exceptions.couldNotFindAllInitiatorsToUnregister();
        }

        // Build up the argument specifying the paths of the initiators
        // to be unregistered.
        StringBuilder argBuilder = new StringBuilder();
        for (VPlexInitiatorInfo initiatorInfo : initiatorInfoList) {
            // If the name and registration name of the initiator are
            // not the same, the initiator is not registered.
            if (!initiatorInfo.getName().equals(initiatorInfo.getRegistrationName())) {
                continue;
            }

            s_logger.info("Unregister initiator {}", initiatorInfo.getName());

            if (argBuilder.length() != 0) {
                argBuilder.append(",");
            }
            argBuilder.append(initiatorInfo.getPath());
        }

        ClientResponse response = null;
        try {
            // Build the unregister initiator request and make the request.
            URI requestURI = _vplexApiClient.getBaseURI().resolve(
                    VPlexApiConstants.URI_UNREGISTER_INITIATORS);
            s_logger.info("Unregister initiators URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_I, argBuilder.toString());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Uregister initiators POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Unregister initiators response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Unregister initiators completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions.unregisterInitiatorsFailureStatus(
                            String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Initiators succesfully unregistered");
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedUnregisterInitiators(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Create a storage view with the passed name on the passed cluster
     * containing the passed targets.
     * 
     * @param viewName The name for the storage view.
     * @param clusterInfo The cluster on which to create the storage view.
     * @param targetInfoList The list of targets to be in the storage view.
     * 
     * @throws VPlexApiException When an error occurs creating the storage view.
     */
    private void createStorageView(String viewName, VPlexClusterInfo clusterInfo,
            List<VPlexTargetInfo> targetInfoList) throws VPlexApiException {

        boolean retryNeeded = false;
        int retryCount = 0;
        do {
            retryNeeded = false;
            ClientResponse response = null;
            try {
                // Create the value for the target ports argument for the request.
                StringBuilder targetPathBuilder = new StringBuilder();
                for (VPlexTargetInfo targetInfo : targetInfoList) {
                    if (targetPathBuilder.length() != 0) {
                        targetPathBuilder.append(",");
                    }
                    targetPathBuilder.append(targetInfo.getPath());
                }

                s_logger.info("Creating storage view {} with targets {}", viewName,
                        targetPathBuilder.toString());

                // Build the create storage view request and make the request.
                URI requestURI = _vplexApiClient.getBaseURI().resolve(
                        VPlexApiConstants.URI_CREATE_STORAGE_VIEW);
                s_logger.info("Create storage view URI is {}", requestURI.toString());
                Map<String, String> argsMap = new HashMap<String, String>();
                argsMap.put(VPlexApiConstants.ARG_DASH_N, viewName);
                argsMap.put(VPlexApiConstants.ARG_DASH_P, targetPathBuilder.toString());
                argsMap.put(VPlexApiConstants.ARG_DASH_C, clusterInfo.getPath());
                JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
                s_logger.info("Create storage view POST data is {}",
                        postDataObject.toString());
                response = _vplexApiClient.post(requestURI,
                        postDataObject.toString());
                String responseStr = response.getEntity(String.class);
                s_logger.info("Create storage view response is {}", responseStr);
                if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                    if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                        s_logger.info("Storage view creation completing asynchronously");
                        _vplexApiClient.waitForCompletion(response);
                    } else if (response.getStatus() == VPlexApiConstants.COULD_NOT_READ_STORAGE_VIEW_STATUS
                            && retryCount++ < VPlexApiConstants.STORAGE_VIEW_CREATE_MAX_RETRIES) {
                        s_logger.info("VPlex error {} will retry after a delay", response.getStatus());
                        VPlexApiUtils.pauseThread(VPlexApiConstants.STORAGE_VIEW_CREATE_RETRY_TIME_MS);
                        retryNeeded = true;
                    } else {
                        String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                        throw VPlexApiException.exceptions
                                .createStorageViewFailureStatus(viewName,
                                        String.valueOf(response.getStatus()), cause);
                    }
                }
                if (!retryNeeded) {
                    s_logger.info("Created storage view {}", viewName);
                }
            } catch (VPlexApiException vae) {
                throw vae;
            } catch (Exception e) {
                throw VPlexApiException.exceptions.failedCreateStorageView(viewName, e);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } while (retryNeeded);
    }

    /**
     * Adds the passed initiators to the storage view.
     * 
     * @param storageViewInfo The storage view.
     * @param initiatorInfoList The initiators to be added to the storage view.
     * 
     * @throws VPlexApiException When an error occurs adding the initiators to
     *             the storage view.
     */
    private void addStorageViewInitiators(VPlexStorageViewInfo storageViewInfo,
            List<VPlexInitiatorInfo> initiatorInfoList) throws VPlexApiException {

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_STORAGE_VIEW_ADD_INITIATORS);
        modifyStorageViewInitiators(storageViewInfo, initiatorInfoList, requestURI);
    }

    /**
     * Removes the passed initiators from the storage view.
     * 
     * @param storageViewInfo The storage view.
     * @param initiatorInfoList The initiators to be removed from the storage
     *            view.
     * 
     * @throws VPlexApiException When an error occurs removing the initiators
     *             from the storage view.
     */
    private void removeStorageViewInitiators(VPlexStorageViewInfo storageViewInfo,
            List<VPlexInitiatorInfo> initiatorInfoList) throws VPlexApiException {

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_STORAGE_VIEW_REMOVE_INITIATORS);
        modifyStorageViewInitiators(storageViewInfo, initiatorInfoList, requestURI);
    }

    /**
     * Adds or removes the passed initiators to/from the passed storage view
     * according to the passed request URI.
     * 
     * @param storageViewInfo The storage view.
     * @param initiatorInfoList The initiators to be added/removed to/from the
     *            storage view.
     * @param requestURI The URI for the modification request.
     * 
     * @throws VPlexApiException When an error occurs modifying the initiators
     *             for the storage view.
     */
    private void modifyStorageViewInitiators(VPlexStorageViewInfo storageViewInfo,
            List<VPlexInitiatorInfo> initiatorInfoList, URI requestURI)
            throws VPlexApiException {

        ClientResponse response = null;
        try {
            // Create the value for the initiators argument for the request.
            StringBuilder initiatorPathBuilder = new StringBuilder();
            for (VPlexInitiatorInfo initiatorInfo : initiatorInfoList) {
                if (initiatorPathBuilder.length() != 0) {
                    initiatorPathBuilder.append(",");
                }
                initiatorPathBuilder.append(initiatorInfo.getPath());
            }

            // Build the request post data and make the request.
            s_logger.info("Modify storage view initiators request URI is {}",
                    requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_I, initiatorPathBuilder.toString());
            argsMap.put(VPlexApiConstants.ARG_DASH_V, storageViewInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Storage view add initiator POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Storage view initiator response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Storage view initiator changes completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions
                            .modifyViewInitiatorsFailureStatus(storageViewInfo.getName(),
                                    String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Successfully updated initiators for storage view {}",
                    storageViewInfo.getName());
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedModifyViewInitiators(
                    storageViewInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Adds or removes targets (VPLEX Ports) to a StorageView.
     * 
     * @param storageViewInfo -- has the StorageView path
     * @param targetInfoList -- The list of targets to be added/removed
     * @param remove -- Boolean, if true, will remove, otherwise add the targets.
     */
    private void modifyStorageViewTargets(VPlexStorageViewInfo storageViewInfo,
            List<VPlexTargetInfo> targetInfoList, boolean remove) {
        ClientResponse response = null;
        try {
            URI requestURI = (remove ?
                    _vplexApiClient.getBaseURI().resolve(VPlexApiConstants.URI_STORAGE_VIEW_REMOVE_TARGETS) :
                    _vplexApiClient.getBaseURI().resolve(VPlexApiConstants.URI_STORAGE_VIEW_ADD_TARGETS));

            // Create the value for the target ports argument for the request.
            StringBuilder targetPathBuilder = new StringBuilder();
            for (VPlexTargetInfo targetInfo : targetInfoList) {
                if (targetPathBuilder.length() != 0) {
                    targetPathBuilder.append(",");
                }
                targetPathBuilder.append(targetInfo.getPath());
            }

            // Build the request post data and make the request.
            s_logger.info("modifyStorageViewTargets URI is {}", requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_V, storageViewInfo.getPath());
            argsMap.put(VPlexApiConstants.ARG_DASH_P, targetPathBuilder.toString());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, false);
            s_logger.info("Modify Storage View Targets POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Modify storage view response is {}", responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Modify Storage View Targets completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions
                            .modifyViewTargetsFailureStatus(storageViewInfo.getName(),
                                    String.valueOf(response.getStatus()), cause);
                }
            }

            if (remove) {
                s_logger.info("Removed targets from storage view {}", storageViewInfo.getName());
            } else {
                s_logger.info("Added targets to storage view {}", storageViewInfo.getName());
            }
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedModifyViewTargets(
                    storageViewInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Adds the virtual volumes with the passed names to the passed storage
     * view.
     * 
     * @param storageViewInfo The storage view.
     * @param virtualVolumeMap Map of virtual volume names to LUN ID.
     * 
     * @throws VPlexApiException When an errors occurs adding the virtual
     *             volumes to the storage view.
     */
    private void addStorageViewVirtualVolumes(VPlexStorageViewInfo storageViewInfo,
            Map<String, Integer> virtualVolumeMap) throws VPlexApiException {

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_STORAGE_VIEW_ADD_VOLUMES);

        // Create the value for the volumes argument for the request.
        StringBuilder volumeArgsBuilder = new StringBuilder();
        Iterator<Entry<String, Integer>> virtualVolumesIter = virtualVolumeMap.entrySet().iterator();
        while (virtualVolumesIter.hasNext()) {
            Entry<String, Integer> entry = virtualVolumesIter.next();
            String virtualVolumeName = entry.getKey();
            Integer lunId = entry.getValue();
            if (volumeArgsBuilder.length() != 0) {
                volumeArgsBuilder.append(",");
            }

            // If no LUN ID is assigned, we simply use the name, otherwise
            // we include the requested LUN ID in the argument.
            if (lunId.intValue() == VPlexApiConstants.LUN_UNASSIGNED) {
                volumeArgsBuilder.append(virtualVolumeName);
            } else {
                volumeArgsBuilder.append("(");
                volumeArgsBuilder.append(lunId);
                volumeArgsBuilder.append(",");
                volumeArgsBuilder.append(virtualVolumeName);
                volumeArgsBuilder.append(")");
            }
        }

        modifyStorageViewVirtualVolumes(storageViewInfo, volumeArgsBuilder.toString(),
                requestURI);

        //
        updateStorageViewInfo(storageViewInfo);
        Iterator<String> virtualVolumesNamesIter = virtualVolumeMap.keySet().iterator();
        while (virtualVolumesNamesIter.hasNext()) {
            String virtualVolumeName = virtualVolumesNamesIter.next();
            s_logger.info("WWN {} for Volume {}", storageViewInfo.getWWNForStorageViewVolume(virtualVolumeName), virtualVolumeName);
        }
    }

    /**
     * Updates a VPlexStorageViewInfo object with detailed attributes.
     * 
     * @param storageViewInfo
     */
    private void updateStorageViewInfo(VPlexStorageViewInfo storageViewInfo) {

        VPlexApiDiscoveryManager discoveryMgr = _vplexApiClient.getDiscoveryManager();
        discoveryMgr.updateStorageViewInfo(storageViewInfo);

    }

    /**
     * Removes the virtual volumes with the passed names from the passed storage
     * view.
     * 
     * @param storageViewInfo The storage view.
     * @param virtualVolumeNames The names of the virtual volumes to be removed.
     * 
     * @throws VPlexApiException When an errors occurs removing the virtual
     *             volumes to the storage view.
     */
    private void removeStorageViewVirtualVolumes(VPlexStorageViewInfo storageViewInfo,
            List<String> virtualVolumeNames) throws VPlexApiException {

        URI requestURI = _vplexApiClient.getBaseURI().resolve(
                VPlexApiConstants.URI_STORAGE_VIEW_REMOVE_VOLUMES);

        // Create the value for the volumes argument for the request.
        StringBuilder volumeNamesBuilder = new StringBuilder();
        for (String virtualVolumename : virtualVolumeNames) {
            if (volumeNamesBuilder.length() != 0) {
                volumeNamesBuilder.append(",");
            }
            volumeNamesBuilder.append(virtualVolumename);
        }

        modifyStorageViewVirtualVolumes(storageViewInfo, volumeNamesBuilder.toString(),
                requestURI);
    }

    /**
     * Adds or removes the passed virtual volumes to/from the passed storage
     * view according to the passed request URI.
     * 
     * @param storageViewInfo The storage view.
     * @param virtualVolumesArg The value for the volumes argument for the request.
     * @param requestURI The URI for the modification request.
     * 
     * @throws VPlexApiException When an error occurs modifying the virtual
     *             volumes for the storage view.
     */
    private void modifyStorageViewVirtualVolumes(VPlexStorageViewInfo storageViewInfo,
            String virtualVolumesArg, URI requestURI) throws VPlexApiException {

        ClientResponse response = null;
        try {
            // Build the request post data and make the request.
            s_logger.info("Modify storage view virtual volumes request URI is {}",
                    requestURI.toString());
            Map<String, String> argsMap = new HashMap<String, String>();
            argsMap.put(VPlexApiConstants.ARG_DASH_O, virtualVolumesArg);
            argsMap.put(VPlexApiConstants.ARG_DASH_V, storageViewInfo.getPath());
            JSONObject postDataObject = VPlexApiUtils.createPostData(argsMap, true);
            s_logger.info("Storage view modify virtual volumes POST data is {}",
                    postDataObject.toString());
            response = _vplexApiClient.post(requestURI,
                    postDataObject.toString());
            String responseStr = response.getEntity(String.class);
            s_logger.info("Storage view modify virtual volumes response is {}",
                    responseStr);
            if (response.getStatus() != VPlexApiConstants.SUCCESS_STATUS) {
                if (response.getStatus() == VPlexApiConstants.ASYNC_STATUS) {
                    s_logger.info("Storage view volume changes completing asynchronously");
                    _vplexApiClient.waitForCompletion(response);
                } else {
                    String cause = VPlexApiUtils.getCauseOfFailureFromResponse(responseStr);
                    throw VPlexApiException.exceptions
                            .modifyViewVolumesFailureStatus(storageViewInfo.getName(),
                                    String.valueOf(response.getStatus()), cause);
                }
            }
            s_logger.info("Successfully updated volumes for storage view {}",
                    storageViewInfo.getName());
        } catch (VPlexApiException vae) {
            throw vae;
        } catch (Exception e) {
            throw VPlexApiException.exceptions.failedModifyViewVolumes(
                    storageViewInfo.getName(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Gets the target info for the passed ports.
     * 
     * @param portInfoList The list of ports.
     * 
     * @return A map of the associated target info for the ports keyed by the
     *         port WWN.
     * 
     * @throws VPlexApiException When an error occurs getting the target info.
     */
    public Map<String, VPlexTargetInfo> getTargetInfoForPorts(
            List<VPlexPortInfo> portInfoList) throws VPlexApiException {

        // There will only be targets for FE ports. Ignore ports that are
        // not FE ports.
        List<PortInfo> fePortInfoList = new ArrayList<PortInfo>();
        for (VPlexPortInfo portInfo : portInfoList) {
            if (portInfo.isFrontendPort()) {
                PortInfo fePortInfo = new PortInfo(portInfo.getPortWwn());
                fePortInfoList.add(fePortInfo);
            }
        }

        // Get the target info for these FE ports.
        List<VPlexTargetInfo> targetInfoList = new ArrayList<VPlexTargetInfo>();
        findTargets(fePortInfoList, targetInfoList, false);

        // Now map the target info for each FE port to the
        // port WWN of that port.
        Map<String, VPlexTargetInfo> targetInfoMap = new HashMap<String, VPlexTargetInfo>();
        for (VPlexTargetInfo targetInfo : targetInfoList) {
            targetInfoMap.put(targetInfo.getPortWwn(), targetInfo);
        }
        return targetInfoMap;
    }

    /**
     * This methods builds the VPlexInitiatorInfo for the initiators which does not
     * exist on VPlex and returns the list of VPlexInitiatorInfo for all the initiators.
     * 
     * @param alreadyFoundinitiatorInfoList List of initiators that exist on VPlex
     * @param initiatorPortInfo All the initiators that needs to be registered on VPlex
     * @param clusterInfo The VPlex cluster info for the VPlex cluster where initiators
     *            should be registered
     * 
     * @return List of VPlexInitiatorInfo that includes all the initiators that needs
     *         to be registered and the initiators which already exist on VPlex
     */
    private List<VPlexInitiatorInfo> buildInitiatorInfoList(List<VPlexInitiatorInfo> alreadyFoundinitiatorInfoList,
            List<PortInfo> initiatorPortInfo, VPlexClusterInfo clusterInfo) {
        List<VPlexInitiatorInfo> initiatorInfoList = new ArrayList<VPlexInitiatorInfo>();

        // Create map by pwwn of the already found initiators on VPLEX.
        Map<String, VPlexInitiatorInfo> initiatorInfoMap = new HashMap<String, VPlexInitiatorInfo>();
        for (VPlexInitiatorInfo initiatorInfo : alreadyFoundinitiatorInfoList) {
            initiatorInfoMap.put(initiatorInfo.getPortWwn(), initiatorInfo);
        }

        // Iterate over all the requested initiatorPortInfo to build VPlexInitiatorInfo if its not
        // found on the VPLEX.
        for (PortInfo initiatorInfo : initiatorPortInfo) {
            if (initiatorInfoMap.get(initiatorInfo.getPortWWN()) != null) {
                initiatorInfoList.add(initiatorInfoMap.get(initiatorInfo.getPortWWN()));
            } else {
                // This initiator does not exist on VPlex. Create VPlexInitiatorInfo for it.
                s_logger.info("Creating VPlexInitiatorInfo for the initiator :" + initiatorInfo.getPortWWN());
                VPlexInitiatorInfo info = new VPlexInitiatorInfo();
                info.setRegistrationName(VPlexApiConstants.REGISTERED_INITIATOR_PREFIX
                        + VPlexApiConstants.WWN_PREFIX
                        + initiatorInfo.getPortWWN().toLowerCase());
                info.setName(VPlexApiConstants.UNREGISTERED_INITIATOR_PREFIX
                        + VPlexApiConstants.WWN_PREFIX
                        + initiatorInfo.getPortWWN().toLowerCase());
                info.setPortWwn(VPlexApiConstants.WWN_PREFIX + initiatorInfo.getPortWWN().toLowerCase());
                info.setNodeWwn(VPlexApiConstants.WWN_PREFIX + initiatorInfo.getNodeWWN().toLowerCase());
                if (initiatorInfo.getType() != null && !initiatorInfo.getType().isEmpty()) {
                    info.setInitiatorType(Initiator_Type.valueOfType(initiatorInfo.getType()));
                }
                info.setPath(clusterInfo.getPath() + VPlexApiConstants.URI_INITIATORS + info.getName());
                initiatorInfoList.add(info);
            }
        }

        return initiatorInfoList;
    }
}
