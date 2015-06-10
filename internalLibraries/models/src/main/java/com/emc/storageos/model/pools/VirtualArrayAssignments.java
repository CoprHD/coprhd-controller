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
package com.emc.storageos.model.pools;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Class captures a list of URIs for the virtual arrays assigned to the
 * storage pool.
 */
public class VirtualArrayAssignments {

    private Set<String> varrays;

    public VirtualArrayAssignments() {}
    
    public VirtualArrayAssignments(Set<String> varrays) {
        this.varrays = varrays;
    }

    // The set of varray URIs.
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public Set<String> getVarrays() {
        if (varrays == null) {
            varrays = new HashSet<String>();
        }
        return varrays;
    }

    public void setVarrays(Set<String> varrays) {
        this.varrays = varrays;
    }    
    
}
