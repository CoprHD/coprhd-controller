/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.exceptions;

public class CompatibilityException extends RuntimeException {
    private static final long serialVersionUID = -9060220365718562909L;

    public CompatibilityException(String message) {
        super(message);
    }

    public CompatibilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
