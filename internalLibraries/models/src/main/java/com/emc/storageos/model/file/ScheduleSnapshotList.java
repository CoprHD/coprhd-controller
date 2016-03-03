/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "schedule_snapshots")
public class ScheduleSnapshotList {

    /**
     * List of Snapshots.
     * 
     */
    private List<ScheduleSnapshotRestRep> scheduleSnapList;

    public ScheduleSnapshotList() {
    }

    public ScheduleSnapshotList(List<ScheduleSnapshotRestRep> scheduleSnapList) {
        this.scheduleSnapList = scheduleSnapList;
    }

    @XmlElement(name = "schedule_snapshot")
    public List<ScheduleSnapshotRestRep> getScheduleSnapList() {
        if (scheduleSnapList == null) {
            scheduleSnapList = new ArrayList<ScheduleSnapshotRestRep>();
        }
        return scheduleSnapList;
    }

    public void setScheduleSnapList(List<ScheduleSnapshotRestRep> scheduleSnapList) {
        this.scheduleSnapList = scheduleSnapList;
    }
}
