/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.storagesystem.type;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_system_type_add")
@XmlAccessorType(XmlAccessType.FIELD)
public class StorageSystemTypeAddParam {

	@XmlElement(name = "storage_type_name")
	private String storageTypeName;

	@XmlElement(name = "storage_type_type")
	private String storageTypeType;

	@XmlElement(name = "storage_type_id")
	private String storageTypeId;

	@XmlElement(name = "isSmiProvider")
	private boolean isSmiProvider = false;

	@XmlElement(name = "isDefaultSsl")
	private boolean isDefaultSsl = false;

	@XmlElement(name = "storage_type_disp_name")
	private String storageTypeDispName;

	@XmlElement(name = "isDefaultMDM")
	private boolean isDefaultMDM = false;

	@XmlElement(name = "isOnlyMDM")
	private boolean isOnlyMDM = false;

	@XmlElement(name = "isElementMgr")
	private boolean isElementMgr = false;

	@XmlElement(name = "nonSslPort")
	private String nonSslPort;

	@XmlElement(name = "sslPort")
	private String sslPort;

	@XmlElement(name = "driverClassName")
	private String driverClassName;

	public StorageSystemTypeAddParam() {
	}

	public String getStorageTypeId() {
		return storageTypeId;
	}

	public void setStorageTypeId(String storageTypeId) {
		this.storageTypeId = storageTypeId;
	}

	public String getStorageTypeName() {
		return storageTypeName;
	}

	public void setStorageTypeName(String storageSystemTypeName) {
		this.storageTypeName = storageSystemTypeName;
	}

	public String getStorageTypeType() {
		return storageTypeType;
	}

	public void setStorageType(String storageType) {
		this.storageTypeType = storageType;
	}

	public boolean getIsSmiProvider() {
		return isSmiProvider;
	}

	public void setIsSmiProvider(boolean isSmiProvider) {
		this.isSmiProvider = isSmiProvider;
	}

	public String getStorageTypeDispName() {
		return storageTypeDispName;
	}

	public void setStorageTypeDispName(String storageTypeDispName) {
		this.storageTypeDispName = storageTypeDispName;
	}

	public boolean getIsDefaultSsl() {
		return isDefaultSsl;
	}

	public void setIsDefaultSsl(boolean isDefaultSsl) {
		this.isDefaultSsl = isDefaultSsl;
	}

	public boolean getIsDefaultMDM() {
		return isDefaultMDM;
	}

	public void setIsDefaultMDM(boolean isDefaultMDM) {
		this.isDefaultMDM = isDefaultMDM;
	}

	public boolean getIsOnlyMDM() {
		return isOnlyMDM;
	}

	public void setIsOnlyMDM(boolean isOnlyMDM) {
		this.isOnlyMDM = isOnlyMDM;
	}

	public boolean getIsElementMgr() {
		return isElementMgr;
	}

	public void setIsElementMgr(boolean isElementMgr) {
		this.isElementMgr = isElementMgr;
	}

	public String getSslPort() {
		return sslPort;
	}

	public void setSslPort(String sslPort) {
		this.sslPort = sslPort;
	}

	public String getNonSslPort() {
		return nonSslPort;
	}

	public void setNonSslPort(String nonSslPort) {
		this.nonSslPort = nonSslPort;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

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
		builder.append(", isDefaultSsl=");
		builder.append(isDefaultSsl);
		builder.append(", nonSslPort=");
		builder.append(nonSslPort);
		builder.append(", sslPort=");
		builder.append(sslPort);
		builder.append(", driverClassName=");
		builder.append(driverClassName);
		builder.append("]");
		return builder.toString();
	}

}
