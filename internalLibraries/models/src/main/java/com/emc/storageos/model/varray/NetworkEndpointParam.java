/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameter used to update the endpoints of a network.
 * 
 * @deprecated use the general API for network update
 * 
 */
@XmlRootElement(name = "network_endpoints")
@Deprecated
public class NetworkEndpointParam {
    private List<String> endpoints;
    private String op;

    public NetworkEndpointParam() {
    }

    public NetworkEndpointParam(List<String> endpoints, String op) {
        this.endpoints = endpoints;
        this.op = op;
    }

    /**
     * List of endpoints (WWN, iqn, IP address of port and host interfaces)
     * to be added to the network or removed from it.
     * 
     */
    @XmlElementWrapper(required = true, name = "endpoints")
    @XmlElement(name = "endpoint")
    public List<String> getEndpoints() {
        if (endpoints == null) {
            endpoints = new ArrayList<String>();
        }
        return endpoints;
    }

    public void setEndpoints(List<String> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * Operation to add or remove endpoints from the network.
     * 
     */
    @XmlElement(required = true)
    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public static enum EndpointOp {
        add,
        remove,
    }
}
