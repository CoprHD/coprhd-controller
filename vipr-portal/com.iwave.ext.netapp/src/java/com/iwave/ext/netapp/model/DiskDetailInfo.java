/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;

public class DiskDetailInfo implements Serializable {

    private static final long serialVersionUID = 234359267176149248L;

    private String aggregate;
    private String diskModel;
    private String diskType; // ATA, BSAS, EATA, FCAL, FSAS, LUN, SAS, SATA, SCSI, SSD, XATA, XSAS, or unknown.
    private String diskUid;
    private String name;
    private String node;
    private String pool;
    private String raidGroup;
    private String raidState; // partner, broken, zeroing, spare, copy, pending, reconstructing, present and unknown.
    private String raidType; // pending, parity, dparity, data, and unowned.
    private Integer rpm;
    private Long physicalSpace;

    public String getAggregate() {
        return aggregate;
    }

    public void setAggregate(String aggregate) {
        this.aggregate = aggregate;
    }

    public String getDiskModel() {
        return diskModel;
    }

    public void setDiskModel(String diskModel) {
        this.diskModel = diskModel;
    }

    public String getDiskType() {
        return diskType;
    }

    public void setDiskType(String diskType) {
        this.diskType = diskType;
    }

    public String getDiskUid() {
        return diskUid;
    }

    public void setDiskUid(String diskUid) {
        this.diskUid = diskUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getPool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    public String getRaidGroup() {
        return raidGroup;
    }

    public void setRaidGroup(String raidGroup) {
        this.raidGroup = raidGroup;
    }

    public String getRaidState() {
        return raidState;
    }

    public void setRaidState(String raidState) {
        this.raidState = raidState;
    }

    public String getRaidType() {
        return raidType;
    }

    public void setRaidType(String raidType) {
        this.raidType = raidType;
    }

    public Integer getRpm() {
        return rpm;
    }

    public void setRpm(Integer rpm) {
        this.rpm = rpm;
    }

    public Long getPhysicalSpace() {
        return physicalSpace;
    }

    public void setPhysicalSpace(Long physicalSpace) {
        this.physicalSpace = physicalSpace;
    }

}
