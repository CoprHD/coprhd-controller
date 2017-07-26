/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.smc.symmetrix.resource;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultResponse;

public class GenericResultImplType extends DefaultResponse {

    private Boolean success;
    private String message;

    public Boolean getSuccess() {
        return success;
    }

    /**
     * @param success the success to set
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(String message) {
        this.message = message;
        this.appendCustMessage(message);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GenericResultImplType [success=" + success + ", message=" + message + ", getCustMessage()=" + getCustMessage()
                + ", getHttpStatusCode()=" + getHttpStatusCode() + "]";
    }

}
