/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

public class ResponseWrapper<T> {
    public Exception exception;
    public T responseBean;

    /**
     * 
     */
    public ResponseWrapper() {
        super();
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
