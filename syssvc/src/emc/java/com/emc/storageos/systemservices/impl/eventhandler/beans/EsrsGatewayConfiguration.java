/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.eventhandler.beans;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "gateway_configuration")
public class EsrsGatewayConfiguration {

    private String gatewaySerialNumber;
    private String gatewayModelName;
    private String gatewayIpAddress;

    @XmlElement(name = "serial_no")
    public String getGatewaySerialNumber() {
        return gatewaySerialNumber;
    }

    public void setGatewaySerialNumber(String gatewaySerialNumber) {
        this.gatewaySerialNumber = gatewaySerialNumber;
    }

    @XmlElement(name = "model_name")
    public String getGatewayModelName() {
        return gatewayModelName;
    }

    public void setGatewayModelName(String gatewayModelName) {
        this.gatewayModelName = gatewayModelName;
    }

    @XmlElement(name = "ip_address")
    public String getGatewayIpAddress() {
        return gatewayIpAddress;
    }

    public void setGatewayIpAddress(String gatewayIpAddress) {
        this.gatewayIpAddress = gatewayIpAddress;
    }
}
