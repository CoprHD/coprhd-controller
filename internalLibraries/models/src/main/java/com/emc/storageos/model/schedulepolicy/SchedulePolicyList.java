/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.schedulepolicy;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * This Class represents a return type that returns the id, name and self link
 * for a list of schedule policies.
 * 
 * @author prasaa9
 * 
 */
@XmlRootElement(name = "schedule_policies")
public class SchedulePolicyList {

    // List of schedule policies
    private List<NamedRelatedResourceRep> schedulePolicies;

    public SchedulePolicyList() {

    }

    public SchedulePolicyList(List<NamedRelatedResourceRep> schedulePolicies) {
        this.schedulePolicies = schedulePolicies;
    }

    @XmlElement(name = "schedule_policy")
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
