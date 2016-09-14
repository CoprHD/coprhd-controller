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

public class IBMSVCQueryStoragePoolResult {
    
    private IBMSVCAttributes properties;
    
    private List<String> supportedDriveTypes;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryStoragePoolResult() {
        this.properties = new IBMSVCAttributes();
        this.supportedDriveTypes = new ArrayList<String>();
    }

    public String getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    void setProperty(String propertyName, String value) {
        properties.put(propertyName, value);
    }

    public List<String> getSupportedDriveTypes() {
        return supportedDriveTypes;
    }

    public void setSupportedDriveTypes(List<String> supportedDriveTypes) {
        this.supportedDriveTypes = supportedDriveTypes;
    }
    
    public void addSupportedDriveTypes(String supportedDriveType) {
        this.supportedDriveTypes.add(supportedDriveType);
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
