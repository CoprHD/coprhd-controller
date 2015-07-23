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

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.resource.util.ClusterNodesUtil;
import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import com.emc.vipr.model.sys.logging.LogRequestBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * Class for handling a log request by setting and updating the log levels on
 * all nodes based on the parameters of a specific log request.
 */
public abstract class BaseLogManager {
    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(BaseLogManager.class);

    // Local URL format for internal nodes
    protected static final String BASE_URL_FORMAT = "http://%1$s:%2$s";

    // A reference to the configurable properties loader.
    protected LogSvcPropertiesLoader _propertiesLoader;

    //Data specifying the parameters of the log request.
    protected LogRequestBase _logReqInfo;

    //Request media type which is used to decide response media type.
    protected MediaType _mediaType;

    /**
     * Constructor.
     *
     * @param propertiesLoader A reference to the configurable properties loader.
     * @throws APIException When a null parameter is passed.
     */
    public BaseLogManager(LogRequestBase logReqInfo, MediaType mediaType,
                                   LogSvcPropertiesLoader propertiesLoader) {
        _logReqInfo = logReqInfo;
        _mediaType = mediaType;
        _propertiesLoader = propertiesLoader;
        if (_propertiesLoader == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("PropertiesLoader");
        }
    }

    /**
     * Gets a reference to the node info for the nodes in the Bourne cluster
     * with the passed nodes identifiers.
     *
     * @param nodeIds The ids of the desired cluster nodes.
     * @return A list containing the connection info for the desired nodes.
     * @throws APIException When an exception occurs trying to get the
     *                               cluster nodes.
     */
    protected List<NodeInfo> getClusterNodesWithIds(List<String> nodeIds) {
        List<NodeInfo> matchingNodes = new ArrayList<NodeInfo>();
        List<NodeInfo> nodeInfoList = ClusterNodesUtil.getClusterNodeInfo();
        for (NodeInfo node : nodeInfoList) {
            if (nodeIds.contains(node.getId())) {
                matchingNodes.add(node);
            }
        }
        return matchingNodes;
    }
}
