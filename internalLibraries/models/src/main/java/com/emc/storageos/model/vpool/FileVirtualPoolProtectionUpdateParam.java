/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_protection")
public class FileVirtualPoolProtectionUpdateParam extends VirtualPoolProtectionParam {

    private Boolean scheduleSnapshots;
    private Boolean replicationSupported;
    private Boolean allowFilePolicyAtProjectLevel;
    private Boolean allowFilePolicyAtFSLevel;
    private Long minRpoValue;
    private String minRpoType;

    public FileVirtualPoolProtectionUpdateParam() {
    }

    /**
     * The snapshot protection settings for a virtual pool.
     * 
     */

    @XmlElement(name = "schedule_snapshots")
    public Boolean getScheduleSnapshots() {
        return scheduleSnapshots;
    }

    public void setScheduleSnapshots(Boolean scheduleSnapshots) {
        this.scheduleSnapshots = scheduleSnapshots;
    }

    @XmlElement(name = "replication_supported")
    public Boolean getReplicationSupported() {
        return replicationSupported;
    }

    public void setReplicationSupported(Boolean replicationSupported) {
        this.replicationSupported = replicationSupported;
    }

    @XmlElement(name = "allow_policy_at_project_level")
    public Boolean getAllowFilePolicyAtProjectLevel() {
        return allowFilePolicyAtProjectLevel;
    }

    public void setAllowFilePolicyAtProjectLevel(Boolean allowFilePolicyAtProjectLevel) {
        this.allowFilePolicyAtProjectLevel = allowFilePolicyAtProjectLevel;
    }

    @XmlElement(name = "allow_policy_at_fs_level")
    public Boolean getAllowFilePolicyAtFSLevel() {
        return allowFilePolicyAtFSLevel;
    }

    public void setAllowFilePolicyAtFSLevel(Boolean allowFilePolicyAtFSLevel) {
        this.allowFilePolicyAtFSLevel = allowFilePolicyAtFSLevel;
    }

    @XmlElement(name = "min_rpo_value")
    public Long getMinRpoValue() {
        return minRpoValue;
    }

    public void setMinRpoValue(Long minRpoValue) {
        this.minRpoValue = minRpoValue;
    }

    @XmlElement(name = "rpo_type")
    public String getMinRpoType() {
        return minRpoType;
    }

    public void setMinRpoType(String minRpoType) {
        this.minRpoType = minRpoType;
    }

}
