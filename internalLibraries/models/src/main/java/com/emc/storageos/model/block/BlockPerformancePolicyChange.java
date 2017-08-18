/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameters to update the performance policy for one or more volumes.
 */
@XmlRootElement(name = "block_performance_policy_change")
public class BlockPerformancePolicyChange {
    
    // The URIs of the volumes whose performance policy is to be changed.
    private List<URI> volumes;
    
    // The URI of the new performance policy.
    private List<BlockPerformancePolicyMapEntry> policies;
    
    /**
     * Default constructor
     */
    public BlockPerformancePolicyChange()
    {}

    /*
     * Required Setters and Getters
     */

    /**
     * The URIs of the volumes whose performance policy is to be changed.
     * Volumes are not required as this same class is used to perform a 
     * policy change on a consistency group in which case the change is
     * executed on the volumes in the consistency group. The id of the
     * consistency group is part of the URI of the request.
     */
    @XmlElementWrapper(name = "volumes")
    @XmlElement(name = "volume")
    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }
    
    /**
     * The URI of the new performance policy to be applies to the volumes.
     */
    @XmlElementWrapper(name = "policies", required = true)
    @XmlElement(name = "policy", required = true)
    public List<BlockPerformancePolicyMapEntry> getPolicies() {
        return policies;
    }

    public void setPolicies(List<BlockPerformancePolicyMapEntry> policies) {
        this.policies = policies;
    }
}
