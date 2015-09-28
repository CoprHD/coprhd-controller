/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_config")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteConfigParam {
    private List<SiteParam> standbySites;
    private SiteParam primarySite;

    @XmlElement(name = "standby_sites")
    public List<SiteParam> getStandbySites() {
        if (standbySites == null) {
            standbySites = new ArrayList<SiteParam>();
        }
        return standbySites;
    }

    public void setStandbySites(List<SiteParam> sites) {
        this.standbySites = sites;
    }
    
    @XmlElement(name = "primary_site")
    public  SiteParam getPrimarySite() {
       return this.primarySite;
    }

    public void setPrimarySite(SiteParam site) {
        this.primarySite = site;
    }
}
