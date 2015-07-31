/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;

public class ObjectSettings {
    private Boolean deviceRegistered;
    private String protectionType;

    /**
     * if device is registered
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "device_registered")
    public Boolean getDeviceRegistered() {
        return deviceRegistered;
    }

    public void setDeviceRegistered(Boolean deviceRegistered) {
        this.deviceRegistered = deviceRegistered;
    }

    /**
     * varray protection type
     */
    @XmlElement(name = "protection_type")
    public String getProtectionType() {
        return protectionType;
    }

    public void setProtectionType(String protectionType) {
        this.protectionType = protectionType;
    }
}
