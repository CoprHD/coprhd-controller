/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest;

import com.emc.storageos.driver.univmax.rest.type.common.IteratorType;

public class ResponseWrapper<T> {
    public Exception exception;
    public T responseBean;
    public IteratorType<T> responseBeanIterator;// Used for list method

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

    /**
     * @return the responseBeanIterator
     */
    public IteratorType<T> getResponseBeanIterator() {
        return responseBeanIterator;
    }

    /**
     * @param responseBeanIterator the responseBeanIterator to set
     */
    public void setResponseBeanIterator(IteratorType<T> responseBeanIterator) {
        this.responseBeanIterator = responseBeanIterator;
    }

}
