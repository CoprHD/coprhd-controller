/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.management.jmx.logging.LoggingOps;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.ServicesMetadata;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.logsvc.LogLevelManager;
import com.emc.storageos.systemservices.impl.logsvc.LogNetworkWriter;
import com.emc.storageos.systemservices.impl.logsvc.LogRequestParam;
import com.emc.storageos.systemservices.impl.logsvc.merger.LogNetworkStreamMerger;
import com.emc.storageos.systemservices.impl.resource.util.ClusterNodesUtil;
import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.logging.LogLevelRequest;
import com.emc.vipr.model.sys.logging.LogLevels;
import com.emc.vipr.model.sys.logging.LogRequest;
import com.emc.vipr.model.sys.logging.LogScopeEnum;
import com.emc.vipr.model.sys.logging.LogSeverity;
import com.emc.vipr.model.sys.logging.SetLogLevelParam;

/**
 * Defines the API for making requests to the log service.
 */
@Path("/logs/")
public class LogService extends BaseLogSvcResource {

    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(LogService.class);
    public final static int MAX_THREAD_COUNT = 10;
    public static AtomicInteger runningRequests = new AtomicInteger(0);

    // FIXME: I have no idea how to register logging MBean to these two services now
    private List<String> _exemptLogSvcs = new ArrayList<>();
    private static final List<LogSeverity> VALID_LOG4J_SEVS = Arrays.asList(
            LogSeverity.FATAL, LogSeverity.ERROR, LogSeverity.WARN, LogSeverity.INFO,
            LogSeverity.DEBUG, LogSeverity.TRACE);
    private static final List<String> VALID_LOG4J_SEV_STRS = new ArrayList<String>();
    private static final int MAX_LOG_LEVEL_EXPIR = 2880; // two days

    @Autowired
    private CoordinatorClientExt _coordinatorClientExt;

    static {
        // Construct a user-friendly string indicating the valid log severities.
        for (LogSeverity sev : VALID_LOG4J_SEVS) {
            StringBuilder sb = new StringBuilder();
            sb.append(sev.ordinal());
            sb.append("(" + sev.name() + ")");
            VALID_LOG4J_SEV_STRS.add(sb.toString());
        }
    }

    /**
     * Default constructor.
     */
    public LogService() {
    }

    /**
     * Setter for the services not eligible for dynamic log level control.
     *
     * @param services A list of service names not eligible for dynamic log level
     *            control.
     */
    public void setExemptLoggerService(List<String> services) {
        _exemptLogSvcs = services;
    }

