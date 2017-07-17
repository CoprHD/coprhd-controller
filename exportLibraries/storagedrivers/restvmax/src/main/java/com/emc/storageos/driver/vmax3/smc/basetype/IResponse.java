/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

public interface IResponse {

    /**
     * @return the message
     */
    String getMessage();

    /**
     * @param message the message to set
     */
    void setMessage(String message);

    /**
     * Append new message to original message.
     * 
     * @param message
     */
    void appendMessage(String message);

    /**
     * @return the status
     */
    int getStatus();

    /**
     * @param status the status to set
     */
    void setStatus(int status);

    boolean isSuccessfulStatus();
}
