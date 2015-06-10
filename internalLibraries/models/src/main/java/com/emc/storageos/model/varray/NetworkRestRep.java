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

package com.emc.storageos.model.varray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;
import com.emc.storageos.model.EndpointAliasRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "network")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class NetworkRestRep extends DiscoveredDataObjectRestRep {
    private RelatedResourceRep varray;
    private String transportType;
    private Set<String> endpoints;
    private List<EndpointAliasRestRep> endpointsDiscovered;
    private String fabricId;
    private Boolean discovered;
    private Set<String> networkSystems;
    private Set<String> assignedVArrays;
    private Set<String> connectedVArrays;
    private Set<String> routedNetworks;
    private String registrationStatus;

    public NetworkRestRep() {}
    
    /**
     * Indicates whether the network was discovered by a network system or manually created.
     * @valid true = network was discovered by a network system
     * @valid false = network was manually created
     */
    @XmlElement(name = "discovered")
    public Boolean getDiscovered() {
        return discovered;
    }

    public void setDiscovered(Boolean discovered) {
        this.discovered = discovered;
    }

    /**
     * List of endpoints associated with the network.
     * @valid none
     */
    @XmlElementWrapper(name = "endpoints")
    @XmlElement(name = "endpoint")
    public Set<String> getEndpoints() {
        if (endpoints == null) {
            endpoints = new LinkedHashSet<String>();
        }
        return endpoints;
    }

    public void setEndpoints(Set<String> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * List of endpoints associated with the network. Each endpoint is indexed 
     * to "true" indicating it was discovered or "false" indicating it was 
     * manually added into the network.
     * @valid none
     */
    @XmlElementWrapper(name = "endpoints_discovered")
    @XmlElement(name = "endpoint_discovered")
    public List<EndpointAliasRestRep> getEndpointsDiscovered() {
        if (endpointsDiscovered == null) {
            endpointsDiscovered = new ArrayList<EndpointAliasRestRep>();
        }
        return endpointsDiscovered;
    }

    public void setEndpointsDiscovered(List<EndpointAliasRestRep> endpointsDiscovered) {
        this.endpointsDiscovered = endpointsDiscovered;
    }

    /**
     * Fabric name or VSAN (Virtual Storage Area Network) ID for the 
     * network if discovered by a network system.
     * @valid none
     */
    @XmlElement(name = "fabric_id")
    public String getFabricId() {
        return fabricId;
    }

    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }

    /**
     * Virtual array associated with the network. 
     * <p>
     * This field is maintained for backward compatibility. It was replaced by
     * {@link #getVirtualArrays()}. This field returns the network varray when
     * the network is associated with a single varray. It returns null otherwise.
     * @see NetworkRestRep#getVirtualArrays()
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return varray;
    }

    public void setVirtualArray(RelatedResourceRep varray) {
        this.varray = varray;
    }

    /**
     * List of network systems that manage the network. These are the network 
     * systems where the network was discovered. 
     * Empty list for manually created network.
     * @valid none
     */
    @XmlElementWrapper(name = "network_systems")
    @XmlElement(name = "network_system")
    public Set<String> getNetworkSystems() {
        return networkSystems;
    }

    public void setNetworkSystems(Set<String> networkSystems) {
        this.networkSystems = networkSystems;
    }

    /**
     * List of virtual arrays to which the network is assigned. 
     *
     * @valid none
     */
    @XmlElementWrapper(name = "assigned_varrays")
    @XmlElement(name = "assigned_varray")
    public Set<String> getAssignedVirtualArrays() {
        return assignedVArrays;
    }

    public void setAssignedVirtualArrays(Set<String> varrays) {
        assignedVArrays = varrays;
    }
    
    /**
     * List of virtual arrays to which the network is implicitly
     * connected because a storage port in the network has been
     * assigned to the virtual array. 
     *
     * @valid none
     */
    @XmlElementWrapper(name = "connected_varrays")
    @XmlElement(name = "connected_varray")
    public Set<String> getConnectedVirtualArrays() {
        return connectedVArrays;
    }

    public void setConnectedVirtualArrays(Set<String> varrays) {
        connectedVArrays = varrays;
    }

    /**
     * Indicates whether the network and its endpoints can be used for provisioning 
     * operation. Only registered networks can be used for provisioning operations.
     * @valid UNREGISTERED 
     * @valid REGISTERED
     */
    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    /**
     * Transport type for the network. Valid values are IP, FC, and Ethernet.
     * @valid IP for IP and iSCSI endpoints
     * @valid FC = Fibre Channel
     * @valid Ethernet
     */
    @XmlElement(name = "transport_type")
    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    /**
     * A list of networks that are routed to this network
     * @valid FC = None
     * @return A list of networks that are routed to this network
     */
    @XmlElementWrapper(name = "routed_networks")
    @XmlElement(name = "routed_network")
    public Set<String> getRoutedNetworks() {
        return routedNetworks;
    }

    public void setRoutedNetworks(Set<String> routedNetworks) {
        this.routedNetworks = routedNetworks;
    }
}
