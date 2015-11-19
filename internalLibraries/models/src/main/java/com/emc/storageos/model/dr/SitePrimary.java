/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_id_list")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SitePrimary {

    private boolean isPrimary;

    public SitePrimary() {
        isPrimary = false;
    }

    @XmlElement(name = "id")
    public boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(boolean sitePrimary) {
        this.isPrimary = sitePrimary;
    }
}
