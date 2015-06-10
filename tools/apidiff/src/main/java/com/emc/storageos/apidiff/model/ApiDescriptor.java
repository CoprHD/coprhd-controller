/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff.model;

import java.util.ArrayList;
import java.util.List;

/**
 * This class includes request parameters, body and response body. They represent the details of one
 * API. The API is treated "changed" if one of them is changed(add, remove, rename).
 */
public class ApiDescriptor {

    private List<String> parameters;
    private String requestElement;
    private String responseElement;

    public ApiDescriptor() {
        this(null, null, null);
    }

    public ApiDescriptor(String requestElement, String responseElement) {
        this(null, requestElement, responseElement);
    }

    public ApiDescriptor(List<String> parameters, String requestElement, String responseElement) {
        if (parameters == null)
            this.parameters = new ArrayList<String>();
        else
            this.parameters = parameters;
        this.requestElement = requestElement;
        this.responseElement = responseElement;
    }

    public String getRequestElement() {
        return requestElement;
    }

    public String getResponseElement() {
        return responseElement;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setRequestElement(String requestElement) {
        this.requestElement = requestElement;
    }

    public void setResponseElement(String responseElement) {
        this.responseElement = responseElement;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getSimpleName()).append(" [ ");
        builder.append("parameters: ");
        for (String param : parameters) {
            builder.append(param).append(" ");
        }
        builder.append("\n");
        builder.append("requestElement: ").append(requestElement).append("\n");
        builder.append("responseElement: ").append(responseElement).append("]");

        return builder.toString();
    }
}
