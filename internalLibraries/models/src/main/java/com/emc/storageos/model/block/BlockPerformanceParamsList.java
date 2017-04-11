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
 * Response class returns a list of block BlockPerformanceParamRestRep instances.
 */
@XmlRootElement(name = "block_performance_params_list")
public class BlockPerformanceParamsList {

    private List<NamedRelatedResourceRep> performanceParamsReps;

    /**
     * Default constructor
     */
    public BlockPerformanceParamsList()
    {}

    /**
     * Constructor.
     * 
     * @param performanceParamsReps A list of performance parameter responses.
     */
    public BlockPerformanceParamsList(List<NamedRelatedResourceRep> performanceParamsReps) {
        this.performanceParamsReps = performanceParamsReps;
    }

    /*
     * Required Setters and Getters
     */

    /**
     * The list of performance parameter responses.
     */
    @XmlElement(name = "performance_params")
    @JsonProperty("performance_params")
    public List<NamedRelatedResourceRep> getPerformanceParams() {
        if (performanceParamsReps == null) {
            performanceParamsReps = new ArrayList<NamedRelatedResourceRep>();
        }
        return performanceParamsReps;
    }

    public void setPerformanceParams(List<NamedRelatedResourceRep> performanceParamsReps) {
        this.performanceParamsReps = performanceParamsReps;
    }
}
