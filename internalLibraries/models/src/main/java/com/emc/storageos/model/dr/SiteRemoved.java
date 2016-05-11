/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_is_removed")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteRemoved {
    private boolean isRemoved;

    public SiteRemoved() {
        isRemoved = false;
    }

    @XmlElement(name = "is_removed")
    public boolean getIsRemoved() {
        return isRemoved;
    }

    public void setIsRemoved(boolean isRemoved) {
        this.isRemoved = isRemoved;
    }
}
