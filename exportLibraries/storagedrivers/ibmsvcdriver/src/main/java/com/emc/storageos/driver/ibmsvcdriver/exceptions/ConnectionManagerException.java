/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.exceptions;

public class ConnectionManagerException extends Exception {

    /**
     * This information is used for serialization purposes.
     */
    private static final long serialVersionUID = -2176917325853714131L;

    private int statusCode;

    private String message;

    public ConnectionManagerException(String message) {
        super(message);
    }

    public ConnectionManagerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionManagerException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        setStatusCode(statusCode);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
