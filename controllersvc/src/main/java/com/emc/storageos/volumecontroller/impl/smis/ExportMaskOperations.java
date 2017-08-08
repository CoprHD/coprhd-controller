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
import com.emc.storageos.db.client.model.PerformancePolicy;
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

    public void addVolumes(StorageSystem storage,
            URI exportMask,
            VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiatorList, 
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void removeVolumes(StorageSystem storage,
            URI exportMask,
            List<URI> volume,
            List<Initiator> initiatorList, 
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void addInitiators(StorageSystem storage,
            URI exportMask,
            List<URI> volumeURIs,
            List<Initiator> initiators,
            List<URI> targetURIs, 
            TaskCompleter taskCompleter) throws DeviceControllerException;

    public void removeInitiators(StorageSystem storage,
            URI exportMask,
            List<URI> volumeURIList,
            List<Initiator> initiators,
            List<URI> targetURIs, 
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
     * @throws DeviceControllerException TODO
     */
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames,
            boolean mustHaveAllPorts) throws DeviceControllerException;

    /**
     * This call will be used to update the ExportMask with the latest data from the
     * array.
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param mask [in] - ExportMask object to be refreshed
     * @return instance of ExportMask object that has been refreshed with data from the
     *         array.
     * @throws DeviceControllerException TODO
     */
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException;

    /**
     * This call will be used to find the HLUs that have been assigned for volumes
     * which are exported to the given list of initiators.
     *
     * @param storage the storage system
     * @param initiatorNames the initiator names
     * @param mustHaveAllPorts
     *            If true, *all* the passed in initiators have to be in the existing matching mask.
     *            If false, a mask with *any* of the specified initiators will be considered a hit.
     * @return the HLUs used for the given initiators
     */
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts);

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
     * Add paths to the export mask
     * 
     * @param storage - Storage system
     * @param exportMask - Export mask URI
     * @param newPaths - New paths to be added to the export mask
     * @param taskCompleter - Task completer
     * @throws Exception
     */
    public void addPaths(StorageSystem storage, URI exportMask,
            Map<URI, List<URI>> newPaths, TaskCompleter taskCompleter) throws DeviceControllerException;
    
    /**
     * Remove paths from the export mask
     * 
     * @param storage - Storage system
     * @param exportMaskURI - Export mask URI
     * @param adjustedPaths - Paths (new and/or retained) in the export mask
     * @param removePaths - Paths to be removed
     * @param taskCompleter - Task completer
     * @throws Exception
     */
    public void removePaths(StorageSystem storage, URI exportMaskURI, Map<URI, List<URI>> adjustedPaths,
            Map<URI, List<URI>> removePaths, TaskCompleter taskCompleter) throws DeviceControllerException;
    
    /**
     * TBD Heg
     * 
     * @param storage
     * @param exportMask
     * @param volumeURIs
     * @param newPerfPolicy
     * @param rollback
     * @param taskCompleter
     * @throws Exception
     */
    public void updatePerformancePolicy(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, PerformancePolicy newPerfPolicy, boolean rollback,
            TaskCompleter taskCompleter) throws Exception;
}
