/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

import com.emc.storageos.driver.univmax.SymConstants;

public class GenericResultImplType extends ResultType {

    // min/max occurs: 1/1
    private Boolean success;
    // min/max occurs: 0/1
    private StringBuilder message = new StringBuilder();

    private int httpCode;

    public Boolean getSuccess() {
        return success;
    }

    /**
     * @return the httpCode
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * @param httpCode the httpCode to set
     */
    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }

    /**
     * @param success the success to set
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(String message) {
        this.message.append(message).append(SymConstants.Mark.NEW_LINE);
    }

    public String getMessage() {
        return message.toString();
    }

    public boolean isSuccessfulStatus() {
        return isCreated() || isOK() || isNoContent();
    }

    private boolean isCreated() {
        return httpCode == SymConstants.StatusCode.CREATED;
    }

    private boolean isOK() {
        return httpCode == SymConstants.StatusCode.OK;
    }

    private boolean isNoContent() {
        return httpCode == SymConstants.StatusCode.NO_CONTENT;
    }

    private boolean hasException() {
        return httpCode == SymConstants.StatusCode.EXCEPTION;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GenericResultImplType [success=" + success + ", message=" + message + ", httpCode=" + httpCode + ", isSuccessfulStatus()="
                + isSuccessfulStatus() + "]";
    }

}
