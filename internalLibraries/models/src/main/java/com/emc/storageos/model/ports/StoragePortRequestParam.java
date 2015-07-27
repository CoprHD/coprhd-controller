/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.ports;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * The Class StoragePortRequestParam.
 * This is only applicable to Cinder storage systems
 *  for users to manually create storage ports.
 */
@XmlRootElement(name = "storage_port_create")
public class StoragePortRequestParam {	
    
	private String name;
	private String transportType;
	private String portNetworkId;
	
    /**
     * Name of the storage system
     * 
     * @valid none
     */
	@XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Storage port transport type.
	 *
	 * @valid example FC, IP
	 */
	@XmlElement(required = true, name = "transport_type")
	public String getTransportType() {
		return transportType;
	}
	
	public void setTransportType(String transportType) {
		this.transportType = transportType;
	}

	/**
	 * Storage port network identifier.
     * 
	 * @valid example: FC - port WWN,
	 *                 IP - iSCSI Qualified Name (IQN) or Extended Unique Identifier (EUI)
	 */
	@XmlElement(required = true, name = "port_network_id")
    public String getPortNetworkId() {
        return portNetworkId;
    }

    public void setPortNetworkId(String portNetworkId) {
        this.portNetworkId = portNetworkId;
    }
}
