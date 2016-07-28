/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.protectionorchestrationcontroller;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.srdfcontroller.SRDFDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.VPlexSrdfUtil;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.vplexcontroller.VPlexConsistencyGroupManager;
import com.emc.storageos.vplexcontroller.VPlexDeviceController;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;

/**
 * This class orchestrates Protection Operations across various devices. The immediate use
 * was to orchestrate SRDF protection operations with the Vplex.
 * Please feel free to add Protection orchestrations here (including RP).
 * The parameters need not necessarily be VolumeDescriptors.
 *
 */
public class ProtectionOrchestrationDeviceController implements ProtectionOrchestrationController {
    private static final Logger s_logger = LoggerFactory.getLogger(ProtectionOrchestrationDeviceController.class);
    private static SRDFDeviceController srdfDeviceController;
    private static VPlexDeviceController vplexDeviceController;
    private static VPlexConsistencyGroupManager vplexConsistencyGroupManager;
    private static WorkflowService workflowService;
    private static DbClient dbClient;
    
    static final String SRDF_PROTECTION_OPERATION = "SRDF_PROTECTION_OPERATION";
    private final String[] srdfFlushableOps = { "failover", "failover-cancel", "swap", "resume" };
    private final String [] srdfSetReadOnlyOps = { "failover", "failover-cancel", "swap" };
    
