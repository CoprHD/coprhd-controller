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

public class IBMSVCQueryVolumeFCMappingResult {

    List<Integer> fcMappingIds;

    List<String> fcMappingNames;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryVolumeFCMappingResult() {
        super();
        fcMappingIds = new ArrayList<Integer>();
        fcMappingNames = new ArrayList<String>();
    }

    public List<Integer> getFcMappingIds() {
        return fcMappingIds;
    }

    public void setFcMappingIds(List<Integer> fcMappingIds) {
        this.fcMappingIds = fcMappingIds;
    }

    public void addFCMappingId(int fcMappingId) {
        this.fcMappingIds.add(fcMappingId);
    }

    public List<String> getFcMappingNames() {
        return fcMappingNames;
    }

    public void setFcMappingNames(List<String> fcMappingNames) {
        this.fcMappingNames = fcMappingNames;
    }

    public void addFCMappingName(String fcMappingName) {
        this.fcMappingNames.add(fcMappingName);
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
