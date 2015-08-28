/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.eventhandler;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "device")
public class Device {
    protected String serialNumber;
    protected String modelName;
    protected String ipAddress;

    @XmlElement(name = "serial-no")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String value) {
        this.serialNumber = value;
    }

    @XmlElement(name = "model-name")
    public String getModelName() {
        return modelName;
    }

    public void setModelName(String value) {
        this.modelName = value;
    }

    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String value) {
        this.ipAddress = value;
    }
}
