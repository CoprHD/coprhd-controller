/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "network_update")
public class NetworkUpdate {

    private String name;
    private List<URI> varrays;

    private EndpointChanges endpointChanges;
    private VirtualArrayAssignmentChanges varrayChanges;

    public NetworkUpdate() {
    }

    public NetworkUpdate(String name, List<URI> varrays) {
        this.name = name;
        this.varrays = varrays;
    }

    /**
     * Name of the network; must be unique.
     * 
     * @valid Must be unique within all existing networks.
     * @valid example: network1
     */
    @XmlElement(required = false)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * List containing 0 or 1 virtual arrays.
     * Empty list will unassign the network from its existing virtual array.
     * List with one element will assign the network to the virtual array.
     * This field is maintained for backward compatibility and {@link #getVarrayChanges()} should be used instead. When used it will
     * override the existing virtual arrays of a
     * network.
     * 
     * @valid example: [ urn:storageos:VirtualArray:0de17e53-f657-4354-a547-0a47049013cb: ]
     * @valid example: [ ]
     * @deprecated use {@link #getVarrayChanges()}
     */
    @XmlElementWrapper(name = "varrays", required = false)
    @XmlElement(name = "varray", required = false)
    @JsonProperty("varrays")
    @Deprecated
    public List<URI> getVarrays() {
        if (varrays == null) {
            varrays = new ArrayList<URI>();
        }
        return varrays;
    }

    /**
     * @param varrays
     * @deprecated use {@link #setVarrayChanges(VirtualArrayAssignmentChanges)}
     */
    @Deprecated
    public void setVarrays(List<URI> varrays) {
        this.varrays = varrays;
    }

    /**
     * Add and remove lists of virtual arrays.
     * 
     * @valid lists of valid URIs for active virtual arrays
     */
    @XmlElement(name = "varray_assignment_changes")
    public VirtualArrayAssignmentChanges getVarrayChanges() {
        return varrayChanges;
    }

    public void setVarrayChanges(VirtualArrayAssignmentChanges varrays) {
        this.varrayChanges = varrays;
    }

    /**
     * Add and remove lists of endpoints
     * 
     * @valid valid endpoints for the network type (FC, IP or Ethernet)
     */
    @XmlElement(name = "endpoint_changes")
    public EndpointChanges getEndpointChanges() {
        return endpointChanges;
    }

    public void setEndpointChanges(EndpointChanges endpoints) {
        this.endpointChanges = endpoints;
    }

}
