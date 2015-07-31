/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.exceptions;

public class ViPRHttpException extends ViPRException {
    private final int httpCode;

    public ViPRHttpException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    public ViPRHttpException(int httpCode) {
        super();
        this.httpCode = httpCode;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public boolean isServiceUnavailable() {
        return httpCode == 503;
    }
}
