/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;

public class VirtualArrayParam {
    private BlockSettings blockSettings;
    private ObjectSettings objectSettings;

    public VirtualArrayParam() {}

    @XmlElement(name="block_settings")
    public BlockSettings getBlockSettings() {
        if (blockSettings == null) {
            blockSettings = new BlockSettings();
        }
        return blockSettings;
    }
    public void setBlockSettings(BlockSettings blockSettings) {
        this.blockSettings = blockSettings;
    }

    @XmlElement(name="object_settings")
    public ObjectSettings getObjectSettings() {
        if (objectSettings == null) {
            objectSettings = new ObjectSettings();
        }
        return objectSettings;
    }
    public void setObjectSettings(ObjectSettings objectSettings) {
        this.objectSettings = objectSettings;
    }

    public VirtualArrayParam(Boolean autoSanZoning) {
        getBlockSettings().setAutoSanZoning(autoSanZoning);
    }

    /**
     * Specifies whether or not zoning is automatic for the virtual array.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name="auto_san_zoning", required = false)
    @Deprecated
    public Boolean getAutoSanZoning() {
        return getBlockSettings().getAutoSanZoning();
    }
    @Deprecated
    public void setAutoSanZoning(Boolean autoSanZoning) {
        getBlockSettings().setAutoSanZoning(autoSanZoning);
    }

    @XmlElement(name="device_registered", required = false)
    @Deprecated
    public Boolean getDeviceRegistered() {
        return getObjectSettings().getDeviceRegistered();
    }

    public void setDeviceRegistered(Boolean deviceRegistered) {
        getObjectSettings().setDeviceRegistered(deviceRegistered);
    }

    /**
     * varray protection type
     */
    @XmlElement(name="protection_type", required = false)
    @Deprecated
    public String getProtectionType() {
        return getObjectSettings().getProtectionType();
    }

    public void setProtectionType(String protectionType) {
        getObjectSettings().setProtectionType(protectionType);
    }
}
