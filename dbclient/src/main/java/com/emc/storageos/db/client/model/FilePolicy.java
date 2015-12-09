/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * FilePolicy will contain the details of file schedule policy.
 * It will hold information about the policyId, policyName, policySchedule, policyExpiration
 * 
 * @author prasaa9
 * 
 */

@Cf("FilePolicy")
public class FilePolicy extends DiscoveredDataObject {

    // Name of the policy
    private String policyName;

    // File policy schedule at
    private String policySchedule;

    // File snapshot expire after
    private Long snapshotExpire;

    @Name("policyName")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
        setChanged("policyName");
    }

    @Name("policySchedule")
    public String getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(String policySchedule) {
        this.policySchedule = policySchedule;
        setChanged("policySchedule");
    }

    @Name("snapshotExpire")
    public Long getSnapshotExpire() {
        return snapshotExpire;
    }

    public void setSnapshotExpire(Long snapshotExpire) {
        this.snapshotExpire = snapshotExpire;
        setChanged("snapshotExpire");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Policy [name=");
        builder.append(policyName);
        builder.append(", schedule at=");
        builder.append(policySchedule);
        builder.append(", snapshot expire after=");
        builder.append(snapshotExpire);
        builder.append("]");
        return builder.toString();
    }
}
