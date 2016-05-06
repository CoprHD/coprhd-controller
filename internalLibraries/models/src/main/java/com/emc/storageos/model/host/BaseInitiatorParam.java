/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.valid.Endpoint;
import javax.xml.bind.annotation.XmlElement;

/**
 * Captures POST data for a host initiator.
 */
public class BaseInitiatorParam {

    private String protocol;
    private String node;
    private String port;
    private String name;

    public BaseInitiatorParam() {
    }

    public BaseInitiatorParam(String protocol, String node, String port, String name) {
        this.protocol = protocol;
        this.node = node;
        this.port = port;
        this.name = name;
    }

    /**
     * The protocols supported by the initiator which should be FC or iSCSI
     * Valid values:
     *   FC
     *   iSCSI
     */
    // @EnumType(HostInterface.Protocol.class)
    @XmlElement()
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The FC (Fibre Channel) initiator WWN (World Wide Name) of the initiator node
     * 
     */
    @XmlElement(name = "initiator_node")
    @Endpoint(type = Endpoint.EndpointType.SAN)
    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    /**
     * The initiator port which can be the WWN of an FC port or the IQN or EUI of an iSCSI port
     * 
     */
    @XmlElement(name = "initiator_port", required = true)
    @Endpoint(type = Endpoint.EndpointType.SAN)
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /**
     * The label of the initiator
     * 
     */
    @XmlElement()
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
