/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * File Policy and returned as a response to a REST request.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "file_schedule_policy")
public class FilePolicyRestRep {

    // File policy id
    private URI policyId;

    // File policy name
    private String policyName;

    // File policy schedule at
    private String policySchedule;

    // File snapshot expire after
    private Long snapshotExpire;

    @XmlElement(name = "policy_id")
    public URI getPolicyId() {
        return policyId;
    }

    public void setPolicyId(URI policyId) {
        this.policyId = policyId;
    }

    @XmlElement(name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(name = "policy_schedule")
    public String getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(String policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "snapshot_expire")
    public Long getSnapshotExpire() {
        return snapshotExpire;
    }

    public void setSnapshotExpire(Long snapshotExpire) {
        this.snapshotExpire = snapshotExpire;
    }
}
