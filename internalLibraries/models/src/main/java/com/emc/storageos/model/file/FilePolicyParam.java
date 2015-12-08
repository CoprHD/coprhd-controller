/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file policy, specified
 * during file policy creation.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "file_system_policy")
public class FilePolicyParam {

    private String policyName;
    private String policyPattern;
    private FilePolicyScheduleParam policySchedule;
    private FilePolicyScheduleParam policyExpire;

    @XmlElement(required = true, name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(required = true, name = "policy_schedule")
    public FilePolicyScheduleParam getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParam policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "policy_pattern")
    public String getPolicyPattern() {
        return policyPattern;
    }

    public void setPolicyPattern(String policyPattern) {
        this.policyPattern = policyPattern;
    }

    @XmlElement(name = "policy_expire")
    public FilePolicyScheduleParam getPolicyExpire() {
        return policyExpire;
    }

    public void setPolicyExpire(FilePolicyScheduleParam policyExpire) {
        this.policyExpire = policyExpire;
    }
}
