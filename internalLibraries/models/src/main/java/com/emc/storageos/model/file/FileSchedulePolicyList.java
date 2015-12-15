/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * This Class represents a return type that returns the id, name and self link
 * for a list of file schedule policies.
 * 
 * @author prasaa9
 * 
 */
@XmlRootElement(name = "file_schedule_policies")
public class FileSchedulePolicyList {

    // List of file schedule policies
    private List<NamedRelatedResourceRep> schedulePolicies;

    public FileSchedulePolicyList() {

    }

    public FileSchedulePolicyList(List<NamedRelatedResourceRep> schedulePolicies) {
        this.schedulePolicies = schedulePolicies;
    }

    @XmlElement(name = "file_schedule_policy")
    public List<NamedRelatedResourceRep> getSchdulePolicies() {
        if (schedulePolicies == null) {
            schedulePolicies = new ArrayList<NamedRelatedResourceRep>();
        }
        return schedulePolicies;
    }

    public void setSchdulePolicies(List<NamedRelatedResourceRep> schedulePolicies) {
        this.schedulePolicies = schedulePolicies;
    }

}
