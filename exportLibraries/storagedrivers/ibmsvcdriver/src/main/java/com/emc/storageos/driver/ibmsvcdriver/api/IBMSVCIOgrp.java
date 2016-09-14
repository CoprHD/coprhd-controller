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


public class IBMSVCIOgrp {
    private String iogrpId;

    private String iogrpName;

    private int nodeCount;

    private int vdiskCount;

    private int hostCount;

    public String getIogrpId() {
        return iogrpId;
    }

    public void setIogrpId(String iogrpId) {
        this.iogrpId = iogrpId;
    }

    public String getIogrpName() {
        return iogrpName;
    }

    public void setIogrpName(String iogrpName) {
        this.iogrpName = iogrpName;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getVdiskCount() {
        return vdiskCount;
    }

    public void setVdiskCount(int vdiskCount) {
        this.vdiskCount = vdiskCount;
    }

    public int getHostCount() {
        return hostCount;
    }

    public void setHostCount(int hostCount) {
        this.hostCount = hostCount;
    }


}
