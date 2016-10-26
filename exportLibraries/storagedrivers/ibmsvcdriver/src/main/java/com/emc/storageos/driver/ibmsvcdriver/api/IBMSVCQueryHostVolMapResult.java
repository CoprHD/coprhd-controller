/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IBMSVCQueryHostVolMapResult {

    private boolean isSuccess;

    private String errorString;

    private int volCount;

    private Map<String, String>volHluMap;

    public IBMSVCQueryHostVolMapResult() {
        super();
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public String getErrorString() {
        return errorString;
    }

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }

    public void setVolCount(int count){
        this.volCount = count;
    }

    public int getVolCount(){
        return volCount;
    }

    public Map<String, String> getVolHluMap() {
        return volHluMap;
    }

    public void setVolHluMap(Map<String, String> volHluMap) {
        this.volHluMap = volHluMap;
    }

}
