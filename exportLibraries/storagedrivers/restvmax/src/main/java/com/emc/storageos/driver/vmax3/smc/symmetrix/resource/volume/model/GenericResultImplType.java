/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractResponse;

public class GenericResultImplType extends AbstractResponse {
    public Boolean getSuccess() {
        return success;
    }

    private Boolean success;

    @Override
    public String getMessage() {
        return message;
    }

    private String message;

    public GenericResultImplType(Boolean success) {
        this.success = success;
    }

    @Override
    public void setMessage(String message) {
        this.message = message;
    }
}
