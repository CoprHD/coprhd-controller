/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Endpoint;

/**
 * Request POST parameter for Management station creation.
 */
@XmlRootElement(name = "management_station_create")
public class ManagementStationCreateParam extends ManagementStationParam {

    private String ipAddress;

    public ManagementStationCreateParam() {
    }

    public ManagementStationCreateParam(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * The IP address or host name of the vCenter.
     * 
     */
    @XmlElement(name = "ip_address", required = true)
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
