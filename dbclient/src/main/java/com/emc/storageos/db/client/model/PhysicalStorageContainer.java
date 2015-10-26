package com.emc.storageos.db.client.model;

@Cf("PhysicalStorageContainer")
public class PhysicalStorageContainer extends StoragePool {

	private String physicalStorageContainerId;
	
	private String physicalStorageContainerName;

	@Name("physicalStorageContainerId")
	public String getPhysicalStorageContainerId() {
		return physicalStorageContainerId;
	}

	public void setPhysicalStorageContainerId(String physicalStorageContainerId) {
		this.physicalStorageContainerId = physicalStorageContainerId;
		setChanged("physicalStorageContainerId");
	}

	@Name("physicalStorageContainerName")
	public String getPhysicalStorageContainerName() {
		return physicalStorageContainerName;
	}

	public void setPhysicalStorageContainerName(String physicalStorageContainerName) {
		this.physicalStorageContainerName = physicalStorageContainerName;
		setChanged("physicalStorageContainerName");
	}
	
	
}
