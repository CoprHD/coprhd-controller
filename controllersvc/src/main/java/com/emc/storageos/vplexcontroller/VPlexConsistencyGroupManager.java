/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getDataObject;
import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getVPlexAPIClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.utils.ClusterConsistencyGroupWrapper;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplexcontroller.VPlexDeviceController.VPlexTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class VPlexConsistencyGroupManager extends AbstractConsistencyGroupManager {

    private static final String ADD_VOLUMES_TO_CG_METHOD_NAME = "addVolumesToCG";
    private static final String REMOVE_VOLUMES_FROM_CG_METHOD_NAME = "removeVolumesFromCG";
    private static final String SET_CG_PROPERTIES_METHOD_NAME = "setCGProperties";

    private static final String SET_CG_PROPERTIES_STEP = "setCGProperties";
    private static final String UPDATE_LOCAL_CG_STEP = "updateLocalCG";

    // logger reference.
    private static final Logger log = LoggerFactory
            .getLogger(VPlexConsistencyGroupManager.class);

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

        if (cgURI == null) {
            log.info("No consistency group for volume creation.");
            return waitFor;
        }
        return addStepsForCreateConsistencyGroup(workflow, waitFor, vplexSystem, vplexVolumeURIs,
                willBeRemovedByEarlierStep, cgURI);

    }

    /**
     * Create consistency group and add volumes to it
     * 
     * @param workflow The workflow
     * @param waitFor The previous step that it needs to wait for
     * @param vplexSystem The vplex system
     * @param vplexVolumeURIs The vplex volumes to be added to the consistency group
     * @param willBeRemovedByEarlierStep if the consistency group could be removed by previous step
     * @param cgURI The consistency group URI
     * @return
     * @throws ControllerException
     */
    private String addStepsForCreateConsistencyGroup(Workflow workflow, String waitFor,
            StorageSystem vplexSystem, List<URI> vplexVolumeURIs,
            boolean willBeRemovedByEarlierStep, URI cgURI) throws ControllerException {

        // No volumes, all done.
        if (vplexVolumeURIs.isEmpty()) {
            log.info(String.format("No volumes specified to add to the consistency group %s", cgURI.toString()));
            return waitFor;
        }

        URI vplexURI = vplexSystem.getId();
        String nextStep = waitFor;
        BlockConsistencyGroup cg = null;

        // Load the CG.
        cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);

        // Get a list of the active VPLEX volumes associated to this CG.
        List<Volume> cgVPLEXVolumes = getActiveVPLEXVolumesForCG(cgURI);

        // Determine the list of volumes to be added to the CG. For straight VPLEX,
        // this is just the passed VPLEX volumes, for RP+VPLEX this will just be the
        // one volume that is currently associated to the CG.
        List<URI> volumeList = new ArrayList<URI>();
        volumeList.addAll(vplexVolumeURIs);

        // Check to see if the CG has been created on the VPlex already
        // or if the CG will be removed by an earlier step such that
        // when the workflow executes, the CG will no longer be on the
        // array.
        if ((!cg.created(vplexURI)) || (willBeRemovedByEarlierStep)) {
            // If the CG doesn't exist at all.
            log.info("Consistency group not created.");
            // Create a step to create the CG.
            nextStep = workflow.createStep(CREATE_CG_STEP,
                    String.format("VPLEX %s creating consistency group %s", vplexURI, cgURI),
                    nextStep, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                    createCGMethod(vplexURI, cgURI, volumeList), rollbackMethodNullMethod(), null);
            log.info("Created step for consistency group creation.");
        } else {
            // See if the CG is created but contains no volumes.
            // That is there should be no volumes other than these
            // volumes we are trying to create and add to the CG.
            // If so, we need to make sure the visibility and storage
            // cluster info for the VPLEX CG is correct for these
            // volumes we are adding. It is the case this CG existed
            // previously for other volumes, but the volumes were
            // deleted and removed from the CG. The visibility and
            // cluster info would have been set for those volumes
            // and may not be appropriate for these volumes.
            if (cgVPLEXVolumes.size() == vplexVolumeURIs.size()) {
                // There are no volumes for the CG, other than these
                // we are adding, so we need to add a step to ensure
                // the visibility and cluster info for the CG is
                // correct.
                nextStep = workflow.createStep(SET_CG_PROPERTIES_STEP, String.format(
                        "Setting consistency group %s properties", cgURI), nextStep,
                        vplexURI, vplexSystem.getSystemType(), this.getClass(),
                        createSetCGPropertiesMethod(vplexURI, cgURI, volumeList),
                        rollbackMethodNullMethod(), null);
                log.info("Created step for setting consistency group properties.");
            }
        }
        // Create a step to add the volumes to the CG.
        nextStep = workflow.createStep(ADD_VOLUMES_TO_CG_STEP, String.format(
                "VPLEX %s adding volumes to consistency group %s", vplexURI, cgURI),
                nextStep, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                createAddVolumesToCGMethod(vplexURI, cgURI, volumeList),
                createRemoveVolumesFromCGMethod(vplexURI, cgURI, volumeList), null);
        log.info(String.format("Created step for adding volumes to the consistency group %s", cgURI.toString()));

        return nextStep;
    }

    /**
     * A method the creates the method to create a new VPLEX consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group to be created.
     * @param vplexVolumeURIs The URIs of the VPLEX volumes that will be used
     *            to create a VPlex consistency group.
     * 
     * @return A reference to the consistency group creation workflow method.
     */
    protected Workflow.Method createCGMethod(URI vplexURI, URI cgURI, List<URI> vplexVolumeURIs) {
        return new Workflow.Method(CREATE_CG_METHOD_NAME, vplexURI, cgURI, vplexVolumeURIs);
    }

    /**
     * Called by the workflow to create a new VPLEX consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the Bourne consistency group
     * @param vplexVolumeURIs The URI of the VPLEX used to determine the VPlex
     *            cluster/distributed information.
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *             state.
     */
    public void createCG(URI vplexURI, URI cgURI, List<URI> vplexVolumeURIs, String stepId)
            throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated step state for consistency group creation to execute.");

            // Lock the CG for the step duration.
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(dbClient, cgURI, vplexURI));
            workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_VPLEX_CG));

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.info("Got VPLEX API client.");

            // Get the consistency group
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);

            // Check to see if it was created since we defined the workflow.
            if (cg.created(vplexURI)) {
                StringSet cgNames = cg.getSystemConsistencyGroups().get(vplexURI.toString());
                log.info("Consistency group(s) already created: " + cgNames.toString());
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            // We need to know on what cluster to create the consistency group.
            // The cluster would be determined by the virtual array specified in
            // a volume creation request, which is the virtual array of the
            // passed virtual volumes. Get the virtual array for one of the
            // vplex volumes.
            Volume firstVPlexVolume = getDataObject(Volume.class, vplexVolumeURIs.get(0), dbClient);

            // Lets determine the VPlex consistency group that need to be created for this volume.
            ClusterConsistencyGroupWrapper clusterConsistencyGroup =
                    getClusterConsistencyGroup(firstVPlexVolume, cg.getLabel());

            String cgName = clusterConsistencyGroup.getCgName();
            String clusterName = clusterConsistencyGroup.getClusterName();
            boolean isDistributed = clusterConsistencyGroup.isDistributed();

            URI vaURI = firstVPlexVolume.getVirtualArray();
            log.info("Got virtual array for VPLEX volume.");

            // Now we can create the consistency group.
            client.createConsistencyGroup(cgName, clusterName, isDistributed);
            log.info("Created VPLEX consistency group.");

            // Now update the CG in the DB.
            cg.setVirtualArray(vaURI);
            cg.setStorageController(vplexURI);
            cg.addSystemConsistencyGroup(vplexSystem.getId().toString(),
                    BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName));
            cg.addConsistencyGroupTypes(Types.VPLEX.name());
            dbClient.persistObject(cg);
            log.info("Updated consistency group in DB.");

            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step for consistency group creation to success.");
        } catch (VPlexApiException vex) {
            log.error("Exception creating consistency group: " + vex.getMessage(), vex);
            WorkflowStepCompleter.stepFailed(stepId, vex);
        } catch (Exception ex) {
            log.error("Exception creating consistency group: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.createConsistencyGroupFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * A method that creates the workflow method for adding VPLEX volumes to a
     * consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group.
     * @param vplexVolumeURIs The URIs of the volumes to be added to the
     *            consistency group.
     * 
     * @return A reference to the workflow method to add VPLEX volumes to a
     *         consistency group.
     */
    protected Workflow.Method createAddVolumesToCGMethod(URI vplexURI, URI cgURI,
            List<URI> vplexVolumeURIs) {
        return new Workflow.Method(ADD_VOLUMES_TO_CG_METHOD_NAME, vplexURI, cgURI,
                vplexVolumeURIs);
    }

    /**
     * The method called by the workflow to add VPLEX volumes to a VPLEX
     * consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group.
     * @param vplexVolumeURIs The URIs of the volumes to be added to the
     *            consistency group.
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *             state.
     */
    public void addVolumesToCG(URI vplexURI, URI cgURI, List<URI> vplexVolumeURIs,
            String stepId) throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated workflow step state to execute for add volumes to consistency group.");

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.info("Got VPLEX API client.");

            Volume firstVPlexVolume = getDataObject(Volume.class, vplexVolumeURIs.get(0), dbClient);
            String cgName = getVplexCgName(firstVPlexVolume, cgURI);

            // Get the names of the volumes to be added.
            List<Volume> vplexVolumes = new ArrayList<Volume>();
            List<String> vplexVolumeNames = new ArrayList<String>();
            for (URI vplexVolumeURI : vplexVolumeURIs) {
                Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
                vplexVolumes.add(vplexVolume);
                vplexVolumeNames.add(vplexVolume.getDeviceLabel());
                log.info("VPLEX volume:" + vplexVolume.getDeviceLabel());
            }
            log.info("Got VPLEX volume names.");

            long startTime = System.currentTimeMillis();
            // Add the volumes to the CG.
            client.addVolumesToConsistencyGroup(cgName, vplexVolumeNames);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info(String.format("TIMER: Adding %s virtual volume(s) %s to the consistency group %s took %f seconds",
                    vplexVolumeNames.size(), vplexVolumeNames, cgName, (double) elapsed / (double) 1000));

            // Make sure the volumes are updated. Necessary when
            // adding volumes to a CG after volume creation.
            for (Volume vplexVolume : vplexVolumes) {
                vplexVolume.setConsistencyGroup(cgURI);
                dbClient.updateAndReindexObject(vplexVolume);
            }

            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step state to success for add volumes to consistency group.");
        } catch (VPlexApiException vae) {
            log.error("Exception adding volumes to consistency group: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            log.error(
                    "Exception adding volumes to consistency group: " + ex.getMessage(), ex);
            ServiceError svcError = VPlexApiException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, svcError);
        }
    }

    /**
     * A method the creates the method to set the properties for an existing
     * VPLEX consistency group with no volumes.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group to be created.
     * @param vplexVolumeURIs The URIs of the VPLEX volumes that will be added
     *            to the consistency group.
     * 
     * @return A reference to the consistency group creation workflow method.
     */
    private Workflow.Method createSetCGPropertiesMethod(URI vplexURI, URI cgURI,
            List<URI> vplexVolumeURIs) {
        return new Workflow.Method(SET_CG_PROPERTIES_METHOD_NAME, vplexURI, cgURI,
                vplexVolumeURIs);
    }

    /**
     * Called by the workflow to set the properties for an existing VPLEX
     * consistency group with no volumes.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the Bourne consistency group.
     * @param vplexVolumeURIs The URIs of the VPLEX volumes to be added to the
     *            consistency group.
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *             state.
     */
    public void setCGProperties(URI vplexURI, URI cgURI, List<URI> vplexVolumeURIs,
            String stepId) throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated step state for consistency group properties to execute.");

            // Lock the CG for the step duration.
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(dbClient, cgURI, vplexURI));
            workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_VPLEX_CG));

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.info("Got VPLEX API client.");

            // Get the consistency group
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);

            // We need to know on what cluster to find the consistency group.
            // The cluster would be determined by the virtual array specified in
            // a volume creation request, which is the virtual array of the
            // passed virtual volumes. Get the virtual array for one of the
            // vplex volumes.
            Volume firstVPlexVolume = getDataObject(Volume.class, vplexVolumeURIs.get(0), dbClient);

            ClusterConsistencyGroupWrapper clusterConsistencyGroup =
                    getClusterConsistencyGroup(firstVPlexVolume, cg.getLabel());

            String cgName = clusterConsistencyGroup.getCgName();
            String clusterName = clusterConsistencyGroup.getClusterName();
            boolean isDistributed = clusterConsistencyGroup.isDistributed();

            // Now we can update the consistency group properties.
            client.updateConsistencyGroupProperties(cgName, clusterName, isDistributed);
            log.info("Updated VPLEX consistency group properties.");

            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step for consistency group properties to success.");
        } catch (VPlexApiException vae) {
            log.error("Exception updating consistency group properties: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            log.error("Exception updating consistency group properties: " + ex.getMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateConsistencyGroup(Workflow workflow, URI vplexURI, URI cgURI,
            List<URI> addVolumesList, List<URI> removeVolumesList, String opId)
            throws InternalException {

        try {
            String waitFor = null;
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);

            // Lock the CG for the duration of update CG workflow.
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(dbClient, cgURI, vplexURI));
            boolean acquiredLocks = workflowService.acquireWorkflowLocks(workflow, lockKeys, LockTimeoutValue.get(LockType.RP_VPLEX_CG));
            if (!acquiredLocks) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        "UpdateConsistencyGroup: " + cg.getLabel());
            }

            // The addVolumesList could be full copies or volumes.
            boolean isFullCopy = false;
            if (addVolumesList != null && !addVolumesList.isEmpty()) {
                URI volURI = addVolumesList.get(0);
                Volume vol = getDataObject(Volume.class, volURI, dbClient);
                isFullCopy = ControllerUtils.isVolumeFullCopy(vol, dbClient);
            }
            // Users could use updateConsistencyGroup operation to add backend CGs for ingested CGs.
            // if that's the case, we will only add the backend CGs, but not add those virtual volumes to
            // the VPlex CG.
            boolean isIngestedCG = isAddingBackendCGForIngestedCG(cg, addVolumesList);

            // Check if the CG has been created in VPlex yet
            boolean isNewCg = !cg.created();
            // If necessary, create a step to update the local CGs.
            if (cg.getTypes().contains(Types.LOCAL.toString()) || isIngestedCG || isNewCg) {
                // We need to determine the backend systems that own the local CGs and the
                // volumes to be added/removed from each. There should really only be either
                // one of two backend systems depending upon whether or not the volumes are
                // local or distributed. In addition, when volumes are being both added and
                // removed, the maps should contains the same key set so it doesn't matter
                // which is used.
                Map<URI, List<URI>> localAddVolumesMap = getLocalVolumesForUpdate(addVolumesList);
                Map<URI, List<URI>> localRemoveVolumesMap = getLocalVolumesForRemove(removeVolumesList);
                Set<URI> localSystems = localAddVolumesMap.keySet();
                if (localSystems.isEmpty()) {
                    localSystems = localRemoveVolumesMap.keySet();
                }

                // Now we need to iterate over the backend systems and create a step to
                // update the corresponding consistency groups on the backend arrays.
                Iterator<URI> localSystemIter = localSystems.iterator();
                while (localSystemIter.hasNext()) {
                    URI localSystemURI = localSystemIter.next();
                    StorageSystem localSystem = getDataObject(StorageSystem.class, localSystemURI, dbClient);
                    List<URI> localAddVolumesList = localAddVolumesMap.get(localSystemURI);
                    List<URI> localRemoveVolumesList = localRemoveVolumesMap.get(localSystemURI);
                    Workflow.Method updateLocalMethod = new Workflow.Method(
                            UPDATE_CONSISTENCY_GROUP_METHOD_NAME, localSystemURI, cgURI,
                            localAddVolumesList, localRemoveVolumesList);
                    Workflow.Method rollbackLocalMethod = new Workflow.Method(
                            UPDATE_CONSISTENCY_GROUP_METHOD_NAME, localSystemURI, cgURI,
                            localRemoveVolumesList, localAddVolumesList);
                    workflow.createStep(UPDATE_LOCAL_CG_STEP, String.format(
                            "Updating consistency group %s on system %s",
                            cgURI, localSystemURI), null,
                            localSystemURI, localSystem.getSystemType(),
                            BlockDeviceController.class, updateLocalMethod,
                            rollbackLocalMethod, null);
                }
                waitFor = UPDATE_LOCAL_CG_STEP;
                log.info("Created steps to remove volumes from native consistency groups.");
            }

            // First remove any volumes to be removed.
            int removeVolumeCount = 0;
            if ((removeVolumesList != null) && !removeVolumesList.isEmpty()) {
                removeVolumeCount = removeVolumesList.size();
                addStepForRemoveVolumesFromCG(workflow, waitFor, vplexSystem,
                        removeVolumesList, cgURI);
            }

            // Now create a step to add volumes to the CG.
            if ((addVolumesList != null) && !addVolumesList.isEmpty() && !isIngestedCG && !isNewCg && !isFullCopy) {
                // See if the CG contains no volumes. If so, we need to
                // make sure the visibility and storage cluster info for
                // the VPLEX CG is correct for these volumes we are adding.
                // It is the case this CG existed previously for other
                // volumes, but the volumes were deleted and removed from
                // the CG. The visibility and cluster info would have been
                // set for those volumes and may not be appropriate for these
                // volumes. It could also be that this request removes all
                // the existing volumes and the volumes being added have
                // different property requirements.
                List<Volume> cgVPLEXVolumes = getActiveVPLEXVolumesForCG(cgURI);
                if ((cgVPLEXVolumes.isEmpty()) || (cgVPLEXVolumes.size() == removeVolumeCount)) {
                    Workflow.Method setPropsMethod = createSetCGPropertiesMethod(vplexURI,
                            cgURI, addVolumesList);
                    // We only need to reset the properties if it's empty because
                    // we just removed all the volumes. The properties are reset
                    // back to those appropriate for the removed volumes before
                    // they get added back in.
                    Workflow.Method rollbackSetPropsMethod =
                            (removeVolumesList != null && !removeVolumesList.isEmpty()) ?
                                    createSetCGPropertiesMethod(vplexURI, cgURI, removeVolumesList) :
                                    rollbackMethodNullMethod();
                    waitFor = workflow.createStep(SET_CG_PROPERTIES_STEP, String.format(
                            "Setting consistency group %s properties", cgURI), waitFor,
                            vplexURI, vplexSystem.getSystemType(), this.getClass(),
                            setPropsMethod, rollbackSetPropsMethod, null);
                    log.info("Created step for setting consistency group properties.");
                }

                // Now create a step to add the volumes.
                Workflow.Method addMethod = createAddVolumesToCGMethod(vplexURI, cgURI,
                        addVolumesList);
                workflow.createStep(ADD_VOLUMES_TO_CG_STEP, String.format(
                        "VPLEX %s adding volumes to consistency group %s", vplexURI, cgURI),
                        waitFor, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                        addMethod, rollbackMethodNullMethod(), null);
                log.info("Created step for add volumes to consistency group.");
            } else if (isNewCg && addVolumesList != null && !addVolumesList.isEmpty() && !isFullCopy) {
                addStepsForCreateConsistencyGroup(workflow, waitFor, vplexSystem, addVolumesList, false, cgURI);

            }

            TaskCompleter completer = new VPlexTaskCompleter(BlockConsistencyGroup.class,
                    Arrays.asList(cgURI), opId, null);
            log.info("Executing workflow plan");
            workflow.executePlan(completer, String.format(
                    "Update of consistency group %s completed successfully", cgURI));
            log.info("Workflow plan executed");
        } catch (Exception e) {
            String failMsg = String.format("Update of consistency group %s failed",
                    cgURI);
            log.error(failMsg, e);
            TaskCompleter completer = new VPlexTaskCompleter(BlockConsistencyGroup.class,
                    Arrays.asList(cgURI), opId, null);
            String opName = ResourceOperationTypeEnum.UPDATE_CONSISTENCY_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.updateConsistencyGroupFailed(
                    cgURI.toString(), opName, e);
            completer.error(dbClient, serviceError);
        }
    }

    /**
     * Maps a VPlex cluster/consistency group to its volumes.
     * 
     * @param vplexVolume The virtual volume from which to obtain the VPlex cluster.
     * @param clusterConsistencyGroupVolumes The map to store cluster/cg/volume relationships.
     * @param cgName The VPlex consistency group name.
     * @throws Exception
     */
    @Override
    public ClusterConsistencyGroupWrapper getClusterConsistencyGroup(Volume vplexVolume, String cgName) throws Exception {
        ClusterConsistencyGroupWrapper clusterConsistencyGroup = new ClusterConsistencyGroupWrapper();

        // If there are no associated volumes, we cannot determine the cluster name and if the
        // volume is distributed or not. This is typical in VPlex ingested volume cases. So for
        // these cases, we just set the cgName value only.
        if (vplexVolume.getAssociatedVolumes() != null && !vplexVolume.getAssociatedVolumes().isEmpty()) {
            String clusterName = VPlexControllerUtils.getVPlexClusterName(dbClient, vplexVolume);
            StringSet assocVolumes = vplexVolume.getAssociatedVolumes();
            boolean distributed = false;

            if (assocVolumes.size() > 1) {
                distributed = true;
            }

            clusterConsistencyGroup.setClusterName(clusterName);
            clusterConsistencyGroup.setDistributed(distributed);
        }

        clusterConsistencyGroup.setCgName(cgName);

        return clusterConsistencyGroup;
    }

    /**
     * Gets the active VPLEX volumes in the CG.
     * 
     * @param cgURI The consistency group URI
     * 
     * @return A list of the active VPLEX volumes in the CG.
     */
    private List<Volume> getActiveVPLEXVolumesForCG(URI cgURI) {
        List<Volume> cgVPLEXVolumes = new ArrayList<Volume>();
        List<Volume> cgVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(
                dbClient, Volume.class, ContainmentConstraint.Factory.
                        getVolumesByConsistencyGroup(cgURI));
        for (Volume cgVolume : cgVolumes) {
            if (!Volume.checkForVplexBackEndVolume(dbClient, cgVolume)) {
                cgVPLEXVolumes.add(cgVolume);
            }
        }
        return cgVPLEXVolumes;
    }

    /**
     * Create a map of the backend volumes for the passed VPLEX volumes key'd by
     * the backend systems. Called during a consistency group update so that
     * the corresponding backend consistency groups can be updated.
     * 
     * @param vplexVolumes A list of VPLEX volumes.
     * 
     * @return A map of the backend volumes for the passed VPLEX volumes key'd
     *         by the backend systems.
     */
    private Map<URI, List<URI>> getLocalVolumesForUpdate(List<URI> vplexVolumes) {
        Map<URI, List<URI>> localVolumesMap = new HashMap<URI, List<URI>>();
        if ((vplexVolumes != null) && (!vplexVolumes.isEmpty())) {
            for (URI vplexVolumeURI : vplexVolumes) {
                Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
                StringSet associatedVolumes = vplexVolume.getAssociatedVolumes();
                for (String assocVolumeId : associatedVolumes) {
                    URI assocVolumeURI = URI.create(assocVolumeId);
                    Volume assocVolume = getDataObject(Volume.class, assocVolumeURI, dbClient);
                    URI assocSystemURI = assocVolume.getStorageController();
                    if (!localVolumesMap.containsKey(assocSystemURI)) {
                        List<URI> systemVolumes = new ArrayList<URI>();
                        localVolumesMap.put(assocSystemURI, systemVolumes);
                    }
                    localVolumesMap.get(assocSystemURI).add(assocVolumeURI);
                }
            }
        }
        return localVolumesMap;
    }

    /**
     * Adds a step to the passed workflow to remove the passed volumes from the
     * consistency group with the passed URI.
     * 
     * @param workflow The workflow to which the step is added
     * @param waitFor The step for which this step should wait.
     * @param vplexSystem The VPLEX system
     * @param volumes The volumes to be removed
     * @param cgURI The URI of the consistency group
     * 
     * @return The step id of the added step.
     */
    public String addStepForRemoveVolumesFromCG(Workflow workflow, String waitFor,
            StorageSystem vplexSystem, List<URI> volumes, URI cgURI) {
        URI vplexURI = vplexSystem.getId();
        Workflow.Method removeMethod = createRemoveVolumesFromCGMethod(vplexURI, cgURI, volumes);
        Workflow.Method removeRollbackMethod = createAddVolumesToCGMethod(vplexURI, cgURI, volumes);
        waitFor = workflow.createStep(REMOVE_VOLUMES_FROM_CG_STEP, String.format(
                "Removing volumes %s from consistency group %s on VPLEX %s", volumes, cgURI,
                vplexURI), waitFor, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                removeMethod, removeRollbackMethod, null);
        log.info("Created step for remove volumes from consistency group.");
        return waitFor;
    }

    /**
     * Check if update consistency group operation is for adding back end consistency groups for ingested CG.
     * 
     * @param cg
     * @param addVolumesList
     * @return true or false
     */
    private boolean isAddingBackendCGForIngestedCG(BlockConsistencyGroup cg, List<URI> addVolumesList) {
        boolean result = false;
        if (cg.getTypes().contains(Types.LOCAL.toString())) {
            // Not ingested CG
            return result;
        }
        List<Volume> cgVolumes = BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(cg, dbClient, null);
        Set<String> cgVolumeURIs = new HashSet<String>();
        for (Volume cgVolume : cgVolumes) {
            cgVolumeURIs.add(cgVolume.getId().toString());
        }
        if (!addVolumesList.isEmpty() && cgVolumeURIs.contains(addVolumesList.get(0).toString())) {
            result = true;
        }
        return result;
    }
    
    
    /**
     * Create a map of the backend volumes that need to remove from backend CG.
     * Called during a consistency group update so that the corresponding backend 
     * consistency groups can be updated.
     * 
     * @param vplexVolumes A list of VPLEX volumes.
     * 
     * @return A map of the backend volumes for the passed VPLEX volumes key'd
     *         by the backend systems.
     */
    private Map<URI, List<URI>> getLocalVolumesForRemove(List<URI> vplexVolumes) {
        Map<URI, List<URI>> localVolumesMap = new HashMap<URI, List<URI>>();
        if ((vplexVolumes != null) && (!vplexVolumes.isEmpty())) {
            for (URI vplexVolumeURI : vplexVolumes) {
                Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
                StringSet associatedVolumes = vplexVolume.getAssociatedVolumes();
                for (String assocVolumeId : associatedVolumes) {
                    URI assocVolumeURI = URI.create(assocVolumeId);
                    Volume assocVolume = getDataObject(Volume.class, assocVolumeURI, dbClient);
                    if (NullColumnValueGetter.isNotNullValue(assocVolume.getReplicationGroupInstance())) { 
                        // The backend volume is in a backend CG
                        URI assocSystemURI = assocVolume.getStorageController();
                        if (!localVolumesMap.containsKey(assocSystemURI)) {
                            List<URI> systemVolumes = new ArrayList<URI>();
                            localVolumesMap.put(assocSystemURI, systemVolumes);
                        }
                        localVolumesMap.get(assocSystemURI).add(assocVolumeURI);
                    }
                }
            }
        }
        return localVolumesMap;
    }
}
