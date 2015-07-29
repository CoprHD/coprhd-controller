/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * 
 */
package com.emc.cloud.platform.ucs.model.ext;

/**
 * @author prabhj
 * 
 */
public enum OutStatus {

    SUCCESS("success"), FAILURE("failure");

    String value;

    OutStatus(String enumText) {
        value = enumText;
    }

    public String getValue() {
        return value;
    }

}
