/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.exceptions;

/**
 * Created by bibbyi1 on 4/24/2015.
 */
public class NoSynchronizationsFoundException extends RuntimeException {
    public NoSynchronizationsFoundException(String message) {
        super(message);
    }
}
