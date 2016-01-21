/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.ManagedResourceCapacity;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.ServicesMetadata;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.healthmonitor.*;
import com.emc.storageos.systemservices.impl.healthmonitor.models.*;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.storageos.systemservices.impl.resource.util.ClusterNodesUtil;
import com.emc.storageos.systemservices.impl.resource.util.NodeDataCollector;
import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.resource.util.NodeDataCollector.Action;
import com.emc.vipr.model.sys.healthmonitor.DataDiskStats;
import com.emc.vipr.model.sys.healthmonitor.DiagRequestParams;
import com.emc.vipr.model.sys.healthmonitor.DiagnosticsRestRep;
import com.emc.vipr.model.sys.healthmonitor.DiagTest;
import com.emc.vipr.model.sys.healthmonitor.HealthRestRep;
import com.emc.vipr.model.sys.healthmonitor.NodeDiagnostics;
import com.emc.vipr.model.sys.healthmonitor.NodeHardwareInfo.NodeHardwareInfoType;
import com.emc.vipr.model.sys.healthmonitor.NodeHardwareInfoRestRep;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.NodeStats;
import com.emc.vipr.model.sys.healthmonitor.RequestParams;
import com.emc.vipr.model.sys.healthmonitor.ServiceHealth;
import com.emc.vipr.model.sys.healthmonitor.StatsRestRep;
import com.emc.vipr.model.sys.healthmonitor.StorageStats;
import com.emc.vipr.model.sys.healthmonitor.TestParam;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.*;

/**
 * Class that provides REST API for node(and its services) health and statistics.
 */
@Path("/monitor")
public class HealthMonitorService extends BaseLogSvcResource {

    private static final Logger _log = LoggerFactory.getLogger(HealthMonitorService
            .class);

    @Autowired
    private CoordinatorClientExt _coordinatorClientExt;
    @Autowired
    private LicenseManager _licenseManager;
    @Autowired
    private NodeResourceAllocationChecker _checker;

    private static final String INTERNAL_NODE_STATS_URI =
            "/monitor/internal/node-stats";
    private static final String INTERNAL_NODE_HARDWARE_URI =
            "/monitor/internal/node-hardware-info";
    private static final String INTERNAL_NODE_HEALTH_URI =
            "/monitor/internal/node-health";
    private static final String INTERNAL_NODE_DIAGNOSTICS_URI =
            "/monitor/internal/node-diagnostics";

    /**
     * Internal method to get node statistics
     * 
     * @return Node stats response
     */
    @POST
    @Path("/internal/node-stats")
    @Produces({ MediaType.APPLICATION_JSON })
    public NodeStats getNodeStats(RequestParams requestParams) {
        _log.info("Retrieving node stats");
        String nodeId = _coordinatorClientExt.getMyNodeId();
        String nodeName = _coordinatorClientExt.getMyNodeName();
        return getNodeStats(nodeId, nodeName, getNodeIP(nodeId),
                requestParams.getInterval(),
                ServicesMetadata.getRoleServiceNames(_coordinatorClientExt.getNodeRoles()));
    }

    /**
     * Internal method to get node hardware info
     * 
     * @return node hardware info response
     */
    @GET
    @Path("/internal/node-hardware-info")
    @Produces({ MediaType.APPLICATION_JSON })
    public NodeHardwareInfoRestRep getNodeHardwareInfo() {
        _log.info("Retrieving node hardware info");
        String nodeId = _coordinatorClientExt.getMyNodeId();
        return getNodeHardWareInfo(nodeId);
    }

