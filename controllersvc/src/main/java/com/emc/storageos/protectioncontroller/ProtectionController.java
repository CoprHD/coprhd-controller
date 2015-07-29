/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.protectioncontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

/**
 * Protection Base Controller Interface
 */
public interface ProtectionController extends Controller {

    /**
     * Connect to the protection controller with the given address and credentials.
     * 
     * @param protection URI for the protection controller.
     */
    public void connect(URI protection) throws InternalException;

    /**
     * Disconnect from the protection controller.
     * 
     * @param protection URI of the protection controller.
     */
    public void disconnect(URI protection) throws InternalException;

    /**
     * Discover the given protection device or protection set which is registered with Bourne
     * 
     * @param tasks for discovery jobs.
     * @throws InternalException
     */
    public void discover(AsyncTask[] tasks) throws InternalException;

}
