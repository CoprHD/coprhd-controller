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

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Parameter for network creation
 */
@XmlRootElement(name = "network_create")
public class NetworkCreate {
    private String label;
    private String transportType;
    private List<URI> varrays;
    private List<String> endpoints;

    public NetworkCreate() {}
    
    public NetworkCreate(String label, String transportType) {
        this.label = label;
        this.transportType = transportType;
    }

    /**
     * Name of the network; must be unique.
     * @valid must be unique within all existing networks
     * @valid example: network1
     */
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Transport type of the network.
     * @valid FC = Fibre Channel
     * @valid IP
     * @valid Ethernet
     */
    @XmlElement(required = true, name = "transport_type")
    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }
    /**
     * The virtual arrays to which the network is associated.
     */
    @XmlElementWrapper(name = "varrays")
    @XmlElement(name = "varray")
	public List<URI> getVarrays() {
		return varrays;
	}

	public void setVarrays(List<URI> varrays) {
		this.varrays = varrays;
	}

    /**
     * The endpoints of the network. For an FC network, the endpoints are the WWNs of
     * the storage port and host initiators that are in the network. For an IP network,
     * these are the IQNs, EUIs, IP addresses or host names for the hosts and the storage
     * ports. 
     */
    @XmlElementWrapper(name = "endpoints")
    @XmlElement(name = "endpoint")
    public List<String> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<String> endpoints) {
		this.endpoints = endpoints;
	}
}
