/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.srdfcontroller;

import com.emc.storageos.model.block.Copy;
import com.emc.storageos.protectioncontroller.ProtectionController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

import java.net.URI;

/**
 * SRDF Controller
 */
public interface SRDFController extends ProtectionController {

    /**
     * Perform protection operation
     *
     * @param system SRDF protection system URI
     * @param copy volume
     * @param op operation to perform
     * @param task task object
     *
     * @throws InternalException
     */
    public void performProtectionOperation(URI system, Copy copy, String op, String task) throws InternalException;

    /**
     * Expand SRDF Devices
     * 
     * @param storage
     * @param pool
     * @param volumeId
     * @param size
     * @param token
     * @throws InternalException
     */
    public void expandVolume(URI storage, URI pool, URI volumeId, Long size, String token) throws InternalException;

}
