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

@XmlRootElement(name = "bulk_block_performance_params")
public class BlockPerformanceParamsBulkRep extends BulkRestRep {
    
    // List of performance param instances.
    private List<BlockPerformanceParamsRestRep> performanceParams;

    /**
     * Default constructor.
     */
    public BlockPerformanceParamsBulkRep() {
    }

    /**
     * Constructor.
     * 
     * @param performanceParams List of performance param instances.
     */
    public BlockPerformanceParamsBulkRep(List<BlockPerformanceParamsRestRep> performanceParams) {
        this.performanceParams = performanceParams;
    }
    
    /*
     * Required Setters and getters.
     */

    /**
     * List of performance params instances.
     */
    @XmlElement(name = "block_performance_params")
    @JsonProperty("block_performance_params")
    public List<BlockPerformanceParamsRestRep> getPerformanceParams() {
        if (performanceParams == null) {
            performanceParams = new ArrayList<BlockPerformanceParamsRestRep>();
        }
        return performanceParams;
    }

    public void setPerformanceParams(List<BlockPerformanceParamsRestRep> performanceParams) {
        this.performanceParams = performanceParams;
    }
}
