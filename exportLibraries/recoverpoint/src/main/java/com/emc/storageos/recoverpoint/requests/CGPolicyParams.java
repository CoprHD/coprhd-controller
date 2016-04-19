/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;

/**
 * CG policy parameters.
 */
@SuppressWarnings("serial")
public class CGPolicyParams implements Serializable {
    private String copyMode;
    private Long rpoValue;
    private String rpoType;

    public CGPolicyParams() {
    }

    public CGPolicyParams(String copyMode) {
        this.copyMode = copyMode;
    }

    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
    }

    public Long getRpoValue() {
        return rpoValue;
    }

    public void setRpoValue(Long rpoValue) {
    	if (null != rpoValue) {
    		this.rpoValue = rpoValue;
    	}
    }

    public String getRpoType() {
        return rpoType;
    }

    public void setRpoType(String rpoType) {
        this.rpoType = rpoType;
    }
}
