/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.workflow.Workflow;

/**
 * Class to hold contextual data for applying the selection rules for
 * ExportMasks that match volumes that we want to export
 */
public class VmaxVolumeToExportMaskApplicatorContext {
    // INPUT:
    // readonly data
    public Map<String, Set<URI>> initiatorToExportMaskPlacementMap;
    public boolean zoningStepNeeded;
    public String previousStep;
    public boolean flowCreated;
    public Workflow workflow;
    public ExportGroup exportGroup;
    public StorageSystem storage;
    public Map<URI, Integer> volumeMap;
    public String token;
    public Map<URI, ExportMaskPolicy> exportMaskURIToPolicy;
    public Set<URI> partialMasks;
    // read/write
    public AbstractDefaultMaskingOrchestrator.InitiatorHelper initiatorHelper;
    public List<URI> initiatorURIsCopy;
    public Set<URI> initiatorsForNewExport;

    // OUTPUT:
    // Generated contextual data
    public Map<ExportMask, ExportMaskPolicy> exportMaskToPolicy;
    public Map<URI, Map<URI, Integer>> masksToUpdateWithVolumes;
    public Map<URI, Set<Initiator>> masksToUpdateWithInitiators;
    // Result/status
    public boolean resultSuccess;
}
