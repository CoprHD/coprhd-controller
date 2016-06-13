/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.workflow.Workflow;

/**
 * Interfaces defined here will attempt to create a workflow that will cover the
 * different mapping/masking use-cases.
 */
public interface MaskingOrchestrator {

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param initiatorURIs
     * @param volumeMap
     * @param token @return
     */
    public void exportGroupCreate(URI storageURI,
            URI exportGroupURI,
            List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap,
            String token) throws Exception;

    /**
     * Perform required updated on a storage system for an export group update
     * 
     * @param storageURI the storage system
     * @param exportGroupURI the export group
     * @param storageWorkflow the overall workflow for the export group update
     * @param token the taskId
     * @throws Exception
     */
    public void exportGroupUpdate(URI storageURI,
            URI exportGroupURI,
            Workflow storageWorkflow,
            String token) throws Exception;

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param token @return
     */
    public void exportGroupDelete(URI storageURI,
            URI exportGroupURI,
            String token) throws Exception;

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param initiators
     * @param token @return
     */
    public void exportGroupAddInitiators(URI storageURI,
            URI exportGroupURI,
            List<URI> initiators,
            String token) throws Exception;

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param initiators
     * @param token @return
     */
    public void exportGroupRemoveInitiators(URI storageURI,
            URI exportGroupURI,
            List<URI> initiators,
            String token) throws Exception;

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param volumeMap
     * @param token @return
     */
    public void exportGroupAddVolumes(URI storageURI,
            URI exportGroupURI,
            Map<URI, Integer> volumeMap,
            String token) throws Exception;

    /**
     * @param storageURI
     * @param exportGroupURI
     * @param volumes
     * @param token @return
     */
    public void exportGroupRemoveVolumes(URI storageURI,
            URI exportGroupURI,
            List<URI> volumes,
            String token) throws Exception;

    /**
     * Find and update free HLUs for cluster export.
     *
     * @param exportGroup the export group
     * @param initiatorNames the initiator names
     * @param volumeMap the volume URI to HLU map
     */
    public void findAndUpdateFreeHLUsForClusterExport(ExportGroup exportGroup, List<String> initiatorNames, Map<URI, Integer> volumeMap)
            throws Exception;

    /**
     * Update the Path Parameters for the volume specified in any of the Export Mask(s)
     * in the Export Group specified.
     * 
     * @param storageURI -- URI of Storage System containing the volume.
     * @param exportGroupURI -- URI of Export Group to be processed
     * @param volumeURI -- URI of volume that is changing Vpool associations.
     * @param token -- String for completer
     */
    public void exportGroupChangePathParams(URI storageURI,
            URI exportGroupURI,
            URI volumeURI,
            String token) throws Exception;

    /**
     * Increase the maximum paths for a given Export Mask. This will add additional
     * initiators and storage ports if possible to the Masking View, Storage View, etc.
     * 
     * @param workflow -- Workflow the steps should be added to
     * @param storageSystem -- StorageSystem containing the Export Mask
     * @param exportGroup -- ExportGroup containing the ExportMask
     * @param exportMask -- The ExportMask object
     * @param newInitiators -- A list of new Initiator URIs to be assigned
     * @param token -- Operation toekn for completer
     * @throws Exception
     */
    public void increaseMaxPaths(Workflow workflow, StorageSystem storageSystem,
            ExportGroup exportGroup, ExportMask exportMask,
            List<URI> newInitiators, String token) throws Exception;
}
