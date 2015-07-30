/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.exceptions;

public class TimeoutException extends ViPRException {
    public TimeoutException() {
    }

    public TimeoutException(String s) {
        super(s);
    }

    public TimeoutException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public TimeoutException(Throwable throwable) {
        super(throwable);
    }
}
