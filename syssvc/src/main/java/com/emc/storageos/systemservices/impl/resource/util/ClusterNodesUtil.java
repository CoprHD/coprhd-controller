/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource.util;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Class used to get cluster nodes information.
 */
public class ClusterNodesUtil {
    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(ClusterNodesUtil.class);

    // A reference to the coordinator client for retrieving the registered
    // Bourne nodes.
    private static volatile CoordinatorClient _coordinator;

    // A reference to the service for getting Bourne cluster information.
    private static volatile Service _service;

    private static volatile CoordinatorClientExt _coordinatorExt;

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setCoordinatorExt(CoordinatorClientExt coordinatorExt) {
        _coordinatorExt = coordinatorExt;
    }

    public void setService(Service service) {
        _service = service;
    }

    /**
     * Gets a reference to the node connection info for the nodes requested.
     * 
     * @param nodeIds List of node ids whose information is returned
     * @return A list containing the connection info for all nodes in the Bourne
     *         cluster.
     * @throws IllegalStateException When an exception occurs trying to get the
     *             cluster nodes.
     */
    public static List<NodeInfo> getClusterNodeInfo(List<String> nodeIds) {
        List<NodeInfo> nodeInfoList = new ArrayList<NodeInfo>();
        List<String> validNodeIds = new ArrayList<String>();
        try {
            if (nodeIds != null && !nodeIds.isEmpty()) {
                _log.info("Getting cluster node info for ids: {}", nodeIds);
            }
            else {
                _log.info("Getting cluster node info for all nodes");
            }

            // We get all instances of the "syssvc" services registered with the
            // cluster coordinator. There will be one on each Bourne cluster
            // node.
            List<Service> svcList = _coordinator.locateAllServices(_service.getName()
                    , _service.getVersion(), null, null);
            for (Service svc : svcList) {
                _log.debug("Got service with node id " + svc.getNodeId());
                // if there are node ids requested
                if (nodeIds != null && !nodeIds.isEmpty() &&
                        !nodeIds.contains(svc.getNodeId())) {
                    continue;
                }
                // The service identifier specifies the connection information
                // for the node on which the service executes.
                URI nodeEndPoint = svc.getEndpoint(null);
                if (nodeEndPoint != null) {
                    nodeInfoList.add(new NodeInfo(svc.getNodeId(),svc.getNodeName(), nodeEndPoint));
                    validNodeIds.add(svc.getNodeId());
                }
            }
            _log.debug("Valid node ids: {}", validNodeIds);
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("cluster nodes info", "coordinator", e);
        }

        // validate if all requested node ids information is retrieved
        if (nodeIds != null && !nodeIds.isEmpty() &&
                !validNodeIds.containsAll(nodeIds)) {
            nodeIds.removeAll(validNodeIds);
            throw APIException.badRequests.parameterIsNotValid("node id(s): " + nodeIds);
        }
        return nodeInfoList;
    }

    /**
     * Gets a reference to the node connection info for all nodes in the Bourne
     * cluster.
     */
    public static List<NodeInfo> getClusterNodeInfo() {
        return getClusterNodeInfo(null);
    }

    public static ArrayList<String> getUnavailableControllerNodes() {
        return _coordinatorExt.getUnavailableControllerNodes();
    }
}
