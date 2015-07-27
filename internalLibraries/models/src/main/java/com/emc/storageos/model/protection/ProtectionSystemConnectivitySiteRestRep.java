/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public ProtectionSystemConnectivitySiteRestRep() {}
    
    public ProtectionSystemConnectivitySiteRestRep(String siteID,
            List<RelatedResourceRep> storageSystems) {
        this.siteID = siteID;
        this.storageSystems = storageSystems;
    }

    /**
     * The Site ID
     * @valid example: lrmb017
     */
    @XmlElement(name="site_id")
    public String getSiteID() {
        return siteID;
    }

    public void setSiteID(String siteID) {
        this.siteID = siteID;
    }

    /**
     * The Storage Systems associated to with this Site
     * @valid 0 or more Storage Systems
     * @valid example: urn:storageos:StorageSystem:f468a1c5-3fe8-4aa9-886f-abc6c6963ebc:
     */
    @XmlElementWrapper(name="storage_systems")
    @XmlElement(name="storage_system")
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
