/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;

/**
 * Parameters required to update a Consistency Group policy.
 */
@SuppressWarnings("serial")
public class UpdateCGPolicyParams implements Serializable {
    // CG name
    private String cgName;
    private CGPolicyParams policyParams;

    public UpdateCGPolicyParams() {
    }

    public UpdateCGPolicyParams(String cgName, CGPolicyParams policyParams) {
        this.cgName = cgName;
        this.policyParams = policyParams;
    }

    public String getCgName() {
        return cgName;
    }

    public void setCgName(String cgName) {
        this.cgName = cgName;
    }

    public CGPolicyParams getPolicyParams() {
        return policyParams;
    }

    public void setPolicyParams(CGPolicyParams policyParams) {
        this.policyParams = policyParams;
    }
}
