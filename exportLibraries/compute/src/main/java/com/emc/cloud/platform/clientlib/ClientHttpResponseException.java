/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import org.apache.http.client.HttpResponseException;

class ClientHttpResponseException extends HttpResponseException {
    // Bourne error respopnse fields according to API on August 13, 2012:
    public String btype;        // Identifies the error type; useful for classifying the error into a category
    public String bcode;        // An integer error code that is associated with the type of error
    public String bmessage;     // Human readable synopsis of the error.
    public String brequest;     // Request operation that caused the error
    public String bpayload;     // Request payload that cause the error

    public ClientHttpResponseException(Integer errorCode, String message) {
        super(errorCode, message);        // no detailed Bourne information available
    }

    public ClientHttpResponseException(Integer errorCode, String message,
            String btype, String bcode, String bmessage, String brequest, String bpayload) {
        super(errorCode, message);
        this.btype = btype;
        this.bcode = bcode;
        this.bmessage = bmessage;
        this.brequest = brequest;
        this.bpayload = bpayload;
    }

    public ClientHttpResponseException(Integer errorCode, String message,
            String brequest, String bpayload) {
        super(errorCode, message);
        this.btype = "unspecified";
        this.bcode = errorCode.toString();
        this.bmessage = message;
        this.brequest = brequest;
        this.bpayload = bpayload;
    }
}
