package com.emc.storageos.driver.ibmsvcdriver.api;/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import java.util.ArrayList;
import java.util.List;

public class IBMSVCQueryVolumeHostMappingResult {

    private List<IBMSVCHost> hostList;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryVolumeHostMappingResult() {
        super();
        hostList = new ArrayList<>();
    }

    public List<IBMSVCHost> getHostList() {
        return hostList;
    }

    public void setHostList(List<IBMSVCHost> hostList) {
        this.hostList = hostList;
    }

    public void addHostList(IBMSVCHost ibmsvcHost) {
        this.hostList.add(ibmsvcHost);
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
