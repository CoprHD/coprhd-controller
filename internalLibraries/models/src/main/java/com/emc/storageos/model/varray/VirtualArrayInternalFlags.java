/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "varray_internal_flags")
public class VirtualArrayInternalFlags {
    private String protectionType;
    private Boolean deviceRegistered;

    public VirtualArrayInternalFlags() {
    }

    @XmlElement(name = "protectionType")
    public String getProtectionType() {
        return protectionType;
    }

    public void setProtectionType(String protectionType) {
        this.protectionType = protectionType;
    }

    @XmlElement(name = "deviceRegistered")
    public Boolean getDeviceRegistered() {
        return deviceRegistered;
    }

    public void setDeviceRegistered(Boolean deviceRegistered) {
        this.deviceRegistered = deviceRegistered;
    }

}