    /**
     * Get statistics of virtual machine and its active services
     * Virtual machine stats include memory usage, I/O for each device,
     * load average numbers
     * Service stats include service memory usage, command that invoked it,
     * file descriptors count and other stats (uptime, start time, thread count).
     * <p/>
     * If interval value is passed it will return differential disk stats: difference between first report (contains stats for the time
     * since system startup) and second report (stats collected during the interval since the first report).
     * 
     * @brief Show disk, memory, service statistics of all virtual machines
     * @param nodeIds  node ids for which stats are collected.
     * @param nodeNames node names for which stats are collected.
     * @param interval Specifies amount of time in seconds for differential stats.
     * @prereq none
     * @return Stats response
     */
    @GET
    @Path("/stats")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StatsRestRep getStats(@QueryParam("node_id") List<String> nodeIds,
                                 @QueryParam("interval") int interval,
                                 @QueryParam("node_name") List<String> nodeNames) {

        nodeIds=_coordinatorClientExt.combineNodeNamesWithNodeIds(nodeNames,nodeIds);

        _log.info("Retrieving stats for nodes. Requested node ids: {}", nodeIds);

        StatsRestRep statsRestRep = new StatsRestRep();
        List<NodeInfo> nodeInfoList = ClusterNodesUtil.getClusterNodeInfo(nodeIds);

        // Validate 'interval'
        if (interval < 0) {
            throw APIException.badRequests.parameterIsNotValid("interval");
        }
        RequestParams requestParams = new RequestParams(interval);
        Map<String, NodeStats> nodesData = NodeDataCollector.getDataFromNodes
                (nodeInfoList, INTERNAL_NODE_STATS_URI,
                        Action.POST, requestParams, NodeStats.class, null);
        statsRestRep.getNodeStatsList().addAll(nodesData.values());
        return statsRestRep;
    }

    /**
     * Internal method to get node health.
     * 
     * @return Node health response
     */
    @GET
    @Path("/internal/node-health")
    @Produces({ MediaType.APPLICATION_JSON })
    public NodeHealth getNodeHealth() {
        _log.info("Retrieving node health");
        String nodeId = _coordinatorClientExt.getMyNodeId();
        String nodeName = _coordinatorClientExt.getMyNodeName();
        return getNodeHealth(nodeId,nodeName, getNodeIP(nodeId),
                ServicesMetadata.getRoleServiceNames(_coordinatorClientExt.getNodeRoles()));
    }

    /**
     * Gets health of node and its services.
     * <p/>
     * Node health status: Good - when node is reachable and all its services are GOOD Unavailable - when node is not reachable Degraded -
     * when node is reachable and any of its service is Unavailable/Degraded Node/syssvc Unavailable - when node is down or syssvc is not
     * Unavailable on the node
     * <p/>
     * Service health status: Good - when a service is up and running Unavailable - when a service is not running but is registered in
     * coordinator Restarted - when service is restarting
     * 
     * @brief Show service health of all virtual machines
     * @param nodeIds node ids for which health stats are collected.
     * @param nodeNames node names for which health stats are collected.
     * @prereq none
     * @return Health response.
     */
    @GET
    @Path("/health")
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public HealthRestRep getHealth(@QueryParam("node_id") List<String> nodeIds, @QueryParam("node_name") List<String> nodeNames) {
        HealthRestRep healthRestRep = new HealthRestRep();
        List<NodeHealth> nodehealthList = healthRestRep.getNodeHealthList();

        nodeIds=_coordinatorClientExt.combineNodeNamesWithNodeIds(nodeNames,nodeIds);

        //Collecting data from all nodes
        List<NodeInfo> nodeInfoList = ClusterNodesUtil.getClusterNodeInfo(nodeIds);
        Map<String, NodeHealth> nodesData = NodeDataCollector.getDataFromNodes
                (nodeInfoList, INTERNAL_NODE_HEALTH_URI,
                        Action.GET, null, NodeHealth.class, null);
        nodehealthList.addAll(nodesData.values());

        String thisNodeId = _coordinatorClientExt.getMyNodeId();
        if (thisNodeId.equals("standalone")) {
            return healthRestRep;
        }

        Map<String, DualInetAddress> ipLookupTable = _coordinatorClientExt.getCoordinatorClient().getInetAddessLookupMap()
                .getControllerNodeIPLookupMap();

        // get all nodes if the input param is empty
        if (nodeIds == null || nodeIds.isEmpty()) {
            int clusterNodeCount = _coordinatorClientExt.getNodeCount();
            nodeIds = new ArrayList<>();
            for (int i = 1; i <= clusterNodeCount; i++) {
                String nodeId = "vipr" + i;
                nodeIds.add(nodeId);
            }
        }

        // Adding health for nodes that are not returned
        for (String nodeId : nodeIds) {
            DualInetAddress ip = ipLookupTable.get(nodeId);
            if (!nodesData.containsKey(nodeId)) {
                String nodeName = _coordinatorClientExt.getPropertyInfo().getProperty("node_"+nodeId.replace("vipr","")+"_name");
                nodehealthList.add(new NodeHealth(nodeId,nodeName,ip.toString(), Status.NODE_OR_SYSSVC_UNAVAILABLE.toString()));
            }
        }
        return healthRestRep;
    }

