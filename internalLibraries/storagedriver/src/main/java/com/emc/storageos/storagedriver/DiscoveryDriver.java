/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;


import java.util.List;

import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageProvider;
import org.apache.commons.lang.mutable.MutableInt;

import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;

/**
 * Set of methods for discovery.
 * When method return DriverTask, there are two options for implementation --- blocking and non-blocking.
 * For non-blocking implementation, return task in intermediate state (QUEUED/PROVISIONING). Client will poll task until task
 * is set to one of terminal states or until polling timeout was reached.
 * For blocking implementation return task in one of terminal states.
 * When client sees task in terminal state, client assumes that driver completed request and all required data is set
 * in Output arguments.
 *
 * When method is not supported, return DriverTask in FAILED state with message indicating that operation is not supported.
 *
 */
public interface DiscoveryDriver extends StorageDriver {

    /**
     * Discover storage system and it's capabilities
     *
     * @param storageSystem StorageSystem to discover. Type: Input/Output.
     * @return
     */
    public DriverTask discoverStorageSystem(StorageSystem storageSystem);

    /**
     * Discover storage pools and their capabilities.
     * @param storageSystem Type: Input.
     * @param storagePools  Type: Output.
     * @return
     */
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools);

    /**
     * Discover storage ports and their capabilities
     * @param storageSystem Type: Input.
     * @param storagePorts  Type: Output.
     * @return
     */
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts);


    /**
     * Discover host components which are part of storage system
     *
     * @param storageSystem Type: Input.
     * @param embeddedStorageHostComponents Type: Output.
     * @return
     */
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents);

    /**
     * Discover storage provider and storage systems under this provider management.
     * This operation is similar to provider scan.
     * For managed storage systems driver should return key connection properties required to run detailed discovery.

     * @param storageProvider Type: Input/Output.
     * @param storageSystems  Type: Output.
     * @return driver task
     */
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems);
}
