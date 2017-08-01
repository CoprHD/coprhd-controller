/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

/**
 * Specifies the performance policies for volumes in the volume topology.
 */
public class BlockPerformancePolicyMap {
    
    // The varray of the copy when the policies are applied to a RDF/RP copy.
    private URI varray;

    // The performance policies map.
    private List<BlockPerformancePolicyMapEntry> performancePolicies;

    /**
     * Default constructor
     */
    public BlockPerformancePolicyMap() {
    }

    /*
     * Required getters and setters.
     */

    /**
     * The performance policies.
     * 
     * @return The performance policies.
     */
    @XmlElement(name = "policies", required = true)
    public List<BlockPerformancePolicyMapEntry> getPolicies() {
        return performancePolicies;
    }

    public void setPolicies(List<BlockPerformancePolicyMapEntry> performancePolicies) {
        this.performancePolicies = performancePolicies;
    }

    /**
     * The varray of the copy when the policies are applied to a RDF/RP copy.
     * 
     * @return The varray URI.
     */
    @XmlElement(name = "varray", required = true)
    public URI getVirtualArray() {
        return varray;
    }

    public void setVirtualArray(URI varray) {
        this.varray = varray;
    }

    /*
     * Utility methods
     */

    /**
     * Finds the performance policy for the passed volume topology role.
     * 
     * @param role A string specifying the volume topology role.
     * 
     * @return The URI of the performance policy instance or null.
     */
    public URI findPerformancePolicyForRole(String role) {
        URI performancePolicyURI = null;
        if (role != null) {
            for (BlockPerformancePolicyMapEntry policyEntry : performancePolicies) {
                if (role.equals(policyEntry.getRole())) {
                    performancePolicyURI = policyEntry.getId(); 
                }
            }
        }

        return performancePolicyURI;
    }
    
}
