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
 **/
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
