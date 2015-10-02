/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualArrayResourceRestRep extends DiscoveredDataObjectRestRep {

    private Set<String> assignedVirtualArrays;
    private Set<String> connectedVirtualArrays;
    private Set<String> taggedVirtualArrays;

    public VirtualArrayResourceRestRep() {
    }

    /**
     * The virtual arrays to which this resource has been explicitly
     * assigned by a user.
     * 
     */
    @XmlElement(name = "assigned_varrays")
    public Set<String> getAssignedVirtualArrays() {
        if (assignedVirtualArrays == null) {
            assignedVirtualArrays = new LinkedHashSet<String>();
        }
        return assignedVirtualArrays;
    }

    public void setAssignedVirtualArrays(Set<String> assignedVirtualArrays) {
        this.assignedVirtualArrays = assignedVirtualArrays;
    }

    /**
     * The virtual arrays that are associated with this resource due to network
     * connectivity. For example, a storage port would be connected to a virtual
     * array if the port is in a network assigned to the virtual array. Similarly,
     * the storage pools on that storage port's storage system would also be
     * connected to the virtual array.
     * 
     */
    @XmlElement(name = "connected_varrays")
    public Set<String> getConnectedVirtualArrays() {
        if (connectedVirtualArrays == null) {
            connectedVirtualArrays = new LinkedHashSet<String>();
        }
        return connectedVirtualArrays;
    }

    public void setConnectedVirtualArrays(Set<String> connectedVirtualArrays) {
        this.connectedVirtualArrays = connectedVirtualArrays;
    }

    /**
     * The virtual arrays that are associated with this resource for the purpose
     * of searching for resources that are associated with a virtual array. If a
     * resource is explicitly assigned to one or more virtual arrays, those
     * virtual arrays are the tagged virtual arrays. If there are no explicit
     * assignments for the resource, all of the connected virtual arrays are the
     * tagged virtual arrays.
     * 
     */
    @XmlElement(name = "tagged_varrays")
    public Set<String> getTaggedVirtualArrays() {
        if (taggedVirtualArrays == null) {
            taggedVirtualArrays = new LinkedHashSet<String>();
        }
        return taggedVirtualArrays;
    }

    public void setTaggedVirtualArrays(Set<String> taggedVirtualArrays) {
        this.taggedVirtualArrays = taggedVirtualArrays;
    }
}
