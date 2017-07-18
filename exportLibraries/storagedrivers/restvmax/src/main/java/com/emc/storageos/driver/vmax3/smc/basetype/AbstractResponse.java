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
public abstract class AbstractResponse implements IResponse {
    private StringBuilder message = new StringBuilder();
    private int status;

    /**
     * @return the message
     */
    @Override
    public String getMessage() {
        return message.toString();
    }

    /**
     * @param message the message to set
     */
    @Override
    public void setMessage(String message) {
        this.message = new StringBuilder(message);
    }

    @Override
    public void appendMessage(String message) {
        this.message.append(message);

    }

    /**
     * @return the status
     */
    @Override
    public int getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public boolean isSuccessfulStatus() {
        // TODO check status

        return isCreated() || isOK();
    }

    private boolean isCreated() {
        return status == SymConstants.StatusCode.CREATED;
    }

    private boolean isOK() {
        return status == SymConstants.StatusCode.OK;
    }

    private boolean hasException() {
        return status == SymConstants.StatusCode.EXCEPTION;
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
