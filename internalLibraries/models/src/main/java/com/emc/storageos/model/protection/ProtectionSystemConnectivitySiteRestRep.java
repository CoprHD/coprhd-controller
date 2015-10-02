/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.protection;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.*;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "protection_connectivity_site")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ProtectionSystemConnectivitySiteRestRep {
    private String siteID;
    private List<RelatedResourceRep> storageSystems;

    public ProtectionSystemConnectivitySiteRestRep() {
    }

    public ProtectionSystemConnectivitySiteRestRep(String siteID,
            List<RelatedResourceRep> storageSystems) {
        this.siteID = siteID;
        this.storageSystems = storageSystems;
    }

    /**
     * The Site ID
     * 
     */
    @XmlElement(name = "site_id")
    public String getSiteID() {
        return siteID;
    }

    public void setSiteID(String siteID) {
        this.siteID = siteID;
    }

    /**
     * The Storage Systems associated to with this Site
     * 
     */
    @XmlElementWrapper(name = "storage_systems")
    @XmlElement(name = "storage_system")
    public List<RelatedResourceRep> getStorageSystems() {
        if (storageSystems == null) {
            storageSystems = new ArrayList<RelatedResourceRep>();
        }
        return storageSystems;
    }

    public void setStorageSystems(List<RelatedResourceRep> storageSystems) {
        this.storageSystems = storageSystems;
    }
}
