/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.protection;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_protection_systems")
public class ProtectionSystemBulkRep extends BulkRestRep {
    private List<ProtectionSystemRestRep> protectionSystems;

    /**
     * List of Protection Systems.
     * @valid 0 or more Protection System IDs
     * @valid example: urn:storageos:ProtectionSystem:4379693c-c2f9-4e8e-ac4f-c67789cf1934:
     */
    @XmlElement(name = "protection_system")
    public List<ProtectionSystemRestRep> getProtectionSystems() {
        if (protectionSystems == null) {
            protectionSystems = new ArrayList<ProtectionSystemRestRep>();
        }
        return protectionSystems;
    }

    public void setProtectionSystems(List<ProtectionSystemRestRep> protectionSystems) {
        this.protectionSystems = protectionSystems;
    }

    public ProtectionSystemBulkRep() {
    }

    public ProtectionSystemBulkRep(List<ProtectionSystemRestRep> protectionSystems) {
        this.protectionSystems = protectionSystems;
    }
}
