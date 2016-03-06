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

	private String storageTypeName;
	private String storageTypeType;
	private String storageTypeId;
	private boolean isSmiProvider = false;

	public StorageSystemTypeRestRep() {
	}

	// TODO remove 2 methods
	@XmlElement(name = "storage_type_id")
	public String getStorageTypeId() {
		return storageTypeId;
	}

	public void setStorageTypeId(String storageTypeId) {
		this.storageTypeId = storageTypeId;
	}

	@XmlElement(name = "storage_type_name")
	public String getStorageTypeName() {
		return storageTypeName;
	}

	public void setStorageTypeName(String storageSystemTypeName) {
		this.storageTypeName = storageSystemTypeName;
	}

	@XmlElement(name = "storage_type_type")
	public String getStorageTypeType() {
		return storageTypeType;
	}

	public void setStorageType(String storageType) {
		this.storageTypeType = storageType;
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
		builder.append("StorageSystemTypeRestRep [storage_type_id=");
		builder.append(storageTypeId);
		builder.append(", storage_type_name=");
		builder.append(storageTypeName);
		builder.append(", storage_type_type=");
		builder.append(storageTypeType);
		builder.append(", isSmiProvider=");
		builder.append(isSmiProvider);
		builder.append("]");
		return builder.toString();
	}
}
