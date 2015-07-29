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

/**
 * This class contains API URL path and HTTP method as identifier of one API. The API is different if
 * httpMethod or path is different.
 */
public class ApiIdentifier {

    private final String path;
    private final String httpMethod;

    public ApiIdentifier(String httpMethod, String path) {
        this.path = path;
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + ((httpMethod == null) ? 0 : httpMethod.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object apiIdentifier) {
        if (this == apiIdentifier) {
            return true;
        }
        if (apiIdentifier == null) {
            return false;
        }
        if (this.getClass() != apiIdentifier.getClass()) {
            return false;
        }
        return this.path.equals(((ApiIdentifier) apiIdentifier).path) &&
                this.httpMethod.equals(((ApiIdentifier) apiIdentifier).httpMethod);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getSimpleName()).append(" [");
        builder.append(" path: ").append(path);
        builder.append(", httpMethod: ").append(httpMethod).append(" ]");

        return builder.toString();
    }
}
