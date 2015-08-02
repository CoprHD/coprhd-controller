/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

public class TieringPolicy {

    private String objectID;

    private String policyID;

    private int allocationThresholdT1Max;

    private int allocationThresholdT1Min;

    private int allocationThresholdT3Max;

    private int allocationThresholdT3Min;

    public TieringPolicy() {
    }

    public TieringPolicy(String objectID) {
        this.objectID = objectID;
    }

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public String getPolicyID() {
        return policyID;
    }

    public void setPolicyID(String policyID) {
        this.policyID = policyID;
    }

    public int getAllocationThresholdT1Max() {
        return allocationThresholdT1Max;
    }

    public void setAllocationThresholdT1Max(int allocationThresholdT1Max) {
        this.allocationThresholdT1Max = allocationThresholdT1Max;
    }

    public int getAllocationThresholdT1Min() {
        return allocationThresholdT1Min;
    }

    public void setAllocationThresholdT1Min(int allocationThresholdT1Min) {
        this.allocationThresholdT1Min = allocationThresholdT1Min;
    }

    public int getAllocationThresholdT3Max() {
        return allocationThresholdT3Max;
    }

    public void setAllocationThresholdT3Max(int allocationThresholdT3Max) {
        this.allocationThresholdT3Max = allocationThresholdT3Max;
    }

    public int getAllocationThresholdT3Min() {
        return allocationThresholdT3Min;
    }

    public void setAllocationThresholdT3Min(int allocationThresholdT3Min) {
        this.allocationThresholdT3Min = allocationThresholdT3Min;
    }

}
