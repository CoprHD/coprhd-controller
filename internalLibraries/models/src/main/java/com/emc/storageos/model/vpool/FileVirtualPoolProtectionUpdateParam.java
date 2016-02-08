/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_protection")
public class FileVirtualPoolProtectionUpdateParam extends VirtualPoolProtectionParam {

    FileVirtualPoolReplicationUpdateParam replicationParam;
    private Boolean scheduleSnapshots;

    public FileVirtualPoolProtectionUpdateParam() {
    }

    public FileVirtualPoolProtectionUpdateParam(
            FileVirtualPoolReplicationUpdateParam replicationParam) {
        this.replicationParam = replicationParam;
    }

    /**
     * The replication protection settings for a virtual pool.
     * 
     */
    @XmlElement(name = "replication_params")
    public FileVirtualPoolReplicationUpdateParam getReplicationParam() {
        return replicationParam;
    }

    public void setReplicationParam(FileVirtualPoolReplicationUpdateParam replParam) {
        this.replicationParam = replParam;
    }

    /**
     * The schedule snapshot protection settings for a virtual pool.
     * 
     */
    @XmlElement(name = "schedule_snapshots")
    public Boolean getScheduleSnapshots() {
        return scheduleSnapshots;
    }

    public void setScheduleSnapshots(Boolean scheduleSnapshots) {
        this.scheduleSnapshots = scheduleSnapshots;
    }
}
