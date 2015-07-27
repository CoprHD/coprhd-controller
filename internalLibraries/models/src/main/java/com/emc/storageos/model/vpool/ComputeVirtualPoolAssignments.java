/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;
import java.util.HashSet;
import java.util.Set;

/**
 * Class captures a list of URIs for the compute elements assigned during
 * virtual compute pool update.
 */
public class ComputeVirtualPoolAssignments {

    private Set<String> computeElements;

    /**
     * Default Constructor.
     */
    public ComputeVirtualPoolAssignments() {}

    public ComputeVirtualPoolAssignments(Set<String> computeElements) {
        this.computeElements = computeElements;
    }

     /**
     * The list of compute elements to be added to or removed from the compute virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "compute_element")
    @JsonProperty("compute_element")
    public Set<String> getComputeElements() {
        if (computeElements == null) {
            computeElements = new HashSet<String>();
        }
        return computeElements;
    }

    public void setComputeElements(Set<String> computeElements) {
        this.computeElements = computeElements;
    }
    
}
