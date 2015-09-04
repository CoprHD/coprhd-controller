/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * The main API for managing storage controller connections.
 *
 * URI storage: The following information will be available from storage URI lookup.
 * ip: IP address of storage controller.
 * credentials: Storage controller access credentials.
 * profile: Storage controller access profile.
 *
 * URI pool: The following information will be available from pool URI lookup.
 * id: Pool identifier.
 * type: Pool type.
 */
public interface StorageController extends Controller {
    /**
     * Connect to the storage controller with the given address and credentials.
     *
     * @param storage URI for the storage controller.
     */
    public void connectStorage(URI storage) throws InternalException;

    /**
     * Disconnect from the storage controller.
     *
     * @param storage URI of the storage controller.
     */
    public void disconnectStorage(URI storage) throws InternalException;

    /**
     * Discover the given storageSystem, which are registered with Bourne
     * 
     * @param tasks for discovery job.
     * @throws InternalException
     */
    public void discoverStorageSystem(AsyncTask[] tasks) throws InternalException;

    /**
     * Scan all SMI-S providers. All providers must be scanned as a single job.
     * 
     * @param tasks List of tasks.
     * @throws InternalException
     */
    public void scanStorageProviders(AsyncTask[] tasks) throws InternalException;

    /**
     * Start monitoring from the indication source.
     * 
     * @param task Task
     * @throws InternalException
     */
    public void startMonitoring(AsyncTask task, Type deviceType) throws InternalException;
}
