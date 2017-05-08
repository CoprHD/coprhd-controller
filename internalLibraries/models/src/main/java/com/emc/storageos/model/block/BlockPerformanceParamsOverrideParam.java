/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures the performance parameter settings when posting a request
 * to provision a new volume or create a new continuous copy i.e., mirror.
 */
public class BlockPerformanceParamsOverrideParam {

    // The performance parameters for the source volume in the volume topology.
    private BlockPerformanceParamsMap sourcePerformanceParams;

    // The performance parameters for the copy volumes in the volume topology.
    private List<BlockPerformanceParamsMap> copyPerformanceParams;

    /**
     * Default constructor
     */
    public BlockPerformanceParamsOverrideParam() {
    }

    /*
     * Required getters and setters.
     */

    /**
     * The performance parameters to use for the source volume in the volume topology.
     * 
     * @return The performance parameters for the source volume.
     */
    @XmlElement(name = "source_params")
    public BlockPerformanceParamsMap getSourceParams() {
        return sourcePerformanceParams;
    }

    public void setSourceParams(BlockPerformanceParamsMap sourcePerformanceParams) {
        this.sourcePerformanceParams = sourcePerformanceParams;
    }

    /**
     * The performance parameters to use for the copy volume(s) in the volume topology.
     * 
     * @return The performance parameters for the copy volume(s).
     */
    @XmlElement(name = "copy_params")
    public List<BlockPerformanceParamsMap> getCopyParams() {
        if (copyPerformanceParams == null) {
            copyPerformanceParams = new ArrayList<>();
        }
        return copyPerformanceParams;
    }

    public void setCopyParams(List<BlockPerformanceParamsMap> copyPerformanceParams) {
        this.copyPerformanceParams = copyPerformanceParams;
    }
}
