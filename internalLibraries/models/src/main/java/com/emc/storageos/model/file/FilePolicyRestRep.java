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

@XmlRootElement(name = "file_policy")
public class FilePolicyRestRep {

    // File policy id
    private URI policyId;

    // File policy name
    private String policyName;

    // File policy schedule at
    private String policySchedule;

    // File policy expire at
    private Long policyExipration;

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

    @XmlElement(name = "policy_expire")
    public Long getPolicyExipration() {
        return policyExipration;
    }

    public void setPolicyExipration(Long policyExipration) {
        this.policyExipration = policyExipration;
    }
}
