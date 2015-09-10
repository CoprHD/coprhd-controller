/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.tier;

import com.emc.storageos.model.NamedRelatedResourceRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@XmlRootElement(name = "auto_tier_policies")
public class AutoTierPolicyList {
    private List<NamedRelatedResourceRep> autoTierPolicies;

    public AutoTierPolicyList() {
    }

    public AutoTierPolicyList(List<NamedRelatedResourceRep> autoTierPolicies) {
        this.autoTierPolicies = autoTierPolicies;
    }

    /**
     * The list of auto tiering policies
     * 
     * @valid none
     */
    @XmlElement(name = "auto_tier_policy")
    public List<NamedRelatedResourceRep> getAutoTierPolicies() {
        if (autoTierPolicies == null) {
            autoTierPolicies = new ArrayList<NamedRelatedResourceRep>();
        }
        return autoTierPolicies;
    }

    public void setAutoTierPolicies(List<NamedRelatedResourceRep> autoTierPolicies) {
        this.autoTierPolicies = autoTierPolicies;
    }

    public boolean containsPolicy(String policyName) {
        Iterator<NamedRelatedResourceRep> iterator = getAutoTierPolicies().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getName().equals(policyName)) {
                return true;
            }
        }
        return false;
    }
}
