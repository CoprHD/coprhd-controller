/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

public interface IResponse {

    /**
     * @return the message
     */
    String getCustMessage();

    /**
     * @param message the message to set
     */
    void setCustMessage(String message);

    /**
     * Append new message to original message.
     * 
     * @param message
     */
    void appendCustMessage(String message);

    /**
     * @return the status
     */
    int getHttpStatusCode();

    /**
     * @param status the status to set
     */
    void setHttpStatusCode(int httpStatusCode);

    boolean isSuccessfulStatus();

    /**
     * Translate this pojo to ViPR pojo
     * 
     * @param clazz
     * @return
     */
    <T> T toViprPojo(Class<T> clazz);
}
