/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_port")
public class StoragePort {
	
	public StoragePort(){
		
	}
	
	public StoragePort(String id) {
		this.id = id;
	}
	
	@XmlElement
	private String id;

	@XmlElement
	private boolean inactive;
	
	@XmlElement(name="native_guid")
	private String nativeGuiId;

	@XmlElement(name = "name")
	private String label;

	@XmlElement(name = "port_name")
	private String portName;

	@XmlElement(name = "port_group")
	private String portGroup;

	@XmlElement(name = "port_network_id")
	private String portNetworkId;

	@XmlElement
	private Long portSpeed;

	@XmlElement(name = "storage_device")
	private Long storageSystem;

	@XmlElement(name = "transport_type")
	private String transportType;

	@XmlElement(name = "transport_zone")
	private String transportZone;

	public String getLabel() {
		return label;
	}

	public String getPortName() {
		return portName;
	}

	public String getPortNetworkId() {
		return portNetworkId;
	}

	public String getTransportType() {
		return transportType;
	}

	public String getId() {
		return id;
	}

	/**
	 * @return the inactive
	 */
	public boolean isInactive() {
		return inactive;
	}

	/**
	 * @return the portGroup
	 */
	public String getPortGroup() {
		return portGroup;
	}

	/**
	 * @return the portSpeed
	 */
	public Long getPortSpeed() {
		return portSpeed;
	}

	/**
	 * @return the storageSystem
	 */
	public Long getStorageSystem() {
		return storageSystem;
	}

	/**
	 * @return the transportZone
	 */
	public String getTransportZone() {
		return transportZone;
	}
	
	public String getNativeGuiId() {
		return nativeGuiId;
	}
	
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("StoragePort [id=");
		builder.append(id);
		builder.append(", inactive=");
		builder.append(inactive);
		builder.append(", nativeGuiId=");
		builder.append(nativeGuiId);
		builder.append(", label=");
		builder.append(label);
		builder.append(", portName=");
		builder.append(portName);
		builder.append(", portGroup=");
		builder.append(portGroup);
		builder.append(", portNetworkId=");
		builder.append(portNetworkId);
		builder.append(", portSpeed=");
		builder.append(portSpeed);
		builder.append(", storageSystem=");
		builder.append(storageSystem);
		builder.append(", transportType=");
		builder.append(transportType);
		builder.append(", transportZone=");
		builder.append(transportZone);
		builder.append("]");
		return builder.toString();
	}


	@XmlRootElement(name = "storage_port")
	public static class StoragePortInfo {
		
		@XmlElement(name="id")
		private String id;
		
		@XmlElement(name="name")
		private String name;
		
		public String getId() {
			return id;
		}
		
		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("StoragePortInfo [id=");
			builder.append(id);
			builder.append(", name=");
			builder.append(name);
			builder.append("]");
			return builder.toString();
		}

	}
	
	@XmlRootElement(name = "storage_ports")
	public static class StoragePortList {
		
		@XmlElement(name = "storage_port")
		private List<StoragePortInfo> portIds;

		public List<StoragePortInfo> getPortIds() {
			return portIds;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("StoragePortList [ids=");
			builder.append(portIds);
			builder.append("]");
			return builder.toString();
		}
	}

}


