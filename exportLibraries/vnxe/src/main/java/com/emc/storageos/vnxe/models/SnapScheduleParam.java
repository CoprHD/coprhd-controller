/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public class SnapScheduleParam {
    private VNXeBase snapSchedule;
    private boolean isSnapSchedulePaused;

    public VNXeBase getSnapSchedule() {
        return snapSchedule;
    }

    public void setSnapSchedule(VNXeBase snapSchedule) {
        this.snapSchedule = snapSchedule;
    }

    public boolean getIsSnapSchedulePaused() {
        return isSnapSchedulePaused;
    }

    public void setIsSnapSchedulePaused(boolean isSnapSchedulePaused) {
        this.isSnapSchedulePaused = isSnapSchedulePaused;
    }

}
