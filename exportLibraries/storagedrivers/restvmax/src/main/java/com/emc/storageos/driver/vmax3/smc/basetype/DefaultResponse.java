/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

import com.emc.storageos.driver.vmax3.smc.SymConstants;

/**
 * @author fengs5
 *
 */
public class DefaultResponse implements IResponse {
    private StringBuilder custMessage = new StringBuilder();// this field is not part of json string, we use this to hold necessary error
                                                            // messages
    private int httpStatusCode;

    /**
     * @return the message
     */
    @Override
    public String getCustMessage() {
        return custMessage.toString();
    }

    /**
     * @param message the message to set
     */
    @Override
    public void setCustMessage(String message) {
        this.custMessage = new StringBuilder(message);
    }

    @Override
    public void appendCustMessage(String message) {
        this.custMessage.append(";").append(message);

    }

    /**
     * @return the httpStatusCode
     */
    @Override
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * @param httpStatusCode the httpStatusCode to set
     */
    @Override
    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    @Override
    public boolean isSuccessfulStatus() {
        // TODO check status

        return isCreated() || isOK();
    }

    private boolean isCreated() {
        return httpStatusCode == SymConstants.StatusCode.CREATED;
    }

    private boolean isOK() {
        return httpStatusCode == SymConstants.StatusCode.OK;
    }

    private boolean hasException() {
        return httpStatusCode == SymConstants.StatusCode.EXCEPTION;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.driver.vmax3.smc.basetype.IResponse#toViprPojo(java.lang.Class)
     */
    @Override
    public <T> T toViprPojo(Class<T> clazz) {
        // TODO Auto-generated method stub
        return null;
    }

}