    /**
     * Get results of diagtool shell script for all virtual machines in a ViPR
     * controller appliance. Also gives test details when verbose option
     * is set.
     * 
     * @brief Get diagtool script results
     * @param nodeIds node ids for which diagnostic results are collected.
     * @param nodeNames node names for which diagnostic results are collected.
     * @param verbose when set to "1"  will run command with -v option.
     * @prereq none
     * @return Returns diagnostic test results.
     */
    @GET
    @Path("/diagnostics")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public DiagnosticsRestRep getDiagnostics(@QueryParam("node_id") List<String> nodeIds,
                                             @QueryParam("verbose") String verbose,
                                             @QueryParam("node_name") List<String> nodeNames) {
        _log.info("Initiating diagnostics test for nodes");

        nodeIds=_coordinatorClientExt.combineNodeNamesWithNodeIds(nodeNames,nodeIds);

        boolean isVerbose = ("1".equals(verbose)) ? true : false;
        DiagRequestParams diagRequestParams = new DiagRequestParams(isVerbose);
        DiagnosticsRestRep diagnosticsRestRep = new DiagnosticsRestRep();
        List<NodeInfo> nodeInfoList = ClusterNodesUtil.getClusterNodeInfo(nodeIds);
        Map<String, NodeDiagnostics> nodesData = NodeDataCollector.getDataFromNodes
                (nodeInfoList, INTERNAL_NODE_DIAGNOSTICS_URI,
                        Action.POST, diagRequestParams, NodeDiagnostics.class, null);

        String allocationResult = _checker.getNodeResourceAllocationCheckResult();
        DiagTest allocationTest = new DiagTest("Resource allocation", allocationResult, new ArrayList<TestParam>());
        for (Map.Entry<String, NodeDiagnostics> entry : nodesData.entrySet()) {
            List<DiagTest> diagTests = entry.getValue().getDiagTests();
            diagTests.add(allocationTest);
            entry.getValue().setDiagTests(diagTests);
        }

        diagnosticsRestRep.getNodeDiagnosticsList().addAll(nodesData.values());
        return diagnosticsRestRep;
    }

    /**
     * Internal method that gets results of diagtool for each node.
     * 
     * @param requestParams Contains verbose option for diagtool
     * @return Returns node diagnostics
     */
    @POST
    @Path("/internal/node-diagnostics")
    @Produces({ MediaType.APPLICATION_JSON })
    public NodeDiagnostics getNodeDiagnostics(DiagRequestParams requestParams) {
        String nodeId = _coordinatorClientExt.getMyNodeId();
        String nodeName = _coordinatorClientExt.getMyNodeName();
        _log.info("Retrieving node diagnostics for node: {}", nodeId);
        return new NodeDiagnostics(nodeId, nodeName, getNodeIP(nodeId),
                DiagnosticsExec.getDiagToolResults(requestParams.isVerbose()
                        ? DiagConstants.VERBOSE : ""));
    }

    /**
     * Get the current capacity for object, file and block storage.
     * 
     * @brief Show storage capacity
     * @prereq none
     * @return Storage stats for controller (file & block) and object.
     */
    @GET
    @Path("/storage")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageStats getStorageStats() {
        _log.info("Getting storage stats");
        StorageStats.ControllerStorageStats controllerStats = null;
        if (_licenseManager.isProductLicensed(LicenseType.CONTROLLER)) {
            ManagedResourcesCapacity resourceCapacities = _licenseManager
                    .getControllerCapacity();
            controllerStats = new StorageStats.ControllerStorageStats();
            for (ManagedResourceCapacity cap : resourceCapacities.getResourceCapacityList()) {
                switch (cap.getType()) {
                    case VOLUME:
                        controllerStats.setBlockCapacityKB(cap.getResourceCapacity() / StatConstants.CAPACITY_CONVERSION_VALUE);
                        break;
                    case FILESHARE:
                        controllerStats.setFileCapacityKB(cap.getResourceCapacity() / StatConstants.CAPACITY_CONVERSION_VALUE);
                        break;
                    case POOL:
                        controllerStats.setFreeManagedCapacityKB(cap.getResourceCapacity() / StatConstants.CAPACITY_CONVERSION_VALUE);
                        break;
                    case BUCKET:
                        controllerStats.setObjectCapacityKB(cap.getResourceCapacity() / StatConstants.CAPACITY_CONVERSION_VALUE);
                        break;
                }
            }
        }

        return new StorageStats(controllerStats);
    }

