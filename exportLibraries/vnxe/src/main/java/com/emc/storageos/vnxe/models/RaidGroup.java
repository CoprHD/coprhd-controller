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

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
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
