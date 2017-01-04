/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jainm15
 */

@XmlRootElement(name = "file_policy_create")
public class FilePolicyCreateParam extends FilePolicyParam {

    private static final long serialVersionUID = 1L;
    // Type of the policy
    private String policyType;

    /**
     * Type of the policy,
     * valid values are : file_snapshot, file_replication, file_quota
     * 
     * @return
     */
    @XmlElement(required = true, name = "policy_type")
    public String getPolicyType() {
        return this.policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }
}
