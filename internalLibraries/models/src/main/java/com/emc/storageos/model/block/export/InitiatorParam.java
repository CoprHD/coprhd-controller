/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import javax.xml.bind.annotation.XmlElement;

/**
 * Captures POST data for an initiator.
 */
public class InitiatorParam {

    private String hostName;
    private String node;
    private String port;
    private String protocol;
    private String clusterName;

    public InitiatorParam() {
    }

    public InitiatorParam(String hostName, String node, String port,
            String protocol, String clusterName) {
        this.hostName = hostName;
        this.node = node;
        this.port = port;
        this.protocol = protocol;
        this.clusterName = clusterName;
    }

    /**
     * The host name of the initiator
     * 
     * @valid none
     */
    @XmlElement(name = "hostname", required = true)
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * The FC initiator WWN of the initiator node
     * 
     * @valid none
     */
    @XmlElement(name = "initiator_node", required = false)
    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    /**
     * The initiator port which can be the WWN of an FC port or the IQN or EUI of an iSCSI port
     * 
     * @valid none
     */
    @XmlElement(name = "initiator_port", required = true)
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /**
     * The protocols supported by the initiator which should be FC or iSCSI
     * 
     * @valid none
     */
    @XmlElement(name = "protocol", required = true)
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The name of the initiator's cluster
     * 
     * @valid none
     */
    @XmlElement(name = "clustername", required = false)
    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
}
