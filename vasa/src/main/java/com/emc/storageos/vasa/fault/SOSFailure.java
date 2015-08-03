/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.fault;

public class SOSFailure extends Exception {

    private static final long serialVersionUID = 1L;

    public SOSFailure() {

    }

    public SOSFailure(String message) {
        super(message);
    }

    public SOSFailure(String message, Throwable cause) {
        super(message, cause);
    }

    public SOSFailure(Throwable cause) {
        super(cause);
    }
}
