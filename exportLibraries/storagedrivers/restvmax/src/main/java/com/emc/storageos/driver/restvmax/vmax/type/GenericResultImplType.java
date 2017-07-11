/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class GenericResultImplType extends ResultType {
    public Boolean getSuccess() {
        return success;
    }

    private Boolean success;

    public String getMessage() {
        return message;
    }

    private String message;

    public GenericResultImplType(Boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
