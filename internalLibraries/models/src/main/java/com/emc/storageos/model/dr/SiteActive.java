/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_is_active")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteActive {

    private boolean isActiveSite;
    private String siteName;

    public SiteActive() {
        isActiveSite = false;
    }

    @XmlElement(name = "is_active")
    public boolean getIsActive() {
        return isActiveSite;
    }

    public void setIsActive(boolean ActiveSite) {
        this.isActiveSite = ActiveSite;
    }
    
    @XmlElement(name = "site_name")
    public String getSiteName() {
        return this.siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }
}
