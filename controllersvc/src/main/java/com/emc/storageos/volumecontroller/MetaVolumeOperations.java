/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.util.List;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * 
 * This interface defines all the meta volume supported operations. This should
 * be implemented by specific device meta volume operations and implement the
 * device specific logic.
 * 
 */
public interface MetaVolumeOperations {

    /**
     * Create meta volume head device. Meta volume is represented by its head.
     * We create it as a regular bound volume.
     * 
     * @param storageSystem
     * @param storagePool
     * @param metaHead
     * @param capacity
     * @param capabilities
     * @param metaVolumeTaskCompleter
     * @throws Exception
     */
    public void createMetaVolumeHead(StorageSystem storageSystem,
            StoragePool storagePool, Volume metaHead, long capacity,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception;

    /**
     * Create meta volume member devices. These devices provide capacity to meta
     * volume. SMI-S requires that these devices be created unbound form a pool.
     * 
     * @param storageSystem
     * @param storagePool
     * @param metaHead
     * @param memberCount
     * @param memberCapacity
     * @param metaVolumeTaskCompleter
     * @return list of native ids of meta member devices
     * @throws Exception
     */
    public List<String> createMetaVolumeMembers(StorageSystem storageSystem,
            StoragePool storagePool, Volume metaHead, int memberCount,
            long memberCapacity, MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception;

    /**
     * Create meta volume from provided meta head and meta members
     * 
     * @param storageSystem
     *            storageSystem
     * @param metaHead
     *            meta head
     * @param metaMembers
     *            list of native ids of meta volume members (not including meta
     *            head)
     * @param metaType
     *            meta volume type to create, concatenate or stripe
     * @param capabilities
     *            capabilities
     * @param metaVolumeTaskCompleter
     *            task completer
     */
    public void createMetaVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume metaHead, List<String> metaMembers, String metaType,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception;

    /**
     * Create multiple meta volumes with the same characteristics.
     *
     * @param storageSystem
     * @param storagePool
     * @param volumes
     * @param capabilities
     * @param taskCompleter
     * @throws Exception
     */
    public void createMetaVolumes(StorageSystem storageSystem, StoragePool storagePool, List<Volume> volumes,
                                  VirtualPoolCapabilityValuesWrapper capabilities, TaskCompleter taskCompleter) throws Exception;

        /**
         * Expand meta volume.
         *
         * @param storageSystem
         * @param storagePool
         * @param metaHead
         * @param newMetaMembers
         * @param metaVolumeTaskCompleter
         * @throws DeviceControllerException
         */
    public void expandMetaVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume metaHead, List<String> newMetaMembers, MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception;

    /**
     * Expand volume as meta volume.
     * 
     * @param storageSystem
     * @param storagePool
     * @param metaHead
     * @param metaMembers
     * @param metaType
     * @param metaVolumeTaskCompleter
     * @throws Exception
     */
    void expandVolumeAsMetaVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume metaHead, List<String> metaMembers, String metaType,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception;
    
    /**
     * Defines meta volume type for volume expansion.
     * @param storageSystem
     * @param volume
     * @param metaVolumeType
     * @param metaVolumeTaskCompleter
     * @return
     * @throws Exception
     */
    public String defineExpansionType(StorageSystem storageSystem, Volume volume,
            String metaVolumeType, MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception;

    public void deleteBCVHelperVolume(StorageSystem storageSystem, Volume volume)
            throws Exception;

}
