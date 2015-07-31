/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RaidGroup {
    private VNXeBase diskGroup;
    private int raidType;
    private int stripeWidth;
    private int parityDisks;
    private List<VNXeBase> disks;

    public VNXeBase getDiskGroup() {
        return diskGroup;
    }

    public void setDiskGroup(VNXeBase diskGroup) {
        this.diskGroup = diskGroup;
    }

    public int getRaidType() {
        return raidType;
    }

    public void setRaidType(int raidType) {
        this.raidType = raidType;
    }

    public int getStripeWidth() {
        return stripeWidth;
    }

    public void setStripeWidth(int stripeWidth) {
        this.stripeWidth = stripeWidth;
    }

    public int getParityDisks() {
        return parityDisks;
    }

    public void setParityDisks(int parityDisks) {
        this.parityDisks = parityDisks;
    }

    public List<VNXeBase> getDisks() {
        return disks;
    }

    public void setDisks(List<VNXeBase> disks) {
        this.disks = disks;
    }

}
