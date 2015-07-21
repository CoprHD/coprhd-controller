/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import com.emc.storageos.recoverpoint.impl.RecoverPointClient.RecoverPointReturnCode;

/**
 * Response to a create/update consistency group request
 * 
 */
public class RecoverPointCGResponse {
	private RecoverPointReturnCode returnCode;
	private Long cgId;

	public RecoverPointReturnCode getReturnCode() {
		return returnCode;
	}

	public void setReturnCode(RecoverPointReturnCode returnCode) {
		this.returnCode = returnCode;
	}

    public Long getCgId() {
        return cgId;
    }

    public void setCgId(Long cgId) {
        this.cgId = cgId;
    }
}
