/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getDataObject;
import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getVPlexAPIClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.locking.DistributedOwnerLockService;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.utils.ClusterConsistencyGroupWrapper;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexConsistencyGroupInfo;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class RPVplexConsistencyGroupManager extends AbstractConsistencyGroupManager {

    private static final String ADD_VOLUME_TO_CG_METHOD_NAME = "addVolumeToCG";
    private static final String REMOVE_VOLUME_FROM_CG_METHOD_NAME = "removeVolumeFromCG";
    
    // logger reference.
    private static final Logger log = LoggerFactory
        .getLogger(RPVplexConsistencyGroupManager.class);
    
    @Override
    public String addStepsForCreateConsistencyGroup(Workflow workflow, String waitFor,
        StorageSystem vplexSystem, List<URI> vplexVolumeURIs,
        boolean willBeRemovedByEarlierStep) throws ControllerException {
        // No volumes, all done.
        if (vplexVolumeURIs.isEmpty()) {
            log.info("No volumes specified consistency group.");
            return waitFor;
        }
       
        // Grab the first volume
        Volume firstVolume = getDataObject(Volume.class, vplexVolumeURIs.get(0), dbClient);
        URI cgURI = firstVolume.getConsistencyGroup();     
        
        URI vplexURI = vplexSystem.getId();
        String nextStep = waitFor;
        int createCgStepCounter = 0;
        
        // Load the ViPR consistency group. This single ViPR consistency group will map to
        // potentially several different VPlex consistency groups.
        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);
        
        for (URI vplexVolumeURI : vplexVolumeURIs) {
            createCgStepCounter++;
            
            // The volume create steps need to complete before we can determine exactly
            // what VPlex cluster/consistency group can be created.  We determine the
            // VPlex cluster/cg information based off volume information (nativeGuid, wwn)
            // which doesn't get populated until the VPlex virtual volumes get created.
            // There are potentially many VPlex CGs that need to get created so instead
            // of creating a single workflow step to create all of them, we break it down
            // to individual create steps for finer granularity.
            
            // Create a step to create the CG.
            nextStep = workflow.createStep(CREATE_CG_STEP + createCgStepCounter,
                    String.format("VPLEX %s creating consistency group for %s", vplexURI, cg.getLabel()),
                    nextStep, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                    createCGMethod(vplexURI, cgURI, vplexVolumeURI), 
                            rollbackMethodNullMethod(), null);
            log.info("Created step for consistency group creation.");

            nextStep = workflow.createStep(ADD_VOLUMES_TO_CG_STEP + createCgStepCounter, String.format(
                    "VPLEX %s adding volume to consistency group %s", vplexURI, cg.getLabel()),
                    nextStep, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                    createAddVolumeToCGMethod(vplexURI, cgURI, vplexVolumeURI), 
                    createRemoveVolumeFromCGMethod(vplexURI, cgURI, vplexVolumeURI), null);
            log.info("Created step for add volumes to consistency group.");
        }
        
        return nextStep;
    }
    
    /**
     * A method the creates the method to create a new VPLEX consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group to be created.
     * @param vplexVolumeURI The URI of the VPLEX volume that will be used
     *        to determine if a consistency group will be created and where.
     * 
     * @return A reference to the consistency group creation workflow method.
     */
    private Workflow.Method createCGMethod(URI vplexURI, URI cgURI, URI vplexVolumeURI) {
        return new Workflow.Method(CREATE_CG_METHOD_NAME, vplexURI, cgURI,vplexVolumeURI);
    }
    
    /**
     * Called by the workflow to create a new VPLEX consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the Bourne consistency group
     * @param vplexVolumeURI The URI of the VPLEX used to determine the VPlex
     *                       cluster/distributed information.
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *         state.
     */
    public void createCG(URI vplexURI, URI cgURI, URI vplexVolumeURI, String stepId)
        throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated step state for consistency group creation to execute.");

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.debug("Got VPLEX API client.");

            // acquire a lock to serialize create cg requests to the VPLEX
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(cgURI, vplexURI));
            workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_VPLEX_CG));

            // Get the consistency group
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
            
        	//For the following cases we need special steps for the CG to choose the HA side/leg on the VPLEX to be the winner:
            //1. In an RP+VPLEX distributed setup, the user can choose to protect only the HA side.
            //2. In a MetroPoint setup, the user can choose the HA side as the Active side.

            if (vplexVolume.getPersonality().equals(PersonalityTypes.SOURCE.toString())) { 
            	VirtualPool vpool = getDataObject(VirtualPool.class, vplexVolume.getVirtualPool(), dbClient);
            	boolean haIsWinningCluster = VirtualPool.isRPVPlexProtectHASide(vpool) 
            			|| (vpool.getMetroPoint() && NullColumnValueGetter.isNotNullValue(vpool.getHaVarrayConnectedToRp()));

                if (haIsWinningCluster) {
                    log.info("Force HA side as winning cluster for VPLEX CG.");
                    // Temporarily change the varray to the HA varray. 
                    // NOTE: Do not persist!
                    vplexVolume.setLabel("DO NOT PERSIST THIS VOLUME");
                    vplexVolume.setVirtualArray(URI.create(vpool.getHaVarrayConnectedToRp()));
                }
            }
            
            // Lets determine the VPlex consistency group that need to be created for this volume.  
            ClusterConsistencyGroupWrapper clusterConsistencyGroup = 
                    getClusterConsistencyGroup(vplexVolume, cg.getLabel());
            
            String cgName = clusterConsistencyGroup.getCgName();
            String clusterName = clusterConsistencyGroup.getClusterName();
            boolean isDistributed = clusterConsistencyGroup.isDistributed();
            
            // Verify that the VPlex CG has been created for this VPlex system and cluster.
            if (!BlockConsistencyGroupUtils.isVplexCgCreated(cg, vplexURI.toString(), clusterName, cgName)) {
                log.info(String.format("Creating VPlex consistency group %s on cluster %s.", cgName, clusterName));
                // Create the VPlex consistency group
                client.createConsistencyGroup(cgName, clusterName, isDistributed);
                log.info(String.format("Created VPlex consistency group %s on cluster %s.", cgName, clusterName));
                
                // Enable the VPLEX CG with "recoverpoint-enabled" flag if the volumes are RP protected.
                log.info("VplexDeviceController : Update the CG with RP enabled flag");
                client.updateConsistencyRPEnabled(cgName, clusterName, true);
                log.info("VplexDeviceController : Done update of the CG with RP enabled flag");
                
                // Find the associated RP source volume so we can properly set the virtual array and
                // storage controller references.  We only want to set these based off the source volume.
                // NOTE: We will hit this code for both the source and target VPlex storage systems in the
                // case we have remote VPlex protection.  This will simply overwrite the existing
                // value for storageController which isn't an issue.
                Volume sourceVolume = RPHelper.getRPSourceVolume(dbClient, vplexVolume);
                
                if (sourceVolume != null) {
                    if (cg.getVirtualArray() == null) {
                        // Set the virtual array for the CG based off the source volume.
                        cg.setVirtualArray(sourceVolume.getVirtualArray());
                    }
                    
                    URI sourceStorageSystem = sourceVolume.getStorageController();
                    
                    if (sourceStorageSystem != null) {
                        // The source virtual array storage controller must be referenced by the 
                        // BlockConsistencyGroup or else a failure will occur because the source virtual
                        // array does not reference any ports of the target storage controller. This
                        // failure will occur in the BlockService where we validate the BlockConsistency
                        // group against the source virtual array.                       
                        cg.setStorageController(sourceStorageSystem);
                    }
                }                

                cg.addSystemConsistencyGroup(vplexSystem.getId().toString(), 
                        BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName));
                cg.addConsistencyGroupTypes(Types.VPLEX.name());
                cg.addConsistencyGroupTypes(Types.RP.name());
                dbClient.persistObject(cg);
                log.info("Updated consistency group in DB.");    
            } else {
                log.info(String.format("VPlex consistency group %s already exists on cluster %s.", cgName, clusterName));
                
                // See if the CG is created but contains no volumes. This CG may have existed 
                // previously for other volumes, but the volumes were deleted and removed from the CG. 
                // The visibility and cluster info would have been set for those volumes and may not be 
                // appropriate for these volumes.
                boolean cgContainsVolumes = false;
                
                // This is not ideal but we need to call the VPlex client to fetch all consistency
                // groups so we can find the one we are looking for.  We need to see if there are
                // any associated virtual volumes.  The BlockServiceApiImpl code associates the volumes
                // with the BlockConsistencyGroup even though these volumes are not yet part of
                // the VPlex consistency group.  Might be worth changing this logic.
                List<VPlexConsistencyGroupInfo> consistencyGroupsInfo = client.getConsistencyGroups();
                for (VPlexConsistencyGroupInfo consistencyGroupInfo : consistencyGroupsInfo) {
                    if (consistencyGroupInfo.getName().equals(cgName) 
                            && consistencyGroupInfo.getClusterName().equals(clusterName)) {
                        if (consistencyGroupInfo.getVirtualVolumes() != null
                                && !consistencyGroupInfo.getVirtualVolumes().isEmpty()) {
                            cgContainsVolumes = true;
                        }
                        break;
                    }
                }
                
                if (!cgContainsVolumes) {
                    log.info("Updating VPLEX consistency group properties.");
                    // Now we can update the consistency group properties.
                    client.updateConsistencyGroupProperties(cgName, clusterName, isDistributed);
                    log.info("Updated VPLEX consistency group properties.");
                }
            }
            
            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step for consistency group creation to success.");
        } catch (Exception ex) {
            log.error("Exception creating consistency group: " + ex.getMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }
    
    /**
     * A method that creates the workflow method for adding a VPLEX volume to a
     * consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group.
     * @param vplexVolumeURI The URI of the volume to be added to the
     *        consistency group.
     * 
     * @return A reference to the workflow method to add VPLEX volumes to a
     *         consistency group.
     */
    private Workflow.Method createAddVolumeToCGMethod(URI vplexURI, URI cgURI, URI vplexVolumeURI) {
        return new Workflow.Method(ADD_VOLUME_TO_CG_METHOD_NAME, vplexURI, cgURI,
                vplexVolumeURI);
    }
    
    /**
     * The method called by the workflow to add a VPLEX volume to a VPLEX
     * consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group.
     * @param vplexVolumeURI The URI of the volume to be added to the
     *        consistency group.
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *         state.
     */
    public void addVolumeToCG(URI vplexURI, URI cgURI, URI vplexVolumeURI,
        String stepId) throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated workflow step state to execute for add volume to consistency group.");

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.debug("Got VPLEX API client.");

            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);
            
            ClusterConsistencyGroupWrapper clusterConsistencyGroup = 
                    getClusterConsistencyGroup(vplexVolume, cg.getLabel());
            
            String cgName = clusterConsistencyGroup.getCgName();
            
            // acquire a lock to serialize create cg requests to the VPLEX
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(cgURI, vplexURI));
            workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_CG));

            log.info("Adding VPLEX volume: " + vplexVolume.getNativeId() + " to CG " + cgName + " on cluster " + clusterConsistencyGroup.getClusterName());

            // Get the names of the volumes to be added.
            List<String> vplexVolumeNames = new ArrayList<String>();
            vplexVolumeNames.add(vplexVolume.getDeviceLabel());
            log.info("VPLEX volume:" + vplexVolume.getDeviceLabel());

            // Add the volumes to the CG.
            client.addVolumesToConsistencyGroup(cgName, vplexVolumeNames);
            log.info("Added volumes to consistency group.");

            // Set the BlockConsistencyGroup reference.
            vplexVolume.setConsistencyGroup(cgURI);
            dbClient.updateAndReindexObject(vplexVolume);
            
            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step state to success for add volumes to consistency group.");
        } catch (Exception ex) {
            log.error(
                "Exception adding volumes to consistency group: " + ex.getMessage(), ex);
            ServiceError svcError = VPlexApiException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, svcError);
        }
    }
    
    /**
     * A method that creates the workflow method for removing VPLEX volumes from a
     * consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgName The name of the VPlex consistency group.
     * @param vplexVolumeURIs The URIs of the volumes to be removed from the
     *        consistency group.
     * 
     * @return A reference to the workflow method to remove VPLEX volumes from a
     *         consistency group.
     */
    private Workflow.Method createRemoveVolumeFromCGMethod(URI vplexURI, URI cgURI,
        URI vplexVolumeURI) {
        return new Workflow.Method(REMOVE_VOLUME_FROM_CG_METHOD_NAME, vplexURI, cgURI,
                vplexVolumeURI);
    }
    
    /**
     * The method called by the workflow to remove VPLEX volumes from a VPLEX
     * consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgName The name of the VPlex consistency group.
     * @param vplexVolumeURIs The URIs of the volumes to be removed from the
     *        consistency group.
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *         state.
     */
    public void removeVolumeFromCG(URI vplexURI, URI cgURI, URI vplexVolumeURI,
        String stepId) throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated workflow step state to execute for remove volumes from consistency group.");

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.info("Got VPLEX API client.");
            
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);
            
            ClusterConsistencyGroupWrapper clusterConsistencyGroup = 
                    getClusterConsistencyGroup(vplexVolume, cg.getLabel());
            
            String cgName = clusterConsistencyGroup.getCgName();
            
            // Get the names of the volumes to be removed.
            List<Volume> vplexVolumes = new ArrayList<Volume>();
            List<String> vplexVolumeNames = new ArrayList<String>();

            vplexVolumes.add(vplexVolume);
            vplexVolumeNames.add(vplexVolume.getDeviceLabel());
            log.info("Got VPLEX volume names.");

            // Remove the volumes from the CG.
            client.removeVolumesFromConsistencyGroup(vplexVolumeNames, cgName, false);
            log.info("Removed volumes from consistency group.");
            
            // Make sure the volume is updated.
            vplexVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
            dbClient.updateAndReindexObject(vplexVolume);

            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step state to success for remove volumes from consistency group.");
        } catch (Exception ex) {
            log.error(
                "Exception removing volumes from consistency group: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_CG_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.removeVolumesFromCGFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }
    
    /**
     * Maps a VPlex cluster/consistency group to its volumes.
     * 
     * @param vplexVolume The virtual volume from which to obtain the VPlex cluster.
     * @param clusterConsistencyGroupVolumes The map to store cluster/cg/volume relationships.
     * @param cgName The VPlex consistency group name.
     */
    @Override
    public ClusterConsistencyGroupWrapper getClusterConsistencyGroup(Volume vplexVolume, String cgName) {
        String clusterName = VPlexControllerUtils.getVPlexClusterName(dbClient, vplexVolume);
        StringSet assocVolumes = vplexVolume.getAssociatedVolumes();
        boolean distributed = false;

        String vplexCgName = null;

        // For RP+VPlex, a single BlockConsistencyGroup can map to local cluster and distributed cluster
        // VPlex CGs.  So, for uniqueness, we must append '-dist' for distributed CGs and '-clusterName' 
        // to non-distributed consistency groups.
        if (assocVolumes.size() > 1) {
            // Add '-dist' to the end of the CG name for distributed consistency groups. 
            vplexCgName = cgName + "-dist"; 
            distributed = true;
        } else {
            vplexCgName = cgName + "-" + clusterName;
        }   
        
        ClusterConsistencyGroupWrapper clusterConsistencyGroup = new ClusterConsistencyGroupWrapper();
        clusterConsistencyGroup.setClusterName(clusterName);
        clusterConsistencyGroup.setCgName(vplexCgName);
        clusterConsistencyGroup.setDistributed(distributed);

        return clusterConsistencyGroup;
    }
}
