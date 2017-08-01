/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures the performance policy settings when posting a request
 * to provision a new volume.
 */
public class VolumeCreatePerformancePolicies {

    // The performance policies for the source volume in the volume topology.
    private BlockPerformancePolicyMap sourcePerformancePolicies;

    // The performance policies for the copy volumes in the volume topology.
    private List<BlockPerformancePolicyMap> copyPerformancePolicies;

    /**
     * Default constructor
     */
    public VolumeCreatePerformancePolicies() {
    }

    /*
     * Required getters and setters.
     */

    /**
     * The performance policies to use for the source volume in the volume topology.
     * 
     * @return The performance policies for the source volume.
     */
    @XmlElement(name = "source_policies")
    public BlockPerformancePolicyMap getSourcePolicies() {
        return sourcePerformancePolicies;
    }

    public void setSourcePolicies(BlockPerformancePolicyMap sourcePerformancePolicies) {
        this.sourcePerformancePolicies = sourcePerformancePolicies;
    }

    /**
     * The performance policies to use for the copy volume(s) in the volume topology.
     * 
     * @return The performance policies for the copy volume(s).
     */
    @XmlElement(name = "copy_policies")
    public List<BlockPerformancePolicyMap> getCopyPolicies() {
        if (copyPerformancePolicies == null) {
            copyPerformancePolicies = new ArrayList<>();
        }
        return copyPerformancePolicies;
    }

    public void setCopyPolicies(List<BlockPerformancePolicyMap> copyPerformancePolicies) {
        this.copyPerformancePolicies = copyPerformancePolicies;
    }
}
