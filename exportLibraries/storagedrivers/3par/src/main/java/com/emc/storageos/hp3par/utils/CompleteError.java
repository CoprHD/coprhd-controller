/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CompleteError {
    private String errorResp;
    private int httpCode;
    private String hp3parCode;
    
    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
   }

    
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
