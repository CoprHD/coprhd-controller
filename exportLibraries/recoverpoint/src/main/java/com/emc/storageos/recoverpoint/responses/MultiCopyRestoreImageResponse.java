/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import com.emc.storageos.recoverpoint.impl.RecoverPointClient.RecoverPointReturnCode;

public class MultiCopyRestoreImageResponse {
    private RecoverPointReturnCode returnCode;

    public RecoverPointReturnCode getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(RecoverPointReturnCode returnCode) {
        this.returnCode = returnCode;
    }
}
