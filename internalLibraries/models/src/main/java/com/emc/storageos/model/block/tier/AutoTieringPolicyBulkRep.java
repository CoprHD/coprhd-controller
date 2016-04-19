/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block.tier;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_auto_tiering_policies")
public class AutoTieringPolicyBulkRep extends BulkRestRep {
    private List<AutoTieringPolicyRestRep> autoTierPolicies;

    /**
     * List of auto tiering policies
     * 
     * 
     */
    @XmlElement(name = "auto_tiering_policy")
    public List<AutoTieringPolicyRestRep> getAutoTierPolicies() {
        if (autoTierPolicies == null) {
            autoTierPolicies = new ArrayList<AutoTieringPolicyRestRep>();
        }
        return autoTierPolicies;
    }

    public void setAutoTierPolicies(List<AutoTieringPolicyRestRep> autoTierPolicies) {
        this.autoTierPolicies = autoTierPolicies;
    }

    public AutoTieringPolicyBulkRep() {
    }

    public AutoTieringPolicyBulkRep(List<AutoTieringPolicyRestRep> autoTierPolicies) {
        this.autoTierPolicies = autoTierPolicies;
    }
}
