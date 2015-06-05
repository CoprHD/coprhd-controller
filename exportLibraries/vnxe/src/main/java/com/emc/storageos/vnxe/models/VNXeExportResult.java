/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

public class VNXeExportResult {
    private String lunId;
    private String hostId;
    private int hlu;
    public String getLunId() {
        return lunId;
    }
    public void setLunId(String lunId) {
        this.lunId = lunId;
    }
    public String getHostId() {
        return hostId;
    }
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }
    public int getHlu() {
        return hlu;
    }
    public void setHlu(int hlu) {
        this.hlu = hlu;
    }
    

}
