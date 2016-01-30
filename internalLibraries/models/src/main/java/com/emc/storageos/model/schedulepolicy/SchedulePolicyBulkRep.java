/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.schedulepolicy;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

/**
 * List of Schedule Policies and returned as a bulk response to a REST request.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "bulk_schedule_policies")
public class SchedulePolicyBulkRep extends BulkRestRep {
    private List<SchedulePolicyRestRep> schedulePolicies;

    public SchedulePolicyBulkRep() {
    }

    public SchedulePolicyBulkRep(List<SchedulePolicyRestRep> schedulePolicies) {
        super();
        this.schedulePolicies = schedulePolicies;
    }

    /**
     * List of Schedule Policies. A schedule policy represents a
     * policy execute at scheduled time.
     * 
     */
    @XmlElement(name = "schedule_policy")
    public List<SchedulePolicyRestRep> getSchedulePolicies() {
        if (schedulePolicies == null) {
            schedulePolicies = new ArrayList<SchedulePolicyRestRep>();
        }
        return schedulePolicies;
    }

    /**
     * @param schedulePolicies the schedulePolicies to set
     */
    public void setSchedulePolicies(List<SchedulePolicyRestRep> schedulePolicies) {
        this.schedulePolicies = schedulePolicies;
    }
}
