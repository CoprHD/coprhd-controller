/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response class returns a list of block BlockPerformancePolicyRestRep instances.
 */
@XmlRootElement(name = "block_performance_policy_list")
public class BlockPerformancePolicyList {

    private List<NamedRelatedResourceRep> performancePolicyReps;

    /**
     * Default constructor
     */
    public BlockPerformancePolicyList()
    {}

    /**
     * Constructor.
     * 
     * @param performancePolicyReps A list of performance policy responses.
     */
    public BlockPerformancePolicyList(List<NamedRelatedResourceRep> performancePolicyReps) {
        this.performancePolicyReps = performancePolicyReps;
    }

    /*
     * Required Setters and Getters
     */

    /**
     * The list of performance policy responses.
     */
    @XmlElement(name = "performance_policies")
    @JsonProperty("performance_policies")
    public List<NamedRelatedResourceRep> getPerformancePolicies() {
        if (performancePolicyReps == null) {
            performancePolicyReps = new ArrayList<NamedRelatedResourceRep>();
        }
        return performancePolicyReps;
    }

    public void setPerformancePolicies(List<NamedRelatedResourceRep> performancePolicyReps) {
        this.performancePolicyReps = performancePolicyReps;
    }
}