    /**
     * Returns IP address of the node
     * 
     * @param nodeId node id
     * @return IP address
     */
    private String getNodeIP(String nodeId) {
        Map<String, DualInetAddress> ipLookupTable = _coordinatorClientExt.getCoordinatorClient().getInetAddessLookupMap()
                .getControllerNodeIPLookupMap();
        DualInetAddress ip = ipLookupTable.get(nodeId);
        return ip.toString();
    }

    /**
     * Main method for starting the process to extract proc data from the
     * desired Storageos related pids.
     * 
     * @return NodeStats
     */
    protected NodeStats getNodeStats(String nodeId,String nodeName, String nodeIP, int interval,
                                     List<String> availableServices) {
        try {
            _log.info("List of available services: {}", availableServices);
            return new NodeStats(nodeId, nodeName, nodeIP, ProcStats.getLoadAvgStats(),
                    ProcStats.getMemoryStats(), ProcStats.getDataDiskStats(),
                    NodeStatsExtractor.getServiceStats(availableServices),
                    NodeStatsExtractor.getDiskStats(interval));
        } catch (Exception e) {
            _log.error("Internal error occurred while getting node stats. {}", e);
            _log.debug(ExceptionUtils.getStackTrace(e));
            throw APIException.internalServerErrors.getObjectError("node stats", e);
        }
    }

    /**
     * Method that returns node and it services health.
     * 
     * @return NodeHealth
     */
    protected NodeHealth getNodeHealth(String nodeId, String nodeName, String nodeIP,
                                       List<String> availableServices) {
        try {
            _log.info("List of available services: {}", availableServices);
            String nodeStatus = Status.GOOD.toString();
            List<ServiceHealth> serviceHealthList = NodeHealthExtractor.getServiceHealth
                    (NodeStatsExtractor.getServiceStats(availableServices), _coordinatorClientExt.getCoordinatorClient(), nodeId);
            for (ServiceHealth serviceHealth : serviceHealthList) {
                if (Status.UNAVAILABLE.toString().equals(serviceHealth.getStatus())
                        || Status.DEGRADED.toString().equals(serviceHealth.getStatus())) {
                    nodeStatus = Status.DEGRADED.toString();
                    break;
                }
            }
            return new NodeHealth(nodeId, nodeName, nodeIP, nodeStatus, serviceHealthList);
        } catch (Exception e) {
            _log.error("Internal error occurred while getting node health. {}", e);
            _log.debug(ExceptionUtils.getStackTrace(e));
            throw APIException.internalServerErrors.getObjectError("health for node " +
                    nodeId, e);
        }
    }

    /**
     * Get node hard ware info
     *
     * @param nodeId
     * @return
     */
    private NodeHardwareInfoRestRep getNodeHardWareInfo(String nodeId) {
        try {
            Map<NodeHardwareInfoType, Float> hardwareInfos = new HashMap<NodeHardwareInfoType, Float>();
            hardwareInfos.put(NodeHardwareInfoType.CPUCOUNT, (float) ProcStats.getCPUCount());
            hardwareInfos.put(NodeHardwareInfoType.CPUFREQ, ProcStats.getCPUFrequence());
            hardwareInfos.put(NodeHardwareInfoType.MEMORY, (float) ProcStats.getMemoryStats().getMemTotal());
            hardwareInfos.put(NodeHardwareInfoType.DISK, (float) getNodeDiskAmount());
            return new NodeHardwareInfoRestRep(nodeId, getNodeIP(nodeId), hardwareInfos);
        } catch (Exception e) {
            _log.error("Internal error occurred while getting node hardware info. {}", e);
            _log.debug(ExceptionUtils.getStackTrace(e));
            throw APIException.internalServerErrors.getObjectError("node hardware info", e);
        }
    }

    private long getNodeDiskAmount() {
        DataDiskStats dataDiskStats = ProcStats.getDataDiskStats();
        long rootDiskAmount = dataDiskStats.getRootAvailKB() +
                dataDiskStats.getRootUsedKB();
        long dataDiskAmount = dataDiskStats.getDataAvailKB() +
                dataDiskStats.getDataUsedKB();
        return (rootDiskAmount + dataDiskAmount);
    }
}
