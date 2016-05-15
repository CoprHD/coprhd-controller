/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class SaUsage {
    private Integer totalMiB;
    private Integer usedMiB;
    
    public Integer getTotalMiB() {
        return totalMiB;
    }
    public void setTotalMiB(Integer totalMiB) {
        this.totalMiB = totalMiB;
    }
    public Integer getUsedMiB() {
        return usedMiB;
    }
    public void setUsedMiB(Integer usedMiB) {
        this.usedMiB = usedMiB;
    }
}
