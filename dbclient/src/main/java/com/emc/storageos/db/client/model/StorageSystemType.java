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
	private String storageTypeType;

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

	@Name("storageTypeType")
	public String getStorageTypeType() {
		return storageTypeType;
	}

	public void setStorageTypeType(String storageType) {
		this.storageTypeType = storageType;
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
	
    public static enum StorageType {
        block, file, object
    }

}
