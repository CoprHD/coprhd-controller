/**
 * 
 */
package com.emc.storageos.db.client.model;

/**
 * @author singhc1
 *
 */
@Cf("VirtualStorageContainer")
public class VirtualStorageContainer extends VirtualPool {

    private String storageContainerName;
    
    private String maxvVolSizeMB;
    
    private String protocolEndPointType;
	
}
