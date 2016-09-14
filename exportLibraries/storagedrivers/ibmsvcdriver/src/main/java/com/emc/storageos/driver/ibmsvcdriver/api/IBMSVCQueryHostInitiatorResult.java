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

import com.emc.storageos.storagedriver.model.Initiator;

public class IBMSVCQueryHostInitiatorResult {

    String hostId;

    List<Initiator> hostInitiatorList;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryHostInitiatorResult() {
        super();
        hostInitiatorList = new ArrayList<Initiator>();
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public List<Initiator> getHostInitiatorList() {
        return hostInitiatorList;
    }

    public void setHostInitiatorList(List<Initiator> hostInitiatorList) {
        this.hostInitiatorList = hostInitiatorList;
    }

    public void addHostInitiatorList(Initiator hostInitiator) {
        this.hostInitiatorList.add(hostInitiator);
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
