package com.emc.storageos.model.storagesystem.type;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "storagesystem_type")
public class StorageSystemTypeRestRep extends DataObjectRestRep {

	private String storageSystemTypeName;
	private String storageType;
	private String storageTypeId;
	private boolean isSmiProvider = false;

	public StorageSystemTypeRestRep() {
	}

	// TODO remove 2 methods
	@XmlElement(name = "type_id")
	public String getStorageSystemTypeId() {
		return storageTypeId;
	}

	public void setStorageSystemTypeId(String storageTypeId) {
		this.storageTypeId = storageTypeId;
	}

	@XmlElement(name = "type_name")
	public String getStorageSystemTypeName() {
		return storageSystemTypeName;
	}

	public void setStorageSystemTypeName(String storageSystemTypeName) {
		this.storageSystemTypeName = storageSystemTypeName;
	}

	@XmlElement(name = "type_type")
	public String getStorageType() {
		return storageType;
	}

	public void setStorageType(String storageType) {
		this.storageType = storageType;
	}

	@XmlElement(name = "isSmiProvider")
	public boolean getIsSmiProvider() {
		return isSmiProvider;
	}

	public void setIsSmiProvider(boolean isSmiProvider) {
		this.isSmiProvider = isSmiProvider;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("StorageSystemTypeRestRep [type_id=");
		builder.append(storageTypeId);
		builder.append(", type_name=");
		builder.append(storageSystemTypeName);
		builder.append(", type_type=");
		builder.append(storageType);
		builder.append(", isSmiProvider=");
		builder.append(isSmiProvider);
		builder.append("]");
		return builder.toString();
	}
}
