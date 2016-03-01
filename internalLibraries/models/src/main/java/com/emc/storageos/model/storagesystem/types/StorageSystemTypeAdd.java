/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.storagesystem.types;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storageSystemType_add")
@XmlAccessorType(XmlAccessType.FIELD)
public class StorageSystemTypeAdd {

	@XmlElement(name = "name")
	private String name;

	@XmlElement(name = "storage_type")
	private String storageType;

	@XmlElement(name = "isProvider")
	private boolean isProvider;

	@XmlElement(name = "uuid")
	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStorageType() {
		return storageType;
	}

	public void setStorageType(String storageType) {
		this.storageType = storageType;
	}

	public boolean getIsProvider() {
		return isProvider;
	}

	public void setSsProvider(boolean isProvider) {
		this.isProvider = isProvider;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("StorageSystemTypeAdd [");
		builder.append("name=");
		builder.append(name);
		builder.append(", storageType=");
		builder.append(storageType);
		builder.append(", isProvider=");
		builder.append(isProvider);
		builder.append("]");
		return builder.toString();
	}

}
