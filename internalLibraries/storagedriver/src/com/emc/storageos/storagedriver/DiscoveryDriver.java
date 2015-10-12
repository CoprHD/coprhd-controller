package com.emc.storageos.storagedriver;


import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.apache.commons.lang.mutable.MutableInt;

import java.util.List;

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
public interface DiscoveryDriver extends StorageDriver{

    /**
     *  Get capability metadata for available capabilities
     */
    public List<CapabilityDefinition> getCapabilityDefinitions();

    /**
     * Discover storage systems and their capabilities
     *
     * @param storageSystems StorageSystems to discover. Type: Input/Output.
     * @return
     */
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems);

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
    public DriverTask getStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts);

    /**
     * Discover storage volumes
     * @param storageSystem  Type: Input.
     * @param storageVolumes Type: Output.
     * @param token used for paging. Input 0 indicates that the first page should be returned. Output 0 indicates
     *              that last page was returned. Type: Input/Output.
     * @return
     */
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StoragePort> storageVolumes, MutableInt token);

}
