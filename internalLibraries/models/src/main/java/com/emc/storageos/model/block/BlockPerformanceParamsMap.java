/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;

/**
 * Specifies the performance parameters for volumes in the volume topology.
 */
public class BlockPerformanceParamsMap {

    // The performance parameters to use for the source volume.
    private List<BlockPerformanceParamsMapEntry> performanceParams;

    /**
     * Default constructor
     */
    public BlockPerformanceParamsMap() {
    }

    /*
     * Required getters and setters.
     */

    /**
     * The performance parameters.
     * 
     * @return The performance parameters.
     */
    @XmlElement(name = "param", required = true)
    public List<BlockPerformanceParamsMapEntry> getParams() {
        return performanceParams;
    }

    public void setParams(List<BlockPerformanceParamsMapEntry> performanceParams) {
        this.performanceParams = performanceParams;
    }
}
