/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

/**
 * Specifies the performance parameters for volumes in the volume topology.
 */
public class BlockPerformanceParamsMap {
    
    // The varray of the copy when the parameters are applied to a RDF/RP copy.
    private URI varray;

    // The performance parameters map.
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

    /**
     * The varray of the copy when the parameters are applied to a RDF/RP copy.
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
     * Finds the performance parameters for the passed volume topology role.
     * 
     * @param role A string specifying the volume topology role.
     * 
     * @return The URI of the performance parameters instance or null.
     */
    public URI findPerformanceParamsForRole(String role) {
        URI performanceParamsURI = null;
        if (role != null) {
            for (BlockPerformanceParamsMapEntry paramsEntry : performanceParams) {
                if (role.equals(paramsEntry.getRole())) {
                    performanceParamsURI = paramsEntry.getId(); 
                }
            }
        }

        return performanceParamsURI;
    }
    
}
