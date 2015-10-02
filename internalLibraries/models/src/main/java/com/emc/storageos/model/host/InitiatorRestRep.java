/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * REST Response representing an Initiator.
 */
@XmlRootElement(name = "initiator")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class InitiatorRestRep extends HostInterfaceRestRep {
    private String hostName;
    private String initiatorNode;
    private String initiatorPort;
    private String clusterName;
    private String label;

    public InitiatorRestRep() {
    }

    public InitiatorRestRep(String hostName, String initiatorNode,
            String initiatorPort, String clusterName, String label) {
        this.hostName = hostName;
        this.initiatorNode = initiatorNode;
        this.initiatorPort = initiatorPort;
        this.clusterName = clusterName;
        this.label = label;
    }

    /**
     * The host name for the initiator.
     * 
     * @return The initiator host name.
     */
    @XmlElement(name = "hostname")
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * The name of the cluster for the initiator.
     * 
     * @return The initiator cluster name.
     */
    @XmlElement(name = "clustername")
    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * The initiator node.
     * 
     * @return The initiator node.
     */
    @XmlElement(name = "initiator_node")
    public String getInitiatorNode() {
        return initiatorNode;
    }

    public void setInitiatorNode(String initiatorNode) {
        this.initiatorNode = initiatorNode;
    }

    /**
     * The port for the initiator.
     * 
     * @return The initiator port.
     */
    @XmlElement(name = "initiator_port")
    public String getInitiatorPort() {
        return initiatorPort;
    }

    public void setInitiatorPort(String initiatorPort) {
        this.initiatorPort = initiatorPort;
    }

    /**
     * The label for the initiator.
     * 
     * @return The initiator label.
     */
    @XmlElement(name = "label")
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
