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

public class IBMSVCQueryHostIOGrpResult {
    List<IBMSVCIOgrp> hostIOGrpList;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryHostIOGrpResult() {
        super();
        hostIOGrpList = new ArrayList<IBMSVCIOgrp>();
    }

    public List getHostIOGrpList() {
        return hostIOGrpList;
    }

    public void setHostIOGrpList(List<IBMSVCIOgrp> hostIOGrpList) {
        this.hostIOGrpList = hostIOGrpList;
    }

    public void addHostIOGrpList(IBMSVCIOgrp iogrp) {
        this.hostIOGrpList.add(iogrp);
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

    public List<String> getIOGroupIDList (){
        List<String> ioGroupIDList = new ArrayList<>();

        for(IBMSVCIOgrp hostIOGrp : hostIOGrpList){
            ioGroupIDList.add(hostIOGrp.getIogrpId());
        }

        return ioGroupIDList;
    }

}
