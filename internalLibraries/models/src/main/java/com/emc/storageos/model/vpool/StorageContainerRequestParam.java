package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_container_create")
public class StorageContainerRequestParam extends VirtualPoolCommonParam {
	
	//protocol endpoint type for storage container
	private String protocolEndPointType;

	@XmlElement(name = "protocolEndPointType")
	public String getProtocolEndPointType() {
		return protocolEndPointType;
	}

	public void setProtocolEndPointType(String protocolEndPointType) {
		this.protocolEndPointType = protocolEndPointType;
	}
	
	
   
	}
