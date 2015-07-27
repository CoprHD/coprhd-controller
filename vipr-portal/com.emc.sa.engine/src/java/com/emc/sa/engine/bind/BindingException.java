/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.bind;

public class BindingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BindingException(String message) {
        super(message);
    }

    public BindingException(Throwable cause) {
        super(cause);
    }

    public BindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
