package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Class represents Storage System Types.
 */
@SuppressWarnings("serial")
@Cf("StorageSystemType")
public class StorageSystemType extends DataObject {
	// Name of Storage Type, like VMAX, VNX, Isilion
	private String storageTypeName;

	// Storage type like Block, File or Object
	private String storageType;

	// Storage array is directly manage by CoprHD or thru SMI: Providers
	private Boolean isSmiProvider = false;
	
	// Storage array URI in string
	private String storageTypeId;

	@Name("storageTypeName")
	public String getStorageTypeName() {
		return storageTypeName;
	}

	public void setStorageTypeName(String name) {
		this.storageTypeName = name;
		setChanged("storageTypeName");
	}

	@Name("storageType")
	public String getStorageType() {
		return storageType;
	}

	public void setStorageType(String storageType) {
		this.storageType = storageType;
		setChanged("storageType");
	}

	@Name("isSmiProvider")
	public Boolean getIsSmiProvider() {
		return isSmiProvider;
	}

	public void setIsSmiProvider(Boolean isSmiProvider) {
		this.isSmiProvider = isSmiProvider;
		setChanged("isSmiProvider");
	}
	
	@Name("storageId")
	public String getStorageTypeId() {
		return storageTypeId;
	}

	public void setStorageTypeId(String storageId) {
		this.storageTypeId = storageId;
		setChanged("storageTypeId");
	}
	
    public static enum StorageType {
        block, file, object
    }

}
