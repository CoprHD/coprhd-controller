/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;

public interface ExportMaskOperations {

    public void createExportMask(StorageSystem storage,
            URI exportMask,
            VolumeURIHLU[] volumeURIHLUs,
            List<URI> targetURIList,
            List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void deleteExportMask(StorageSystem storage,
            URI exportMask,
            List<URI> volumeURIList,
            List<URI> targetURIList,
            List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void addVolume(StorageSystem storage,
            URI exportMask,
            VolumeURIHLU[] volumeURIHLUs,
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void removeVolume(StorageSystem storage,
            URI exportMask,
            List<URI> volume,
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void addInitiator(StorageSystem storage,
            URI exportMask,
            List<Initiator> initiators,
            List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void removeInitiator(StorageSystem storage,
            URI exportMask,
            List<Initiator> initiators,
            List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * This call can be used to look up the passed in initiator/port names and find (if
     * any) to which export masks they belong on the 'storage' array.
     * 
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param initiatorNames [in] - Port identifiers (WWPN or iSCSI name)
     * @param mustHaveAllPorts [in] Indicates if true, *all* the passed in initiators
     *            have to be in the existing matching mask. If false,
     *            a mask with *any* of the specified initiators will be
     *            considered a hit.
     * @return Map of port name to Set of ExportMask URIs
     */
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames,
            boolean mustHaveAllPorts);

    /**
     * This call will be used to update the ExportMask with the latest data from the
     * array.
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param mask [in] - ExportMask object to be refreshed
     * @return instance of ExportMask object that has been refreshed with data from the
     *         array.
     */
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask);

    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception;

    /**
     * For the given ExportMask, go to the StorageArray and get a mapping of volumes to their HLUs
     *
     * @param storage the storage system
     * @param exportMask the ExportMask that represents the masking component of the array
     *
     * @return The BlockObject URI to HLU mapping for the ExportMask
     */
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask);
    
    /**
     * Remap initiators to target ports.
     * 
     * @param storage   The storage system
     * @param exportMaskURI The exportMask URI
     * @param initiatorTargets The map of initiator to storage ports
     * @param taskCompleter The task completer
     * @throws DeviceControllerException
     */
    public void remapInitiatorTargetPorts(StorageSystem storage,
            URI exportMaskURI,
            Map<URI, List<URI>> initiatorTargets,
            TaskCompleter taskCompleter) throws DeviceControllerException;
    
}
