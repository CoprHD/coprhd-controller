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

import java.util.HashMap;
import java.util.Map;

public class IBMSVCQueryFCConsistGrpResult {

    private String consistGrpId;

    private String consistGrpName;

    private String consistGrpStatus;

    private Map<String, String> fcMappingProp;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryFCConsistGrpResult() {
        this.fcMappingProp = new HashMap<String, String>();
    }

    public String getConsistGrpId() {
        return consistGrpId;
    }

    public void setConsistGrpId(String consistGrpId) {
        this.consistGrpId = consistGrpId;
    }

    public String getConsistGrpName() {
        return consistGrpName;
    }

    public void setConsistGrpName(String consistGrpName) {
        this.consistGrpName = consistGrpName;
    }

    public String getConsistGrpStatus() {
        return consistGrpStatus;
    }

    public void setConsistGrpStatus(String consistGrpStatus) {
        this.consistGrpStatus = consistGrpStatus;
    }

    public Map<String, String> getFcMappingProp() {
        return fcMappingProp;
    }

    public void setFcMappingProp(Map<String, String> fcMappingProp) {
        this.fcMappingProp = fcMappingProp;
    }

    public void addFcMappingData(String fcMappingId, String fcMappingName) {
        this.fcMappingProp.put(fcMappingId, fcMappingName);
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
