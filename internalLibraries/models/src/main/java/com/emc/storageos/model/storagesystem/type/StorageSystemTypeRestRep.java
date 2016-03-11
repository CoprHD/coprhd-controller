package com.emc.storageos.model.storagesystem.type;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.DataObjectRestRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "storagesystem_type")
public class StorageSystemTypeRestRep extends DataObjectRestRep {

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

	@XmlElement(name = "storage_type_disp_name")
	public String getStorageTypeDispName() {
		return storageTypeDispName;
	}

	public void setStorageTypeDispName(String storageTypeDispName) {
		this.storageTypeDispName = storageTypeDispName;
	}

	@XmlElement(name = "isDefaultSsl")
	public boolean getIsDefaultSsl() {
		return isDefaultSsl;
	}

	public void setIsDefaultSsl(boolean isDefaultSsl) {
		this.isDefaultSsl = isDefaultSsl;
	}

	@XmlElement(name = "isDefaultMDM")
	public boolean getIsDefaultMDM() {
		return isDefaultMDM;
	}

	public void setIsDefaultMDM(boolean isDefaultMDM) {
		this.isDefaultMDM = isDefaultMDM;
	}

	@XmlElement(name = "isOnlyMDM")
	public boolean getIsOnlyMDM() {
		return isOnlyMDM;
	}

	public void setIsOnlyMDM(boolean isOnlyMDM) {
		this.isOnlyMDM = isOnlyMDM;
	}

	@XmlElement(name = "isElementMgr")
	public boolean getIsElementMgr() {
		return isElementMgr;
	}

	public void setIsElementMgr(boolean isElementMgr) {
		this.isElementMgr = isElementMgr;
	}

	@XmlElement(name = "sslPort")
	public String getSslPort() {
		return sslPort;
	}

	public void setSslPort(String sslPort) {
		this.sslPort = sslPort;
	}

	@XmlElement(name = "nonSslPort")
	public String getNonSslPort() {
		return nonSslPort;
	}

	public void setNonSslPort(String nonSslPort) {
		this.nonSslPort = nonSslPort;
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
		builder.append(", isDefaultSsl=");
		builder.append(isDefaultSsl);
		builder.append(", nonSslPort=");
		builder.append(nonSslPort);
		builder.append(", sslPort=");
		builder.append(sslPort);
		builder.append("]");
		return builder.toString();
	}
}
