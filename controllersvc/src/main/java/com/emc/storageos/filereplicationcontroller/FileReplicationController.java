/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * 
 * class define to process File Mirror replication operations
 *
 */
public interface FileReplicationController extends Controller {

    /**
     * Perform remote protection operations
     * 
     * @param storage -source storage system
     * @param sourceFileShare -source file share
     * @param mirrorURIs - mirror file share
     * @param opType - operation performed
     * @param opId - task object
     * @throws ControllerException
     */
    public void performNativeContinuousCopies(URI storage, URI sourceFileShare, List<URI> mirrorURIs, String opType, String opId)
            throws ControllerException;

}
