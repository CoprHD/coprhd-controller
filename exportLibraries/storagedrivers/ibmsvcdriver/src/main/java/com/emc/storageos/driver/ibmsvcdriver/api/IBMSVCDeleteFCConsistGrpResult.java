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

public class IBMSVCDeleteFCConsistGrpResult {

    private String consistGrpId;

    private String consistGrpName;

    private boolean isSuccess;

    private String errorString;

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
