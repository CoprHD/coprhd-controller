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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;


@XmlRootElement(name = "varray")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualArrayRestRep extends DataObjectRestRep {
    private BlockSettings blockSettings;
    private ObjectSettings objectSettings;

    public VirtualArrayRestRep() {}

    @XmlElement(name="block_settings")
    public BlockSettings getBlockSettings() {
        return blockSettings;
    }
    public void setBlockSettings(BlockSettings blockSettings) {
        this.blockSettings = blockSettings;
    }

    @XmlElement(name="object_settings")
    public ObjectSettings getObjectSettings() {
        return objectSettings;
    }
    public void setObjectSettings(ObjectSettings objectSettings) {
        this.objectSettings = objectSettings;
    }

    /**
     * ViPR creates the required zones in the SAN fabric
     * when a request to export a volume is made in this
     * virtualstorage array. This will allow the exported
     * volume to be visible on the specified hosts.
     *
     * @valid true
     * @valid false
     */
    @XmlElement(name="auto_san_zoning")
    @Deprecated
    public Boolean getAutoSanZoning() {
        if (blockSettings != null) {
            return blockSettings.getAutoSanZoning();
        } else {
            return null;
        }
    }

    @Deprecated
    public void setAutoSanZoning(Boolean autoSanZoning) {
        if (blockSettings == null) {
            blockSettings = new BlockSettings();
        }
        blockSettings.setAutoSanZoning(autoSanZoning);
    }
}
