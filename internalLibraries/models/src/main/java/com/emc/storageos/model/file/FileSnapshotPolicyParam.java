/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

/**
 * @author jainm15
 */
import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

public class FileSnapshotPolicyParam implements Serializable {

    private static final long serialVersionUID = 1L;
    // snapshot policy schedule parameters..
    private FilePolicyScheduleParams policySchedule;

    // Snapshot expire params like type and value..
    private FileSnapshotPolicyExpireParam snapshotExpireParams;

    public static enum SnapshotExpireType {
        HOURS, DAYS, WEEKS, MONTHS, NEVER
    }

    public FileSnapshotPolicyParam() {

    }

    @XmlElement(name = "policy_schedule")
    public FilePolicyScheduleParams getPolicySchedule() {
        return this.policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParams policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "snapshot_expire_params")
    public FileSnapshotPolicyExpireParam getSnapshotExpireParams() {
        return this.snapshotExpireParams;
    }

    public void setSnapshotExpireParams(FileSnapshotPolicyExpireParam snapshotExpireParams) {
        this.snapshotExpireParams = snapshotExpireParams;
    }

}
