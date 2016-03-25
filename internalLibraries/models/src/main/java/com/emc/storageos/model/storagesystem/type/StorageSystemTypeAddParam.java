/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.storagesystem.type;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storagesystem_type_add")
@XmlAccessorType(XmlAccessType.FIELD)
public class StorageSystemTypeAddParam {

	private String storageTypeName;
	private String storageTypeType;
	private String storageTypeId;
	private boolean isSmiProvider = false;
	private boolean isDefaultSsl = false;
	private String storageTypeDispName;
	private boolean isDefaultMDM = false;
	private boolean isOnlyMDM = false;
	private boolean isElementMgr = false;
	private String nonSslPort;
	private String sslPort;
	private String driverClassName;

	public StorageSystemTypeAddParam() {
	}

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

	@XmlElement(name = "is_smi_provider")
	public boolean getIsSmiProvider() {
		return isSmiProvider;
	}

	public void setIsSmiProvider(boolean isSmiProvider) {
		this.isSmiProvider = isSmiProvider;
	}

	@XmlElement(name = "storage_type_disp_name")
	public String getStorageTypeDispName() {
		return storageTypeDispName;
	}

	public void setStorageTypeDispName(String storageTypeDispName) {
		this.storageTypeDispName = storageTypeDispName;
	}

	@XmlElement(name = "is_default_ssl")
	public boolean getIsDefaultSsl() {
		return isDefaultSsl;
	}

	public void setIsDefaultSsl(boolean isDefaultSsl) {
		this.isDefaultSsl = isDefaultSsl;
	}

	@XmlElement(name = "is_default_mdm")
	public boolean getIsDefaultMDM() {
		return isDefaultMDM;
	}

	public void setIsDefaultMDM(boolean isDefaultMDM) {
		this.isDefaultMDM = isDefaultMDM;
	}

	@XmlElement(name = "is_only_mdm")
	public boolean getIsOnlyMDM() {
		return isOnlyMDM;
	}

	public void setIsOnlyMDM(boolean isOnlyMDM) {
		this.isOnlyMDM = isOnlyMDM;
	}

	@XmlElement(name = "is_element_mgr")
	public boolean getIsElementMgr() {
		return isElementMgr;
	}

	public void setIsElementMgr(boolean isElementMgr) {
		this.isElementMgr = isElementMgr;
	}

	@XmlElement(name = "ssl_port")
	public String getSslPort() {
		return sslPort;
	}

	public void setSslPort(String sslPort) {
		this.sslPort = sslPort;
	}

	@XmlElement(name = "non_ssl_port")
	public String getNonSslPort() {
		return nonSslPort;
	}

	public void setNonSslPort(String nonSslPort) {
		this.nonSslPort = nonSslPort;
	}

	@XmlElement(name = "driver_class_name")
	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("StorageSystemTypeRestRep [storageTypeId=");
		builder.append(storageTypeId);
		builder.append(", storageTypeName=");
		builder.append(storageTypeName);
		builder.append(", storageTypeType=");
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
