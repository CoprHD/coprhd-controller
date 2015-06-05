/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.engine.inject;

public class InjectorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InjectorException(String message) {
        super(message);
    }

    public InjectorException(Throwable cause) {
        super(cause);
    }

    public InjectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