    /**
     * Get log data from the specified virtual machines that are filtered, merged,
     * and sorted based on the passed request parameters and streams the log
     * messages back to the client as JSON formatted strings.
     *
     * @brief Show logs from all or specified virtual machine
     * @param nodeIds The ids of the virtual machines for which log data is
     *            collected.
     *            Allowed values: standalone,
     *            control nodes: vipr1,vipr2 etc
     *            data services nodes: dataservice-10-111-111-222 (node-ip-address)
     * @param nodeNames The custom names of the vipr nodes for which log data is
     *            collected.
     *            Allowed values: Current values of node_x_name properties
     * @param logNames The names of the log files to process.
     * @param severity The minimum severity level for a logged message.
     *            Allowed values:0-9. Default value: 7
     * @param startTimeStr The start datetime of the desired time window. Value is
     *            inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or
     *            datetime in ms.
     *            Default: Set to yesterday same time
     * @param endTimeStr The end datetime of the desired time window. Value is
     *            inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or
     *            datetime in ms.
     * @param msgRegex A regular expression to which the log message conforms.
     * @param maxCount Maximum number of log messages to retrieve. This may return
     *            more than max count, if there are more messages with same
     *            timestamp as of the latest message.
     *            Value should be greater than 0.
     * @param dryRun if true, the API will do a dry run for log collection. Instead
     *            of collecting logs from nodes, dry run will check the nodes'
     *            availability for collecting logs. Entity body of the response
     *            will return an error message string indicating which node(s)
     *            not available for collecting logs. If log collection is ok
     *            for all specified nodes, no error message is included in
     *            response.
     *            Default value of this parameter is false.
     * @prereq none
     * @return A reference to the StreamingOutput to which the log data is
     *         written.
     * @throws WebApplicationException When an invalid request is made.
     */
    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN })
    public Response getLogs(
            @QueryParam(LogRequestParam.NODE_ID) List<String> nodeIds,
            @QueryParam(LogRequestParam.NODE_NAME) List<String> nodeNames,
            @QueryParam(LogRequestParam.LOG_NAME) List<String> logNames,
            @DefaultValue(LogSeverity.DEFAULT_VALUE_AS_STR) @QueryParam(LogRequestParam
            .SEVERITY) int severity,
            @QueryParam(LogRequestParam.START_TIME) String startTimeStr,
            @QueryParam(LogRequestParam.END_TIME) String endTimeStr,
            @QueryParam(LogRequestParam.MSG_REGEX) String msgRegex,
            @QueryParam(LogRequestParam.MAX_COUNT) int maxCount,
            @QueryParam(LogRequestParam.DRY_RUN) @DefaultValue("false") boolean dryRun)
            throws Exception {
        _log.info("Received getlogs request");
        enforceRunningRequestLimit();

        final MediaType mediaType = getMediaType();
        _log.info("Logs request media type {}", mediaType);

        nodeIds = _coordinatorClientExt.combineNodeNamesWithNodeIds(nodeNames, nodeIds);

        // Validate the passed node ids.
        validateNodeIds(nodeIds);
        _log.debug("Validated requested nodes");

        // Validate the passed severity is valid.
        validateLogSeverity(severity);
        _log.debug("Validated requested severity");

        // Validate the passed start and end times are valid.
        Date startTime = TimeUtils.getDateTimestamp(startTimeStr);
        _log.info(startTime.toString());
        Date endTime = TimeUtils.getDateTimestamp(endTimeStr);
        _log.info(endTime.toString());
        validateTimestamps(startTime, endTime);
        _log.debug("Validated requested time window");

        // Setting default start time to yesterday
        if (startTime == null) {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DATE, -1);
            startTime = yesterday.getTime();
            _log.info("Setting start time to yesterday {} ", startTime);
        }

        // Validate regular message
        validateMsgRegex(msgRegex);
        _log.debug("Validated regex");

        // Validate max count
        if (maxCount < 0) {
            throw APIException.badRequests.parameterIsNotValid("maxCount");
        }

        // validate log names
        Set<String> allLogNames = getValidLogNames();
        _log.debug("valid log names {}", allLogNames);
        boolean invalidLogName = false;
        for (String logName : logNames) {
            if (!allLogNames.contains(logName)) {
                invalidLogName = true;
                break;
            }
        }
        if (invalidLogName) {
            throw APIException.badRequests.parameterIsNotValid("log names");
        }

        if (dryRun) {
            List<NodeInfo> clusterNodesInfo = ClusterNodesUtil.getClusterNodeInfo();
            if (clusterNodesInfo.isEmpty()) {
                _log.error("No nodes available for collecting logs");
                throw APIException.internalServerErrors.noNodeAvailableError("no nodes available for collecting logs");
            }
            List<NodeInfo> matchingNodes = null;
            if (nodeIds.isEmpty()) {
                matchingNodes = clusterNodesInfo;
            }
            else {
                matchingNodes = new ArrayList<NodeInfo>();
                for (NodeInfo node : clusterNodesInfo) {
                    if (nodeIds.contains(node.getId())) {
                        matchingNodes.add(node);
                    }
                }
            }

            // find the unavailable nodes
            List<String> failedNodes = null;
            if (matchingNodes.size() == 1 && matchingNodes.get(0).getId().equals("standalone")) {
                failedNodes = new ArrayList<String>();
            }
            else {
                // find the unavailable nodes
                failedNodes = _coordinatorClientExt.getUnavailableControllerNodes();
            }

            if (!nodeIds.isEmpty()) {
                failedNodes.retainAll(nodeIds);
            }
            String baseNodeURL;
            SysClientFactory.SysClient sysClient;
            for (final NodeInfo node : matchingNodes) {
                baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT, node.getIpAddress(),
                        node.getPort());
                _log.debug("getting log names from node: " + baseNodeURL);
                sysClient = SysClientFactory.getSysClient(URI.create(baseNodeURL),
                        _logSvcPropertiesLoader.getNodeLogCollectorTimeout() * 1000,
                        _logSvcPropertiesLoader.getNodeLogConnectionTimeout() * 1000);
                LogRequest logReq = new LogRequest.Builder().nodeIds(nodeIds).baseNames(
                        getLogNamesFromAlias(logNames)).logLevel(severity).startTime(startTime)
                        .endTime(endTime).regex(msgRegex).maxCont(maxCount).build();
                logReq.setDryRun(true);
                try {
                    sysClient.post(SysClientFactory.URI_NODE_LOGS, null, logReq);
                } catch (Exception e) {
                    _log.error("Exception accessing node {}: {}", baseNodeURL, e);
                    failedNodes.add(node.getId());
                }
            }
            if (_coordinatorClientExt.getNodeCount() == failedNodes.size()) {
                throw APIException.internalServerErrors.noNodeAvailableError("All nodes are unavailable for collecting logs");
            }

            return Response.ok().build();
        }

        LogRequest logReqInfo = new LogRequest.Builder().nodeIds(nodeIds).baseNames(
                getLogNamesFromAlias(logNames)).logLevel(severity).startTime(startTime)
                .endTime(endTime).regex(msgRegex).maxCont(maxCount).build();
        _log.info("log request info is {}", logReqInfo.toString());
        final LogNetworkStreamMerger logRequestMgr = new LogNetworkStreamMerger(
                logReqInfo, mediaType, _logSvcPropertiesLoader);
        StreamingOutput logMsgStream = new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {
                try {
                    runningRequests.incrementAndGet();
                    logRequestMgr.streamLogs(outputStream);
                } finally {
                    runningRequests.decrementAndGet();
                }
            }
        };
        return Response.ok(logMsgStream).build();
    }

    /**
     * Internal Use
     * <p/>
     * Gets a chunk of the log data from the Bourne node to which the request is directed that is filtered, merged, and sorted based on the
     * passed request parameters. The log messages are returned as a JSON formatted string.
     *
     * @return A Response containing the log messages as a JSON formatted
     *         string.
     */
    @POST
    @Path("internal/node-logs/")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getNodeLogs(LogRequest logReqInfo) {
        _log.trace("Enter into getNodeLogs()");
        if (logReqInfo.isDryRun()) {
            return Response.ok().build();
        }
        final LogNetworkWriter logRequestMgr = new LogNetworkWriter(logReqInfo,
                _logSvcPropertiesLoader);
        StreamingOutput logMsgStream = new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) throws IOException,
                    WebApplicationException {
                logRequestMgr.write(outputStream);
            }
        };
        return Response.ok(logMsgStream).build();
    }

    /**
     * Get current logging levels for all services and virtual machines
     *
     * @brief Get current log levels
     * @param nodeIds The ids of the virtual machines for which log data is
     *            collected.
     *            Allowed values: standalone,vipr1,vipr2 etc
     * @param nodeNames The custom names of the vipr nodes for which log data is
     *            collected.
     *            Allowed values: standalone,vipr1,vipr2 etc
     * @param logNames The names of the log files to process.
     * @prereq none
     * @return A list of log levels
     * @throws WebApplicationException When an invalid request is made.
     */
    @GET
    @Path("log-levels/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public LogLevels getLogLevels(
            @QueryParam(LogRequestParam.NODE_ID) List<String> nodeIds,
            @QueryParam(LogRequestParam.NODE_NAME) List<String> nodeNames,
            @QueryParam(LogRequestParam.LOG_NAME) List<String> logNames)
            throws WebApplicationException {
        _log.info("Received getloglevels request");
        enforceRunningRequestLimit();

        MediaType mediaType = getMediaType();
        _log.debug("Get MediaType in header");

        nodeIds = _coordinatorClientExt.combineNodeNamesWithNodeIds(nodeNames, nodeIds);

        // Validate the passed node ids.
        validateNodeIds(nodeIds);
        _log.debug("Validated requested nodes");

        // Validate the passed log names.
        validateNodeServices(logNames);
        if (logNames != null && logNames.removeAll(_exemptLogSvcs)) {
            throw APIException.badRequests.parameterIsNotValid("log name");
        }
        _log.debug("Validated requested services");

        // Create the log request info bean from the request data.
        LogLevelRequest logLevelReq = new LogLevelRequest(nodeIds, logNames,
                LogSeverity.NA, null, null);
        final LogLevelManager logLevelMgr = new LogLevelManager(logLevelReq, mediaType,
                _logSvcPropertiesLoader);
        try {
            runningRequests.incrementAndGet();
            return logLevelMgr.process();
        } finally {
            runningRequests.decrementAndGet();
        }
    }

    /**
     * Update log levels
     *
     * @brief Update log levels
     * @param param The parameters required to update the log levels, including:
     *            node_id: optional, a list of node ids to be updated.
     *            All the nodes in the cluster will be updated by default
     *            log_name: optional, a list of service names to be updated.
     *            All the services will be updated by default
     *            severity: required, an int indicating the new log level.
     *            Refer to {@LogSeverity} for a full list of log levels.
     *            For log4j(the default logging implementation of ViPR),
     *            only the following values are valid:
     *            * 0 (FATAL)
     *            * 4 (ERROR)
     *            * 5 (WARN)
     *            * 7 (INFO)
     *            * 8 (DEBUG)
     *            * 9 (TRACE)
     * @prereq none
     * @return server response indicating if the operation succeeds.
     * @throws WebApplicationException When an invalid request is made.
     * @see LogSeverity
     */
    @POST
    @Path("log-levels/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.SECURITY_ADMIN })
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response setLogLevels(SetLogLevelParam param)
            throws WebApplicationException {
        _log.info("Received setloglevels request");
        enforceRunningRequestLimit();

        MediaType mediaType = getMediaType();
        _log.debug("Get MediaType {} in header", mediaType);

        // get nodeIds for node names
        List<String> nodeIds = _coordinatorClientExt.combineNodeNamesWithNodeIds(param.getNodeNames(), param.getNodeIds());
        param.setNodeIds(nodeIds);

        // Validate the passed node ids.
        validateNodeIds(param.getNodeIds());
        _log.debug("Validated requested nodes: {}", param.getNodeIds());

        // Validate the passed log names.
        validateNodeServices(param.getLogNames());
        if (param.getLogNames() != null && param.getLogNames().removeAll(_exemptLogSvcs)) {
            throw APIException.badRequests.parameterIsNotValid("log name");
        }
        _log.debug("Validated requested services: {}", param.getLogNames());

        // Validate the passed severity is valid.
        if (param.getSeverity() == null) {
            throw APIException.badRequests.invalidSeverityInURI("null", VALID_LOG4J_SEVS.toString());
        }
        LogSeverity logSeverity = validateLogSeverity(param.getSeverity());
        if (!VALID_LOG4J_SEVS.contains(logSeverity)) {
            throw APIException.badRequests.invalidSeverityInURI(logSeverity.toString(),
                    VALID_LOG4J_SEVS.toString());
        }
        _log.debug("Validated requested severity: {}", param.getSeverity());

        // Validate the passed expiration time is valid.
        if (param.getExpirInMin() != null && (param.getExpirInMin() < 0 || param.getExpirInMin() >=
                MAX_LOG_LEVEL_EXPIR)) {
            throw APIException.badRequests.parameterNotWithinRange("expir_in_min",
                    param.getExpirInMin(), 0, MAX_LOG_LEVEL_EXPIR, "");
        }
        _log.debug("Validated requested expiration: {}", param.getExpirInMin());

        // Validate the passed log level scope value.
        String scopeLevel = validateLogScope(param.getScope());
        _log.debug("Validated requested scope: {}", param.getScope());

        // Create the log request info bean from the request data.
        LogLevelRequest logLevelReq = new LogLevelRequest(param.getNodeIds(), param.getLogNames(),
                logSeverity, param.getExpirInMin(), scopeLevel);
        final LogLevelManager logLevelMgr = new LogLevelManager(logLevelReq, mediaType,
                _logSvcPropertiesLoader);
        try {
            runningRequests.incrementAndGet();
            logLevelMgr.process();
        } finally {
            runningRequests.decrementAndGet();
        }

        return Response.ok().build();
    }

    /**
     * Internal Use
     * <p/>
     * Gets/sets the log level of the Bourne node to which the request is directed that is filtered based on the passed request paramters.
     *
     * @return A Response containing the log levels for each service specified
     *         in the request.
     */
    @POST
    @Path("internal/log-level/")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public LogLevels processNodeLogLevel(LogLevelRequest logReqInfo)
            throws WebApplicationException {
        String nodeId = logReqInfo.getNodeIds().get(0);
        LogLevels logLevels = new LogLevels();

        // filter the log names list
        List<String> logNames = logReqInfo.getLogNames();
        List<String> availableLogNames =
                ServicesMetadata.getRoleServiceNames(_coordinatorClientExt.getNodeRoles());
        if (logNames.isEmpty()) {
            logNames = new ArrayList<String>(availableLogNames);
        } else {
            logNames.retainAll(availableLogNames);
        }
        logNames.removeAll(_exemptLogSvcs);

        boolean isGetReq = false;
        if (logReqInfo.getSeverity() == LogSeverity.NA) {
            isGetReq = true;
        }

        for (String logName : logNames) {
            if (isGetReq) {
                _log.info("getting log level from service {}", logName);
            } else {
                _log.info("setting log level of service {}", logName);
            }

            try {
                String level = null;
                if (isGetReq) {
                    level = LoggingOps.getLevel(logName);
                    _log.debug("log level of service {} is {}", logName, level);
                    String nodeName = _coordinatorClientExt.getMatchingNodeName(nodeId);
                    logLevels.getLogLevels().add(new LogLevels.LogLevel(nodeId, nodeName, logName,
                            level));
                } else {
                    // set logger level
                    level = logReqInfo.getSeverity().toString();
                    LoggingOps.setLevel(logName, level, logReqInfo.getExpirInMin(), logReqInfo.getScope());
                    _log.debug("log level of service {} has been set to {}", logName, level);
                }
            } catch (IllegalStateException e) {
                if (isGetReq) {
                    _log.error("Failed to get log level from service {}:", logName, e);
                } else {
                    _log.error("Failed to set log level of service {}:", logName, e);
                }
            }
        }

        return logLevels;
    }

    /**
     * Validates that the passed list specifies valid Bourne node Ids. Note that
     * an empty list is perfectly valid and means the service will process all
     * Bourne nodes in the cluster.
     *
     * @param nodeIds A list of the node ids for the Bourne nodes from which the
     *            logs are to be collected.
     * @throws APIException if the list contains an invalid node id.
     */
    private void validateNodeIds(List<String> nodeIds) {
        // Get the cluster node information and validate that there is
        // a cluster node with each of the requested ids.
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        List<NodeInfo> nodeInfoList = ClusterNodesUtil.getClusterNodeInfo();
        List<String> validNodeIds = new ArrayList<String>(nodeInfoList.size());
        for (NodeInfo node : nodeInfoList) {
            validNodeIds.add(node.getId());
        }
        List<String> nodeIdsClone = new ArrayList<String>(nodeIds);
        nodeIdsClone.removeAll(validNodeIds);
        if (!nodeIdsClone.isEmpty()) {
            throw APIException.badRequests.parameterIsNotValid("node id");
        }
    }

    /**
     * Validates that the passed list specifies valid ViPR services. Note that
     * an empty list is perfectly valid and means the service will process all
     * services on a ViPR node.
     *
     * @param logNames A list of the log names to be updated.
     * @throws APIException if the list contains an invalid node id.
     */
    private void validateNodeServices(List<String> logNames) {
        if (logNames == null || logNames.isEmpty()) {
            return;
        }
        List<String> logNamesClone = new ArrayList<String>(logNames);
        // both control and extra node services are valid service names
        logNamesClone.removeAll(ServicesMetadata.getControlNodeServiceNames());
        logNamesClone.removeAll(ServicesMetadata.getExtraNodeServiceNames());
        if (!logNamesClone.isEmpty()) {
            throw APIException.badRequests.parameterIsNotValid("log name");
        }
    }

    /**
     * Validates that the passed log scope value.
     *
     * @param scope the value of log scope
     * @return the corresponding scope level in enum
     * @throws APIException for an invalid scope value
     */
    private String validateLogScope(String scope) {
        if (scope == null) {
            return null;
        }
        String scopeLevel = LogScopeEnum.getName(scope);
        if (scopeLevel == null) {
            throw APIException.badRequests.parameterIsNotValid("log scope value:" + scope);
        }

        return scopeLevel;
    }

    /**
     * Make sure that no more than MAX_THREAD_COUNT log requests get processed concurrently
     */
    private void enforceRunningRequestLimit() {
        _log.debug("runningRequests: " + runningRequests.get());
        if (runningRequests.get() >= MAX_THREAD_COUNT) {
            _log.error("Current running requests: {} vs maximum allowed {}",
                    runningRequests, MAX_THREAD_COUNT);
            throw APIException.serviceUnavailable.logServiceIsBusy();
        }
    }
}
