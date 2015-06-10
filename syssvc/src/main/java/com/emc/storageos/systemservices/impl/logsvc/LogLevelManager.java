/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.*;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.resource.util.ClusterNodesUtil;
import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import com.emc.vipr.model.sys.logging.LogLevelRequest;
import com.emc.vipr.model.sys.logging.LogLevels;
import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Class for handling a log request by setting and updating the log levels on
 * all nodes based on the parameters of a specific log request.
 */
public class LogLevelManager extends BaseLogManager {
    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(LogLevelManager.class);

    /**
     * Constructor.
     *
     * @param propertiesLoader A reference to the configurable properties loader.
     * @throws APIException When a null parameter is passed.
     */
    public LogLevelManager(LogLevelRequest logReqInfo, MediaType mediaType,
                                   LogSvcPropertiesLoader propertiesLoader) {
        super(logReqInfo, mediaType, propertiesLoader);
    }

    /**
     *
     * @throws APIException When an error occurs satisfying the log request.
     */
    public LogLevels process() {
        List<NodeInfo> nodeInfo;
        int expirInMin;

        //Getting all nodes information
        if (_logReqInfo.getNodeIds().isEmpty()) {
            _log.debug("No nodes specified, assuming all nodes");
            nodeInfo = ClusterNodesUtil.getClusterNodeInfo();
        } else {
            nodeInfo = getClusterNodesWithIds(_logReqInfo.getNodeIds());
        }
        if (nodeInfo.isEmpty()) {
            throw APIException.internalServerErrors.noNodeAvailableError("update log levels");
        }
         
        LogLevelRequest logLevelReq = (LogLevelRequest)_logReqInfo;
        if (logLevelReq.getExpirInMin() == null) {
            _log.debug("No expiration specified, asuming default value");
            expirInMin = _propertiesLoader.getLogLevelExpiration();
        } else {
            expirInMin = logLevelReq.getExpirInMin();
        }

        //we will handle the empty logNames list inside the internal log level API.

        return propagate(nodeInfo, _logReqInfo.getLogNames(), _logReqInfo.getSeverity(), 
                expirInMin, logLevelReq.getScope());
    }

    // Building the internal log URI for each node and calling using client
    // Collecting streams from all nodes - if any node does not send response,
    // logging error.
    private LogLevels propagate(List<NodeInfo> nodeInfos,
            List<String> logNames, LogSeverity severity, int expirInMin, String scope) {
        LogLevels nodeLogLevels = new LogLevels();
        for (final NodeInfo node : nodeInfos) {
            String baseNodeURL = String.format(BASE_URL_FORMAT, node.getIpAddress(),
                    node.getPort());
            _log.debug("processing node: " + baseNodeURL);
            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(
                    URI.create(baseNodeURL),
                    _propertiesLoader.getNodeLogCollectorTimeout() * 1000,
                    _propertiesLoader.getNodeLogConnectionTimeout() * 1000);
            try {
                LogLevelRequest nodeLogReqInfo = new LogLevelRequest(new ArrayList<String>() {{
                        add(node.getId());
                    }}, logNames, severity, expirInMin, scope);
                LogLevels nodeResp = sysClient.post(SysClientFactory.URI_LOG_LEVELS,
                        LogLevels.class, nodeLogReqInfo);
                nodeLogLevels.getLogLevels().addAll(nodeResp.getLogLevels());
            } catch (Exception e) {
                _log.error("Exception accessing node {}:", baseNodeURL, e);
            }
        }
        return nodeLogLevels;
    }
}
