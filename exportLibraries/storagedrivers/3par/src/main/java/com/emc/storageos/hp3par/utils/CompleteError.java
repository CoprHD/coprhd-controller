/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.utils;

public class CompleteError {
    private String errorResp;
    private int httpCode;
    private String hp3parCode;
    
    public String getErrorResp() {
        return errorResp;
    }
    public void setErrorResp(String errorResp) {
        this.errorResp = errorResp;
    }
    public int getHttpCode() {
        return httpCode;
    }
    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }
    public String getHp3parCode() {
        return hp3parCode;
    }
    public void setHp3parCode(String hp3parCode) {
        this.hp3parCode = hp3parCode;
    }
}
