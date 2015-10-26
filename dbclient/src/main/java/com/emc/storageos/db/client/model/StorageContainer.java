/**
 * 
 */
package com.emc.storageos.db.client.model;

/**
 * @author singhc1
 *
 */
@Cf("StorageContainer")
public class StorageContainer extends VirtualPool {

    private String storageContainerName;
    
    private String maxvVolSizeMB;
    
    private String protocolEndPointType;
    
    @Name("storageContainerName")
	public String getStorageContainerName() {
		return storageContainerName;
	}

	public void setStorageContainerName(String storageContainerName) {
		this.storageContainerName = storageContainerName;
		setChanged("storageContainerName");
	}

	@Name("maxvVolSizeMB")
	public String getMaxvVolSizeMB() {
		return maxvVolSizeMB;
	}

	public void setMaxvVolSizeMB(String maxvVolSizeMB) {
		this.maxvVolSizeMB = maxvVolSizeMB;
		setChanged("maxvVolSizeMB");
	}

	@Name("protocolEndPointType")
	public String getProtocolEndPointType() {
		return protocolEndPointType;
	}

	public void setProtocolEndPointType(String protocolEndPointType) {
		this.protocolEndPointType = protocolEndPointType;
		setChanged("protocolEndPointType");
	}
	
}
