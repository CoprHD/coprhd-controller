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

@XmlRootElement(name = "bulk_protection_sets")
public class ProtectionSetBulkRep extends BulkRestRep {
    private List<ProtectionSetRestRep> protectionSets;

    /**
     * List of Protection Sets. Not currently being used.
     * @valid none - not currently implemented
     */
    @XmlElement(name = "protection_set")
    public List<ProtectionSetRestRep> getProtectionSets() {
        if (protectionSets == null) {
            protectionSets = new ArrayList<ProtectionSetRestRep>();
        }
        return protectionSets;
    }

    public void setProtectionSets(List<ProtectionSetRestRep> protectionSets) {
        this.protectionSets = protectionSets;
    }

    public ProtectionSetBulkRep() {
    }

    public ProtectionSetBulkRep(List<ProtectionSetRestRep> protectionSets) {
        this.protectionSets = protectionSets;
    }
}
