/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class GenericResultImplType extends ResultType {

    // min/max occurs: 1/1
    private Boolean success;
    // min/max occurs: 0/1
    private String message;

    public Boolean getSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
