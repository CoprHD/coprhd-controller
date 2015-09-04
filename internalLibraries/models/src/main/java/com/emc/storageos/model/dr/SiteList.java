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

    private List<NamedRelatedResourceRep> sites;

    public SiteList() {
    }

    public SiteList(List<NamedRelatedResourceRep> standbys) {
        this.sites = standbys;
    }

    @XmlElement(name = "sites")
    public List<NamedRelatedResourceRep> getSites() {
        if (sites == null) {
            sites = new ArrayList<NamedRelatedResourceRep>();
        }
        return sites;
    }

    public void setSites(List<NamedRelatedResourceRep> sites) {
        this.sites = sites;
    }
}
