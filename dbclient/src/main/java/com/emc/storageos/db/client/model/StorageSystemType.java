package com.emc.storageos.db.client.model;

/**
 * Class represents Storage System Types.
 */
@SuppressWarnings("serial")
@Cf("StorageSystemType")
public class StorageSystemType extends DataObject {
	// Name of Storage Type, like VMAX, VNX, Isilion
	private String storageTypeName;

	// Display Name of Storage Type, like VMAX, VNX, Isilion
	private String storageTypeDispName;

	// Storage type like Block, File or Object
	private String storageTypeType;

	// Storage array is directly manage by CoprHD or thru SMI: Providers
	private Boolean isSmiProvider = false;

	// Storage array URI in string
	private String storageTypeId;

	// Default SSL
	private Boolean isDefaultSsl = false;

	private Boolean isDefaultMDM = false;
	private Boolean isOnlyMDM = false;
	private Boolean isElementMgr = false;

	private String sslPort;
	private String nonSslPort;
	private String driverClassName;

	public static enum storageSupportedType {
		block, file, object, all
	}

	@Name("storageTypeName")
	public String getStorageTypeName() {
		return storageTypeName;
	}

	public void setStorageTypeName(String name) {
		this.storageTypeName = name;
		setChanged("storageTypeName");
	}

	@Name("storageTypeDispName")
	public String getStorageTypeDispName() {
		return storageTypeDispName;
	}

	public void setStorageTypeDispName(String name) {
		this.storageTypeDispName = name;
		setChanged("storageTypeDispName");
	}

	@Name("storageTypeType")
	public String getStorageTypeType() {
		return storageTypeType;
	}

	public void setStorageTypeType(String storageTypeType) {
		this.storageTypeType = storageTypeType;
		setChanged("storageTypeType");
	}

	@Name("isSmiProvider")
	public Boolean getIsSmiProvider() {
		return isSmiProvider;
	}

	public void setIsSmiProvider(Boolean isSmiProvider) {
		this.isSmiProvider = isSmiProvider;
		setChanged("isSmiProvider");
	}

	@Name("storageTypeId")
	public String getStorageTypeId() {
		return storageTypeId;
	}

	public void setStorageTypeId(String storageId) {
		this.storageTypeId = storageId;
		setChanged("storageTypeId");
	}

	@Name("isDefaultSsl")
	public Boolean getIsDefaultSsl() {
		return isDefaultSsl;
	}

	public void setIsDefaultSsl(Boolean isDefaultSsl) {
		this.isDefaultSsl = isDefaultSsl;
		setChanged("isDefaultSsl");
	}

	@Name("isDefaultMDM")
	public Boolean getIsDefaultMDM() {
		return isDefaultMDM;
	}

	public void setIsDefaultMDM(Boolean isDefaultMDM) {
		this.isDefaultMDM = isDefaultMDM;
		setChanged("isDefaultMDM");
	}

	@Name("isOnlyMDM")
	public Boolean getIsOnlyMDM() {
		return isOnlyMDM;
	}

	public void setIsOnlyMDM(Boolean isOnlyMDM) {
		this.isOnlyMDM = isOnlyMDM;
		setChanged("isOnlyMDM");
	}

	@Name("isElementMgr")
	public Boolean getIsElementMgr() {
		return isElementMgr;
	}

	public void setIsElementMgr(Boolean isElementMgr) {
		this.isElementMgr = isElementMgr;
		setChanged("isElementMgr");
	}

	@Name("sslPort")
	public String getSslPort() {
		return sslPort;
	}

	public void setSslPort(String sslPort) {
		this.sslPort = sslPort;
		setChanged("sslPort");
	}

	@Name("nonSslPort")
	public String getNonSslPort() {
		return nonSslPort;
	}

	public void setNonSslPort(String nonSslPort) {
		this.nonSslPort = nonSslPort;
		setChanged("nonSslPort");
	}

	@Name("driverClassName")
	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
		setChanged("driverClassName");
	}
}
