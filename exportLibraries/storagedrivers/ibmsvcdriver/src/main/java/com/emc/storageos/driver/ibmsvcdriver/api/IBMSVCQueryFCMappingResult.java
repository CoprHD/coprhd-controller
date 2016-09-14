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

public class IBMSVCQueryFCMappingResult {

    private IBMSVCAttributes properties;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryFCMappingResult() {
        this.properties = new IBMSVCAttributes();
    }

    public String getId() {
        return properties.get("FCMappingId");
    }

    public void setId(String id) {
        properties.put("FCMappingId", id);
    }

    public String getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    void setProperty(String propertyName, String value) {
        properties.put(propertyName, value);
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
}
