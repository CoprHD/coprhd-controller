/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;

public class BlockSettings {
    private Boolean autoSanZoning;

    @XmlElement(name = "auto_san_zoning", required = false)
    public Boolean getAutoSanZoning() {
        return autoSanZoning;
    }

    public void setAutoSanZoning(Boolean autoSanZoning) {
        this.autoSanZoning = autoSanZoning;
    }
}
