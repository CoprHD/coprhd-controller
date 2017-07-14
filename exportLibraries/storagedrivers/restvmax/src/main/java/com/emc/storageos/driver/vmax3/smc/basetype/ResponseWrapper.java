/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

public class ResponseWrapper<T> {
    private final static int HTTP_CODE_OK = 200;// OK
    private final static int HTTP_CODE_CREATED = 201;// Created
    public String message;
    public int status;
    public Exception exception;
    public T responseBean;

    /**
     * 
     */
    public ResponseWrapper() {
        super();
    }

    /**
     * @param status
     * @param responseBean
     */
    public ResponseWrapper(int status, T responseBean) {
        super();
        this.status = status;
        this.responseBean = responseBean;
    }

    /**
     * @param message
     * @param status
     * @param exception
     */
    public ResponseWrapper(int status, String message, Exception exception) {
        super();
        this.message = message;
        this.status = status;
        this.exception = exception;
    }

    public boolean isSuccessfulStatus() {
        // TODO check status

        return isCreated() || isOK();
    }

    public boolean isCreated() {
        return status == HTTP_CODE_CREATED;
    }

    public boolean isOK() {
        return status == HTTP_CODE_OK;
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
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }

    /**
     * @return the responseBean
     */
    public T getResponseBean() {
        return responseBean;
    }

    /**
     * @param responseBean the responseBean to set
     */
    public void setResponseBean(T responseBean) {
        this.responseBean = responseBean;
    }

}
