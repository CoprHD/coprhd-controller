/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource.util;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeInfo {

    // The node id
    private String _nodeId;

    // The node name
    private String _nodeName;

    // The node IP address.
    private String _ipAddress;

    // The node port.
    private int _port;

    // Logger reference.
    protected static final Logger _log = LoggerFactory.getLogger(NodeInfo.class);

    /**
     * Constructor.
     * 
     * @param nodeId The id of the node.
     * @throws Exception If the passed connection info is not valid.
     */
    public NodeInfo(String nodeId, String nodeName, URI endPointURI) throws Exception {
        _nodeId = nodeId;
        _nodeName = nodeName;
        _log.debug("Creating node info or node {}", _nodeId);
        _ipAddress = endPointURI.getHost();
        _log.debug("Node IP address is {}", _ipAddress);
        _port = endPointURI.getPort();
        _log.debug("Node port is {}", _port);
    }

    /**
     * Getter for the cluster node id.
     * 
     * @return The cluster node id.
     */
    public String getId() {
        return _nodeId;
    }

    /**
     * Getter for the cluster node name.
     *
     * @return The cluster node name.
     */
    public String getName() {
        return _nodeName;
    }

    /**
     * Getter for the cluster node IP address.
     * 
     * @return The cluster node IP address.
     */
    public String getIpAddress() {
        return _ipAddress;
    }

    /**
     * Getter for the cluster node port.
     * 
     * @return The cluster node port.
     */
    public int getPort() {
        return _port;
    }
}
