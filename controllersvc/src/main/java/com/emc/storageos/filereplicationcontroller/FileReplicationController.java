/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;

import java.net.URI;
/**
 * 
 * class define to process File Mirror replication operations
 *
 */
public interface FileReplicationController extends Controller {
	
	/**
	 * Attach new mirror(s) for the given fileShare (local Mirror)
	 * @param storage
	 * @param sourceVolume
	 * @param opId
	 * @throws ControllerException
	 */
    public void attachNativeContinuousCopies(URI storage, URI sourceVolume, String opId) throws ControllerException;
}