    @Override
    public void performSRDFProtectionOperation(URI storageSystemId, Copy copy, String op, String task) {
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemId);
        // Maps Vplex volume that needs to be flushed to underlying array volume
        Map<Volume, Volume> vplexToArrayVolumesToFlush = getVplexVolumesToBeCacheFlushed(copy, op);
        if (vplexToArrayVolumesToFlush.isEmpty()) {
            srdfDeviceController.performProtectionOperation(storageSystemId, copy, op, task);
        } else {
            // There are volumes that require flushing. Create a workflow to do so.
            String waitFor = null;
            List<URI> volumeURIs = getCompleterVolumesForSRDFProtectionOperaton(copy);
            VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volumeURIs, task);
            try {
                Workflow workflow = workflowService.getNewWorkflow(this,
                        "performSRDFProtectionOperation", true, task);
                
                // If there source volumes in a CG, mark them read-only before we start if needed
                StringBuilder volNames = new StringBuilder();
                List<URI> readOnlyVolumes = getVPlexVolumesToMarkReadOnly(vplexToArrayVolumesToFlush, op, volNames);
                waitFor = vplexConsistencyGroupManager.addStepForUpdateConsistencyGroupReadOnlyState(
                        workflow, readOnlyVolumes, true, "Set CG state to read-only: " + volNames, waitFor);

                // Add vplex pre flush steps. 
                Map<URI, String> vplexVolumeIdToDetachStep = new HashMap<URI, String>();
                waitFor = vplexDeviceController.addPreRestoreResyncSteps(workflow, 
                        vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, waitFor);

                // Add a step for the SRDF operation.
                Workflow.Method performProtectionOperationMethod = 
                        srdfDeviceController.performProtectionOperationMethod(storageSystemId, copy, op);
                Workflow.Method nullRollbackMethod = 
                        srdfDeviceController.rollbackMethodNullMethod();
                String srdfStep = workflow.createStep(SRDF_PROTECTION_OPERATION, 
                        "SRDFProtectionOperation: " + op, waitFor, 
                        storageSystemId, storageSystem.getSystemType(), false, 
                        srdfDeviceController.getClass(), performProtectionOperationMethod, 
                        nullRollbackMethod, null);

                // Add post-flush steps.If all are Vplex local volumes, nothing will be added.
                waitFor = vplexDeviceController.addPostRestoreResyncSteps(workflow, 
                        vplexToArrayVolumesToFlush, vplexVolumeIdToDetachStep, srdfStep);
                
                // If there target volumes in a CG, mark them read-write if-needed now that we are done
                volNames = new StringBuilder();
                List<URI> readWriteVolumes = getVPlexVolumesToMarkReadWrite(vplexToArrayVolumesToFlush, op, volNames);
                waitFor = vplexConsistencyGroupManager.addStepForUpdateConsistencyGroupReadOnlyState(
                        workflow, readWriteVolumes, false, "Set CG state to read-write: " + volNames, waitFor);

                // Execute workflow.
                workflow.executePlan(completer, 
                        "Sucessful workflow for SRDF Protection Operation" + copy.getCopyID().toString());
            } catch (Exception ex) {
                s_logger.error("Could not create workflow", ex);
                ServiceError error = DeviceControllerException.errors.jobFailed(ex);
                completer.error(dbClient, error);
            }
        }
    }
    
   
    /**
     * Returns true if the SRDF operation requires a cache flush on the Vplex.
     * @param op
     * @return
     */
    private boolean srdfOpRequiresVplexCacheFlush(String op) {
        return Arrays.asList(srdfFlushableOps).contains(op);
    }
    
    /**
     * Returns true if the SRDF operations requires changing the CG read-only flag.
     * @param op
     * @return
     */
    private boolean srdfOpRequresReadOnlyChange(String op) {
        return Arrays.asList(srdfSetReadOnlyOps).contains(op);
    }
    
    /**
     * Returns a map of Vplex volume that needs to be cache flushed to the underlying array volume that
     * will be updated after the cache flush.
     * @param copy
     * @param op
     * @return map of Vplex Volume to be flushed to associated array volume that will be updated
     */
    private Map<Volume, Volume> getVplexVolumesToBeCacheFlushed(Copy copy, String op) {
        // Map of Vplex Volume to array volume that will be updated with protection operation.
        Map<Volume, Volume> vplexToArrayVolumes = new HashMap<Volume, Volume>();
        Set<URI> addedVolumes = new HashSet<URI>();
        // Determine if the operation requires a flush.
        if (!srdfOpRequiresVplexCacheFlush(op)) {
            s_logger.info("Not a flushable op: " + op);
            return vplexToArrayVolumes;
        }
        // Get the volume with access state NOT_READY. 
        // This is the one that needs to be flushed as it may become ready.
        Volume protoVolume = determineAccessStateNotReadyVolume(copy.getCopyID());
        if (protoVolume == null) {
            s_logger.info("No volume with access state NOT_READY");
            return vplexToArrayVolumes;
        }
        // See if there is a corresponding Vplex volume.
        Volume vplexVolume = VPlexSrdfUtil.getVplexVolumeFromSrdfVolume(dbClient, protoVolume);
        if (vplexVolume == null) {
            s_logger.info("Copy volume not VPLEX protected");
            return vplexToArrayVolumes;
        }
        vplexToArrayVolumes.put(vplexVolume, protoVolume);
        addedVolumes.add(vplexVolume.getId());
        // Determine if target volume is in a CG, and if so, get related SRDF volumes.
        if (protoVolume.getConsistencyGroup() != null) {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, protoVolume.getConsistencyGroup());
            // Find all the volumes in that same consistency group
            List<Volume> cgVolumes = BlockConsistencyGroupUtils.getActiveNonVplexVolumesInCG(
                    cg, dbClient, null);
            // Loop through the CG volumes on the same storage system, adding the Vplex equivalent volume to the set to be flushed
            for (Volume cgVolume : cgVolumes) {
                if (cgVolume.getStorageController().equals(protoVolume.getStorageController())) {
                    vplexVolume = VPlexSrdfUtil.getVplexVolumeFromSrdfVolume(dbClient, cgVolume);
                    if (vplexVolume != null && !addedVolumes.contains(vplexVolume.getId())) {
                        vplexToArrayVolumes.put(vplexVolume, cgVolume);
                        addedVolumes.add(vplexVolume.getId());
                    }
                }
            }
        }
        // Log volumes to be flushed.
        s_logger.info("VPlex volumes to be flushed: ");
        for (Volume volume : vplexToArrayVolumes.keySet()) {
            s_logger.info(volume.getLabel() + " (" + volume.getId() + ")");
        }
        return vplexToArrayVolumes;
    }
    
    /**
     * Generate the list of volumes needed for the Workflow completer.
     * @param copy - Copy parameter
     * @return - List of volume URIs
     */
    private List<URI> getCompleterVolumesForSRDFProtectionOperaton(Copy copy) {
        Volume volume = dbClient.queryObject(Volume.class, copy.getCopyID());
        List<String> targetVolumeUris = new ArrayList<String>();
        List<URI> combined = new ArrayList<URI>();
        if (PersonalityTypes.SOURCE.toString().equalsIgnoreCase(volume.getPersonality())) {
            targetVolumeUris.addAll(volume.getSrdfTargets());
            URI sourceVolumeUri = volume.getId();
            combined.add(sourceVolumeUri);
            combined.addAll(transform(volume.getSrdfTargets(), FCTN_STRING_TO_URI));
        } else {
            URI sourceVolumeUri = volume.getSrdfParent().getURI();
            targetVolumeUris.add(volume.getId().toString());
            combined.add(sourceVolumeUri);
            combined.add(volume.getId());
        }
        return combined;
    }
    
    /**
     * Determine the corresponding volume with a access state of NOT_READY..
     * @param volumeURI URI of volume to start with
     * @return Corresponding volume with Target personality (may be same volume), or maybe null if not found
     */
    private Volume determineAccessStateNotReadyVolume(URI volumeURI) {
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        if (volume.getAccessState().equals(Volume.VolumeAccessState.NOT_READY.name())) {
            return volume;
        }
        // There should be a srdf source.
        Volume srdfParent = null;
        if (volume.getSrdfParent().getURI() != null) {
            srdfParent = dbClient.queryObject(Volume.class, volume.getSrdfParent().getURI());
            if (srdfParent.getAccessState().equals(Volume.VolumeAccessState.NOT_READY.name())) {
                return srdfParent;
            }
        }
        s_logger.info(String.format("No NOT_READY volume corresponding to %s (%s)", volume.getLabel(), volume.getId()));
        return null;
    }
    
    /**
     * Given the map for vplex volumes to srdf volumes to be flushed,
     * returns a list of the URIs representing vplex volumes that are in consistency group
     * that virtualize the current srdf source volumes. These vplex volumes need to be marked
     * read-only as after the protection operation they will not be writeable.
     * @param vplexVolumesToBeCacheFlushed - Map returned from getVplexVolumesToBeCacheFlushed
     * @param volumeNames OUT parameter containing list of volume names
     * @param op String operation type
     * @return list of URIs of Vplex volumes to be marked read-only
     */
    private List<URI> getVPlexVolumesToMarkReadOnly(Map<Volume, Volume> vplexVolumesToBeCacheFlushed,
            String op, StringBuilder volumeNames) {
        List<URI> readOnlyVolumes = new ArrayList<URI>();
        // Determine if the operation requires a changing read-only flag..
        if (!srdfOpRequresReadOnlyChange(op)) {
            s_logger.info("Op doesn't require read-only change: " + op);
            return readOnlyVolumes;
        }
        // For each underlying volume to be cache flushed, determine the corresponding source vplex volume
        for (Volume volume : vplexVolumesToBeCacheFlushed.values()) {
            if (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())) {
                Volume srdfSource = dbClient.queryObject(Volume.class, volume.getSrdfParent().getURI());
                if (srdfSource != null && !srdfSource.getInactive()) {
                    // Get associated Vplex volume, and add it if it's in a consistency group
                    Volume vplexSource = VPlexSrdfUtil.getVplexVolumeFromSrdfVolume(dbClient, srdfSource);
                    if (vplexSource != null && !vplexSource.getInactive() 
                            && !NullColumnValueGetter.isNullURI(vplexSource.getConsistencyGroup())) {
                        s_logger.info("Added to list to be marked read-only: " + vplexSource.getLabel());
                        readOnlyVolumes.add(vplexSource.getId());
                        volumeNames.append(vplexSource.getLabel() + " ");
                    } else if (vplexSource != null) {
                        s_logger.info("Volume inactive or not in CG: " + vplexSource.getLabel());
                    }
                } 
            } else if (volume.getSrdfTargets() != null) {
                for (String target : volume.getSrdfTargets()) {
                    Volume srdfTarget = dbClient.queryObject(Volume.class, URI.create(target));
                    if (srdfTarget != null && !srdfTarget.getInactive()) {
                        // Get associated Vplex volume, and add it if it's in a consistency group
                        Volume vplexTarget = VPlexSrdfUtil.getVplexVolumeFromSrdfVolume(dbClient, srdfTarget);
                        if (vplexTarget != null && !vplexTarget.getInactive() 
                                && !NullColumnValueGetter.isNullURI(vplexTarget.getConsistencyGroup())) {
                            s_logger.info("Added to list to be marked read-only: " + vplexTarget.getLabel());
                            readOnlyVolumes.add(vplexTarget.getId());
                            volumeNames.append(vplexTarget.getLabel());
                        } else if (vplexTarget != null) {
                            s_logger.info("Volume inactive or not in CG: " + vplexTarget.getLabel());
                        }
                    }
                }
            }
        }
        return readOnlyVolumes;
    }
    
    /**
     * Returns a list of VPlex Volume URIs that should be marked read-write. These are the volumes that
     * are cache flushed and also in a consistency group.
     * @param vplexVolumesToBeCacheFlushed - Map returned from getVplexVolumesToBeCacheFlushed
     * @param op - String protection operation
     * @param volumeNames - OUT parameter of volume names for logging
     * @return list of URIs of Vplex volumes to be marked read-write
     */
    private List<URI> getVPlexVolumesToMarkReadWrite(Map<Volume, Volume> vplexVolumesToBeCacheFlushed, 
            String op, StringBuilder volumeNames) {
        List<URI> readWriteVolumes = new ArrayList<URI>();
         // Determine if the operation requires a changing read-only flag..
        if (!srdfOpRequresReadOnlyChange(op)) {
            s_logger.info("Op doesn't require read-only change " + op);
            return readWriteVolumes;
        }
        for (Volume vplexVolume : vplexVolumesToBeCacheFlushed.keySet()) {
            if (vplexVolume != null && !vplexVolume.getInactive() 
                    && !NullColumnValueGetter.isNullURI(vplexVolume.getConsistencyGroup())) {
                s_logger.info("Added to list to be marked read-write: " + vplexVolume.getLabel());
                readWriteVolumes.add(vplexVolume.getId());
                volumeNames.append(vplexVolume.getLabel() + " ");
            } else if (vplexVolume != null) {
                s_logger.info("Volume inactive or not in CG: " + vplexVolume.forDisplay());
            }
        }
        return readWriteVolumes;
    }
    
    public static SRDFDeviceController getSrdfDeviceController() {
        return srdfDeviceController;
    }
    public static void setSrdfDeviceController(SRDFDeviceController srdfDeviceController) {
        ProtectionOrchestrationDeviceController.srdfDeviceController = srdfDeviceController;
    }
    public static VPlexDeviceController getVplexDeviceController() {
        return vplexDeviceController;
    }
    public static void setVplexDeviceController(VPlexDeviceController vplexDeviceController) {
        ProtectionOrchestrationDeviceController.vplexDeviceController = vplexDeviceController;
    }

    public static WorkflowService getWorkflowService() {
        return workflowService;
    }

    public static void setWorkflowService(WorkflowService workflowService) {
        ProtectionOrchestrationDeviceController.workflowService = workflowService;
    }

    public static DbClient getDbClient() {
        return dbClient;
    }

    public static void setDbClient(DbClient dbClient) {
        ProtectionOrchestrationDeviceController.dbClient = dbClient;
    }

    public static VPlexConsistencyGroupManager getVplexConsistencyGroupManager() {
        return vplexConsistencyGroupManager;
    }

    public static void setVplexConsistencyGroupManager(VPlexConsistencyGroupManager vplexConsistencyGroupManager) {
        ProtectionOrchestrationDeviceController.vplexConsistencyGroupManager = vplexConsistencyGroupManager;
    }
}
