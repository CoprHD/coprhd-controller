/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

public class ClientResponseException extends ClientGeneralException {
    // Bourne error response fields according to API on August 13, 2012:
    String btype;        // Identifies the error type; useful for classifying the error into a category
    String bcode;        // An integer error code that is associated with the type of error
    String bmessage;     // Human readable synopsis of the error.
    String brequest;     // Request operation that cuased the error
    String bpayload;     // Request payload that cause the error

    public ClientResponseException(Integer errorCode, String message, Throwable cause) {
        super(ClientMessageKeys.byErrorCode(errorCode), cause);
    }

    public ClientResponseException(Integer errorCode, String message) {
        super(ClientMessageKeys.byErrorCode(errorCode));
    }

    public ClientResponseException(ClientHttpResponseException ex) {
        super(ClientMessageKeys.byErrorCode(ex.getStatusCode()), new String[] { ex.brequest, ex.bpayload, ex.bmessage }, ex.getCause());
        this.btype = ex.btype;
        this.bcode = ex.bcode;
        this.bmessage = ex.bmessage;
        this.brequest = ex.brequest;
        this.bpayload = ex.bpayload;
    }

    public String getBtype() {
        return btype;
    }

    public String getBcode() {
        return bcode;
    }

    public String getBmessage() {
        return bmessage;
    }

    public String getBrequest() {
        return brequest;
    }

    public String getBpayload() {
        return bpayload;
    }
}
