/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block.tier;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "auto_tier_policies_get")
public class AutoTierPolicyParam {

    private String provisioningType;

    public AutoTierPolicyParam() {}
    
    public AutoTierPolicyParam(String provisioningType) {
        this.provisioningType = provisioningType;
    }

    /**
     * The provisioning type of the auto tiering policy
     * 
     * @valid Thin
     * @valid Thick
     * @valid All
     */
    @XmlElement(name = "provisioning_type", required = false)
    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
    }
    
}
