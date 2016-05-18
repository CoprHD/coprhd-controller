/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "sites")
public class SiteList {

    private List<SiteRestRep> sites;
    private Long configVersion;

    public SiteList() {
    }

    public SiteList(List<SiteRestRep> standbys) {
        this.sites = standbys;
    }

    @XmlElement(name = "site")
    public List<SiteRestRep> getSites() {
        if (sites == null) {
            sites = new ArrayList<SiteRestRep>();
        }
        return sites;
    }

    public void setSites(List<SiteRestRep> sites) {
        this.sites = sites;
    }

    @XmlElement(name = "config_version")
    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }
    
}
