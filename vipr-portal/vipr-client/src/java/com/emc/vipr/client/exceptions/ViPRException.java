/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.exceptions;

public class ViPRException extends RuntimeException {
    public ViPRException() {
    }

    public ViPRException(String s) {
        super(s);
    }

    public ViPRException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ViPRException(Throwable throwable) {
        super(throwable);
    }
}
