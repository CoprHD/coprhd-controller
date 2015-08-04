/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

/**
 * Internal exception when db data is being marshaled and failed.
 * 
 */
public class MarshallingExcetion extends Exception {

    private static final long serialVersionUID = 1L;

    public MarshallingExcetion(String string, Exception e) {
        super(string, e);
    }
}
