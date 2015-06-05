/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.engine;

public class RollbackException extends ExecutionException {
    private static final long serialVersionUID = 1L;

    public RollbackException(Throwable cause) {
        super(cause);
    }
}
