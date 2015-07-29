/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.apidiff.model;

import com.emc.storageos.apidiff.util.Pair;

/**
 * This class records differences of two API, including request parameter, body and response body.
 * 
 */
public class ApiDescriptorDiff {

    private Pair<String, String> paramDiff;
    private Pair<String, String> requestElementDiff;
    private Pair<String, String> responseElementDiff;

    public ApiDescriptorDiff() {
    }

    public ApiDescriptorDiff(Pair<String, String> paramDiff,
            Pair<String, String> requestElementDiff,
            Pair<String, String> responseElementDiff) {
        this.paramDiff = paramDiff;
        this.requestElementDiff = requestElementDiff;
        this.responseElementDiff = responseElementDiff;
    }

    public Pair<String, String> getParamDiff() {
        return paramDiff;
    }

    public Pair<String, String> getRequestElementDiff() {
        return requestElementDiff;
    }

    public Pair<String, String> getResponseElementDiff() {
        return responseElementDiff;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getSimpleName()).append(" [ ");
        if (paramDiff != null) {
            builder.append("paramDiff: ");
            builder.append(paramDiff);
            builder.append("; ");
        }
        if (requestElementDiff != null) {
            builder.append("requestElementDiff: ");
            builder.append(requestElementDiff);
            builder.append("; ");
        }
        if (responseElementDiff != null) {
            builder.append("responseElementDiff: ");
            builder.append(responseElementDiff);
        }
        builder.append("]");

        return builder.toString();
    }
}
