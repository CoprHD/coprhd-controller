/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_is_primary")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SitePrimary {

    private boolean isPrimary;

    public SitePrimary() {
        isPrimary = false;
    }

    @XmlElement(name = "isPrimary")
    public boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(boolean sitePrimary) {
        this.isPrimary = sitePrimary;
    }
}
