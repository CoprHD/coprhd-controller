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

public class IBMSVCQueryAllHostResult {

    List<IBMSVCHost> hostList;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryAllHostResult() {
        super();
        hostList = new ArrayList<IBMSVCHost>();
    }

    public List<IBMSVCHost> getHostList() {
        return hostList;
    }

    public void setHostList(List<IBMSVCHost> hostList) {
        this.hostList = hostList;
    }

    public void addHostList(IBMSVCHost host) {
        this.hostList.add(host);
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
