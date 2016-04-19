/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import com.emc.storageos.model.valid.Endpoint;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Request PUT parameter for vCenter update operation.
 */
@XmlRootElement(name = "vcenter_update")
public class VcenterUpdateParam extends VcenterParam {

    private String ipAddress;

    public VcenterUpdateParam() {
    }

    public VcenterUpdateParam(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * The IP address or host name of the vCenter.
     * 
     */
    @XmlElement(name = "ip_address")
    @Endpoint(type = Endpoint.EndpointType.HOST)
    @JsonProperty("ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Override
    public String findIpAddress() {
        return ipAddress;
    }
}
