/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

/**
 * Bean class used to record array detail info for testing.
 *
 * Created by gang on 7/1/16.
 */
public class ArrayInfo {
    private String arrayId;
    private String srpId;
    private String directorId;
    private String portId;

    public ArrayInfo(String arrayId, String srpId, String directorId, String portId) {
        this.arrayId = arrayId;
        this.srpId = srpId;
        this.directorId = directorId;
        this.portId = portId;
    }

    public String getArrayId() {
        return arrayId;
    }

    public void setArrayId(String arrayId) {
        this.arrayId = arrayId;
    }

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public String getDirectorId() {
        return directorId;
    }

    public void setDirectorId(String directorId) {
        this.directorId = directorId;
    }

    public String getPortId() {
        return portId;
    }

    public void setPortId(String portId) {
        this.portId = portId;
    }
}
