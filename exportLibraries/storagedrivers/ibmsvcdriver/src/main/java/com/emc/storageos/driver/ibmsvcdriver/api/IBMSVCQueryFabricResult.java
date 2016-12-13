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

public class IBMSVCQueryFabricResult {

    List<IBMSVCFabricConnectivity> fabricConnectivityList;

    List<String> initiatorList;
    List<String> targetList;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryFabricResult() {
        super();
        this.fabricConnectivityList = new ArrayList<IBMSVCFabricConnectivity>();
        this.initiatorList = new ArrayList<>();
        this.targetList = new ArrayList<>();
    }

    public List<IBMSVCFabricConnectivity> getFabricConnectivityList() {
        return fabricConnectivityList;
    }

    public void setFabricConnectivityList(List<IBMSVCFabricConnectivity> fabricConnectivityList) {
        this.fabricConnectivityList = fabricConnectivityList;
    }

    public void addFabricConnectivityList(IBMSVCFabricConnectivity fabricConnectivity) {
        this.fabricConnectivityList.add(fabricConnectivity);
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

    public List<String> getInitiatorList() {
        return initiatorList;
    }

    public void setInitiatorList(List<String> initiatorList) {
        this.initiatorList = initiatorList;
    }

    public List<String> getTargetList() {
        return targetList;
    }

    public void setTargetList(List<String> targetList) {
        this.targetList = targetList;
    }

    public void addTarget(String target) {
        this.targetList.add(target);
    }

    public void addInitiator(String initiator) {
        this.initiatorList.add(initiator);
    }

}
