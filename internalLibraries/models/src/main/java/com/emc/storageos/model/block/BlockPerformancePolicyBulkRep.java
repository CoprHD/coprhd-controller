/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.BulkRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_block_performance_policy")
public class BlockPerformancePolicyBulkRep extends BulkRestRep {

    // List of performance policy instances.
    private List<BlockPerformancePolicyRestRep> performancePolicies;

    /**
     * Default constructor.
     */
    public BlockPerformancePolicyBulkRep() {
    }

    /**
     * Constructor.
     * 
     * @param performancePolicies List of performance policy instances.
     */
    public BlockPerformancePolicyBulkRep(List<BlockPerformancePolicyRestRep> performancePolicies) {
        this.performancePolicies = performancePolicies;
    }

    /*
     * Required Setters and getters.
     */

    /**
     * List of performance policy instances.
     */
    @XmlElement(name = "block_performance_policies")
    @JsonProperty("block_performance_policies")
    public List<BlockPerformancePolicyRestRep> getPerformancePolicies() {
        if (performancePolicies == null) {
            performancePolicies = new ArrayList<BlockPerformancePolicyRestRep>();
        }
        return performancePolicies;
    }

    public void setPerformancePolicies(List<BlockPerformancePolicyRestRep> performancePolicies) {
        this.performancePolicies = performancePolicies;
    }
}
