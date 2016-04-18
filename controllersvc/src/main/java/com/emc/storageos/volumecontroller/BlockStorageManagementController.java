/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;

import com.emc.storageos.exceptions.DeviceControllerException;
/**
 * The main API for managing block storage management controllers.
 * 
 */
public interface BlockStorageManagementController extends StorageController {

    /**
     * Add Storage system to SMIS Provider
     * 
     * @param storage : URI of the storage system to add to the providers
     * @param providers : array of URIs where this system must be added
     * @param primaryProvider : indicate if the first provider in the list must
     *            be treated as the active provider
     * @throws InternalException
     */
    public void addStorageSystem(URI storage, URI[] providers, boolean primaryProvider, String opId) throws InternalException;

    /**
     * Validate storage provider connection.
     * 
     * @param ipAddress the ip address
     * @param portNumber the port number
     * @param interfaceType
     * @return true, if successful
     */
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber, String interfaceType);

        
    public boolean updateStorageProviderConfig(String ipAddress,
            Integer portNumber, String userName, String password, String interfaceType) throws DeviceControllerException;        	

    
    public String getStorageProviderConfig(String ipAddress,
            Integer portNumber, String userName, String password, String interfaceType) throws DeviceControllerException;
    
}
