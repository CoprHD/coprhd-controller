/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.srdfcontroller;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.model.Volume.PersonalityTypes.TARGET;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockRetryException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.RemoteMirroring;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFAddPairToGroupCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFChangeCopyModeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFExpandCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkFailOverCancelCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkFailOverCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkPauseCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkResumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkStartCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkStopCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkSuspendCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkSyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFMirrorRollbackCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFRemoveDeviceGroupsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFSwapCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations.Mode;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * SRDF-specific Controller implementation with support for block orchestration.
 */
public class SRDFDeviceController implements SRDFController, BlockOrchestrationInterface {
    private static final Logger log = LoggerFactory.getLogger(SRDFDeviceController.class);
    private static final String ADD_SYNC_VOLUME_PAIRS_METHOD = "addVolumePairsToCgMethodStep";
    private static final String ROLLBACK_ADD_SYNC_VOLUME_PAIR_METHOD = "rollbackAddSyncVolumePairStep";
    private static final String CREATE_SRDF_VOLUME_PAIR = "createSRDFVolumePairStep";
    private static final String CREATE_SRDF_ASYNC_MIRROR_METHOD = "createSrdfCgPairsStep";
    private static final String REFRESH_SRDF_TARGET_SYSTEM = "refreshStorageSystemStep";
    private static final String ROLLBACK_SRDF_LINKS_METHOD = "rollbackSRDFLinksStep";
    private static final String SUSPEND_SRDF_LINK_METHOD = "suspendSRDFLinkStep";
    private static final String SPLIT_SRDF_LINK_METHOD = "splitSRDFLinkStep";
    private static final String REMOVE_DEVICE_GROUPS_METHOD = "removeDeviceGroupsStep";
    private static final String CREATE_SRDF_RESYNC_PAIR_METHOD = "reSyncSRDFLinkStep";
    private static final String CREATE_SRDF_MIRRORS_STEP_GROUP = "CREATE_SRDF_MIRRORS_STEP_GROUP";
    private static final String CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_GROUP = "CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_GROUP";
    private static final String CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_DESC = "Synchronize source/target pairs";
    private static final String DELETE_SRDF_MIRRORS_STEP_GROUP = "DELETE_SRDF_MIRRORS_STEP_GROUP";
    private static final String CREATE_SRDF_MIRRORS_STEP_DESC = "Create SRDF Link";
    private static final String REFRESH_SYSTEM_STEP_DESC = "Refresh System";
    public static final String SUSPEND_SRDF_MIRRORS_STEP_GROUP = "SUSPEND_SRDF_MIRRORS_STEP_GROUP";
    public static final String SUSPEND_SRDF_MIRRORS_STEP_DESC = "Suspend SRDF Link";
    public static final String SPLIT_SRDF_MIRRORS_STEP_DESC = "Split SRDF Link ";
    private static final String DETACH_SRDF_MIRRORS_STEP_DESC = "Detach SRDF Link";
    public static final String RESUME_SRDF_MIRRORS_STEP_GROUP = "RESUME_SRDF_MIRRORS_STEP_GROUP";
    public static final String RESUME_SRDF_MIRRORS_STEP_DESC = "Resume SRDF Link";
    public static final String RESTORE_SRDF_MIRRORS_STEP_GROUP = "RESTORE_SRDF_MIRRORS_STEP_GROUP";
    public static final String RESTORE_SRDF_MIRRORS_STEP_DESC = "Restore SRDF Link";
    private static final String UPDATE_SRDF_PAIRING_STEP_GROUP = "UPDATE_SRDF_PAIRING_STEP_GROUP";
    private static final String UPDATE_SRDF_PAIRING = "updateSRDFPairingStep";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";
    private static final String CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP = "CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP";
    private static final String CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_DESC = "Active source/target pairs";
    private static final String REFRESH_VOLUME_PROPERTIES_STEP = "REFRESH_VOLUME_PROPERTIES_STEP";
    private static final String REFRESH_VOLUME_PROPERTIES_STEP_DESC = "Refresh volume properties";

    private static final String REMOVE_ASYNC_PAIR_METHOD = "removePairFromGroup";
    private static final String DETACH_SRDF_PAIR_METHOD = "detachVolumePairStep";
    private static final String REMOVE_SRDF_PAIR_STEP_DESC = "Remove %1$s pair from %1$s cg";
    private static final String SUSPEND_SRDF_PAIR_STEP_DESC = "Suspend %1$s pair removed from %1$s cg";
    private static final String DETACH_SRDF_PAIR_STEP_DESC = "Detach %1$s pair removed from %1$s cg";
    private static final String REMOVE_DEVICE_GROUPS_STEP_DESC = "Removing volume from replication group";
    private static final String DETACH_SRDF_MIRRORS_STEP_GROUP = "DETACH_SRDF_MIRRORS_STEP_GROUP";
    public static final String SPLIT_SRDF_MIRRORS_STEP_GROUP = "SPLIT_SRDF_MIRRORS_STEP_GROUP";
    private static final String RESYNC_SRDF_MIRRORS_STEP_GROUP = "RESYNC_SRDF_MIRRORS_STEP_GROUP";
    private static final String RESYNC_SRDF_MIRRORS_STEP_DESC = "Reestablishing SRDF Relationship again";
    private static final String STEP_VOLUME_EXPAND = "EXPAND_VOLUME";
    private static final String CREATE_SRDF_RESUME_PAIR_METHOD = "resumeSyncPairStep";
    private static final String RESUME_SRDF_GROUP_METHOD = "resumeSrdfGroupStep";
    private static final String RESTORE_METHOD = "restoreStep";
    private static final String SUSPEND_SRDF_GROUP_METHOD = "suspendSrdfGroupStep";
    private static final String CHANGE_SRDF_TO_NONSRDF_STEP_DESC = "Converting SRDF Devices to Non Srdf devices";
    private static final String CONVERT_TO_NONSRDF_DEVICES_METHOD = "convertToNonSrdfDevicesMethodStep";
    private static final String CREATE_LIST_REPLICAS_METHOD = "createListReplicas";
    private static final String UPDATE_VOLUME_PROEPERTIES_METHOD = "updateVolumeProperties";

    private WorkflowService workflowService;
    private DbClient dbClient;
    private Map<String, BlockStorageDevice> devices;
    private SRDFUtils utils;

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public void setWorkflowService(final WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(final DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public Map<String, BlockStorageDevice> getDevices() {
        return devices;
    }

    public void setDevices(final Map<String, BlockStorageDevice> devices) {
        this.devices = devices;
    }

    @Override
    public String addStepsForCreateVolumes(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> volumeDescriptors, final String taskId)
            throws InternalException {
        List<VolumeDescriptor> srdfDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.SRDF_SOURCE,
                        VolumeDescriptor.Type.SRDF_EXISTING_SOURCE,
                        VolumeDescriptor.Type.SRDF_TARGET }, new VolumeDescriptor.Type[] {});
        if (srdfDescriptors.isEmpty()) {
            log.info("No SRDF Steps required");
            return waitFor;
        }
        log.info("Adding SRDF steps for create volumes");
        // Create SRDF relationships
        waitFor = createElementReplicaSteps(workflow, waitFor, srdfDescriptors);
        return waitFor;
    }

    @Override
    public String addStepsForDeleteVolumes(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> volumeDescriptors, final String taskId)
            throws InternalException {
        List<VolumeDescriptor> sourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                VolumeDescriptor.Type.SRDF_SOURCE);
        if (sourceDescriptors.isEmpty()) {
            return waitFor;
        }

        Map<URI, Volume> volumeMap = queryVolumes(sourceDescriptors);
        // a rare roll back scenario, where target volume deletion failed due to Sym lock
        // as of multiple targets not supported
        // TODO make this roll back work for multiple targets
        for (Volume source : volumeMap.values()) {
            StringSet targets = source.getSrdfTargets();

            if (targets == null) {
                return waitFor;
            }

            for (String target : targets) {
                Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));

                if (null == targetVolume) {
                    return waitFor;
                }

                if (Mode.ASYNCHRONOUS.toString().equalsIgnoreCase(targetVolume.getSrdfCopyMode()) &&
                        targetVolume.hasConsistencyGroup()) {
                    // if replication Group Name is not set, we end up in delete errors, preventing.
                    RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                            targetVolume.getSrdfGroup());
                    if (!NullColumnValueGetter.isNotNullValue(group.getSourceReplicationGroupName()) ||
                            !NullColumnValueGetter.isNotNullValue(group.getTargetReplicationGroupName())) {
                        log.warn(
                                "Consistency Groups of RDF {} still not updated in ViPR DB. If async pair is created minutes back and tried delete immediately,please wait and try again",
                                group.getNativeGuid());
                        throw DeviceControllerException.exceptions.srdfAsyncStepDeletionfailed(group.getNativeGuid());
                    }
                }

            }
        }

        waitFor = deleteSRDFMirrorSteps(workflow, waitFor, sourceDescriptors);

        return waitFor;
    }

    private String createElementReplicaSteps(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> volumeDescriptors) {
        log.info("START create element replica steps");
        List<VolumeDescriptor> sourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                VolumeDescriptor.Type.SRDF_SOURCE, VolumeDescriptor.Type.SRDF_EXISTING_SOURCE);
        List<VolumeDescriptor> targetDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                VolumeDescriptor.Type.SRDF_TARGET);
        Map<URI, Volume> uriVolumeMap = queryVolumes(volumeDescriptors);

        /**
         * Locks that must be acquired before continuing.
         */
        acquireWorkflowLockOrThrow(workflow, generateLocks(volumeDescriptors, uriVolumeMap));

        /**
         * If copy Mode synchronous, then always run createElementReplica, irrespective of
         * whether existing volumes are present in RA Group or not. Consistency parameter
         * doesn't have any effect , hence creating replication groups for each volume
         * doesn't make sense. Moreover, SMI-S has done rigorous testing of
         * Pause,resume,fail over,fail back on StorageSynchronized rather than on Groups.
         */
        boolean volumePartOfCG = isVolumePartOfCG(sourceDescriptors, uriVolumeMap);

        if (!volumePartOfCG) {
            Mode SRDFMode = getSRDFMode(sourceDescriptors, uriVolumeMap);
            if (Mode.ACTIVE.equals(SRDFMode)) {
                createNonCGSRDFActiveModeVolumes(workflow, waitFor, sourceDescriptors, targetDescriptors, uriVolumeMap);
            } else {
                createNonCGSRDFVolumes(workflow, waitFor, sourceDescriptors, uriVolumeMap);
            }
        } else {
            createCGSRDFVolumes(workflow, waitFor, sourceDescriptors, targetDescriptors, uriVolumeMap);
        }
        waitFor = CREATE_SRDF_MIRRORS_STEP_GROUP;

        if (volumePartOfCG && sourceDescriptors.size() > 1) {
            waitFor = updateSourceAndTargetPairings(workflow, waitFor,
                    sourceDescriptors, targetDescriptors, uriVolumeMap);
        }

        return waitFor;
    }

    private String updateSourceAndTargetPairings(Workflow workflow, String waitFor,
            List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors,
            Map<URI, Volume> uriVolumeMap) {

        log.info("Creating step to update source and target pairings");
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);

        Volume firstSource = dbClient.queryObject(Volume.class, sourceURIs.get(0));
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, firstSource.getStorageController());

        Method method = updateSourceAndTargetPairingsMethod(sourceURIs, targetURIs);
        workflow.createStep(UPDATE_SRDF_PAIRING_STEP_GROUP, UPDATE_SRDF_PAIRING_STEP_GROUP, waitFor,
                sourceSystem.getId(), sourceSystem.getSystemType(), getClass(), method, null, null);

        return UPDATE_SRDF_PAIRING_STEP_GROUP;
    }

    private Method updateSourceAndTargetPairingsMethod(List<URI> sourceURIs, List<URI> targetURIs) {
        return new Workflow.Method(UPDATE_SRDF_PAIRING, sourceURIs, targetURIs);
    }

    public boolean updateSRDFPairingStep(List<URI> sourceURIs, List<URI> targetURIs, String opId) {
        log.info("Updating SRDF pairings");

        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            completer = new SRDFTaskCompleter(sourceURIs, opId);
            getRemoteMirrorDevice().doUpdateSourceAndTargetPairings(sourceURIs, targetURIs);
            completer.ready(dbClient);
        } catch (Exception e) {
            log.error("Failed to update SRDF pairings", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    protected void createNonCGSRDFVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> sourceDescriptors,
            Map<URI, Volume> uriVolumeMap) {
        for (VolumeDescriptor sourceDescriptor : sourceDescriptors) {
            Volume source = uriVolumeMap.get(sourceDescriptor.getVolumeURI());
            // this will be null for normal use cases except vpool change
            URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
            log.info("VPoolChange URI {}", vpoolChangeUri);
            StringSet srdfTargets = source.getSrdfTargets();
            for (String targetStr : srdfTargets) {
                URI targetURI = URI.create(targetStr);
                Volume target = uriVolumeMap.get(targetURI);
                RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                        target.getSrdfGroup());
                StorageSystem system = dbClient.queryObject(StorageSystem.class,
                        group.getSourceStorageSystemUri());

                Workflow.Method createMethod = createSRDFVolumePairMethod(system.getId(),
                        source.getId(), targetURI, vpoolChangeUri);
                Workflow.Method rollbackMethod = rollbackSRDFLinkMethod(system.getId(),
                        source.getId(), targetURI, false);
                // Ensure CreateElementReplica steps are executed sequentially (CQ613404)
                waitFor = workflow.createStep(CREATE_SRDF_MIRRORS_STEP_GROUP,
                        CREATE_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                        system.getSystemType(), getClass(), createMethod, rollbackMethod, null);
            }
        }
    }

    /**
     * This method creates steps to add non CG SRDF Active mode volumes in the RDF group.
     * 
     * @param workflow Reference to Workflow
     * @param waitFor String waitFor of previous step, we wait on this to complete
     * @param sourceDescriptors list of source volume descriptors
     * @param targetDescriptors list of target volume descriptors
     * @param uriVolumeMap map of volume URI to volume object
     */
    protected void createNonCGSRDFActiveModeVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors, Map<URI, Volume> uriVolumeMap) {
        RemoteDirectorGroup group = getRAGroup(targetDescriptors, uriVolumeMap);
        StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, group.getRemoteStorageSystemUri());
        // finding actual volumes from Provider
        Set<String> volumesInRDFGroupsOnProvider = findVolumesPartOfRDFGroups(system, group);

        if (group.getVolumes() == null) {
            group.setVolumes(new StringSet());
        }

        if ((group.getVolumes().isEmpty() && !volumesInRDFGroupsOnProvider.isEmpty())
                || (!group.getVolumes().isEmpty() && volumesInRDFGroupsOnProvider.isEmpty())) {
            log.info("RDF Group {} in ViPR DB is not sync with the one on the provider. ", group.getNativeGuid());
            clearSourceAndTargetVolumes(sourceDescriptors, targetDescriptors);
            throw DeviceControllerException.exceptions.rdfGroupInViprDBNotInSyncWithArray(group
                    .getNativeGuid());
        }

        if (volumesInRDFGroupsOnProvider.isEmpty() && !SupportedCopyModes.ALL.toString().equalsIgnoreCase(group.getSupportedCopyMode())) {
            log.info("RDF Group {} is empty and supported copy mode is {} ", group.getNativeGuid(), group.getSupportedCopyMode());
            clearSourceAndTargetVolumes(sourceDescriptors, targetDescriptors);
            throw DeviceControllerException.exceptions.rdfGroupInViprDBNotInSyncWithArray(group
                    .getNativeGuid());
        }

        if (!group.getVolumes().isEmpty()) {
            // Make sure that the Active volumes in this group are not created outside the ViPR Controller
            // ViPR Controller should not attempt to suspend them
            try {
                // The below call will return an error if there is a single volume in the group that does not have an associated Volume URI
                List<Volume> volumes = utils.getAssociatedVolumesForSRDFGroup(system, group);
            } catch (Exception e) {
                log.info("RDF Group {} has devices created outside ViPRController", group.getNativeGuid());
                clearSourceAndTargetVolumes(sourceDescriptors, targetDescriptors);
                throw DeviceControllerException.exceptions.rdfGroupHasPairsCreatedOutsideViPR(group
                        .getNativeGuid());
            }
        }

        String createSrdfPairStep = null;
        if (volumesInRDFGroupsOnProvider.isEmpty() && SupportedCopyModes.ALL.toString().equalsIgnoreCase(group.getSupportedCopyMode())) {
            log.info("RA Group {} was empty", group.getId());
            createSrdfPairStep = createNonCGSrdfPairStepsOnEmptyGroup(sourceDescriptors, targetDescriptors, group, uriVolumeMap, waitFor,
                    workflow);
        } else {
            log.info("RA Group {} not empty", group.getId());
            createSrdfPairStep = createNonCGSrdfPairStepsOnPopulatedGroup(sourceDescriptors, targetDescriptors, group, uriVolumeMap,
                    waitFor,
                    workflow);
        }
        // Generate workflow step to refresh source and target system .
        String refreshSourceSystemStep = null;
        if (null != system) {
            refreshSourceSystemStep = addStepToRefreshSystem(CREATE_SRDF_MIRRORS_STEP_GROUP, system, null, createSrdfPairStep, workflow);
        }
        String refreshTargetSystemStep = null;
        if (null != targetSystem) {
            refreshTargetSystemStep = addStepToRefreshSystem(CREATE_SRDF_MIRRORS_STEP_GROUP, targetSystem, null, refreshSourceSystemStep,
                    workflow);
        }

        // Refresh target volume properties
        refreshVolumeProperties(targetDescriptors, targetSystem, refreshTargetSystemStep, workflow);
    }

    /**
     * This method is used to clean resources in case of failures.
     * 
     * @param sourceDescriptors list of source volume descriptors
     * @param targetDescriptors list of target volume descriptors
     */
    private void clearSourceAndTargetVolumes(List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors) {
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);
        URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
        // Clear source and target

        for (URI sourceUri : sourceURIs) {
            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceUri);
            if (null != sourceVolume) {
                log.info("Clearing source volume {}-->{}", sourceVolume.getNativeGuid(),
                        sourceVolume.getId());
                if (null == vpoolChangeUri) {
                    // clear everything if not vpool change
                    sourceVolume.setPersonality(NullColumnValueGetter.getNullStr());
                    sourceVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());

                    sourceVolume.setInactive(true);
                    sourceVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
                if (null != sourceVolume.getSrdfTargets()) {
                    sourceVolume.getSrdfTargets().clear();
                }
                dbClient.updateAndReindexObject(sourceVolume);
            }

        }

        for (URI targetUri : targetURIs) {
            Volume targetVolume = dbClient.queryObject(Volume.class, targetUri);
            if (null != targetVolume) {
                log.info("Clearing target volume {}-->{}", targetVolume.getNativeGuid(),
                        targetVolume.getId());
                targetVolume.setPersonality(NullColumnValueGetter.getNullStr());
                targetVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                targetVolume.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(),
                        NullColumnValueGetter.getNullStr()));
                targetVolume.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                targetVolume.setSrdfGroup(NullColumnValueGetter.getNullURI());
                targetVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                targetVolume.setInactive(true);
                dbClient.updateAndReindexObject(targetVolume);
            }

        }
    }

    protected void createSyncSteps(Workflow workflow, String waitFor, Volume source, StorageSystem system) {
        StringSet srdfTargets = source.getSrdfTargets();
        for (String targetStr : srdfTargets) {
            URI targetURI = URI.create(targetStr);

            Workflow.Method createMethod = createSRDFVolumePairMethod(system.getId(),
                    source.getId(), targetURI, null);
            Workflow.Method rollbackMethod = rollbackSRDFLinkMethod(system.getId(),
                    source.getId(), targetURI, false);
            // Ensure CreateElementReplica steps are executed sequentially (CQ613404)
            waitFor = workflow.createStep(CREATE_SRDF_MIRRORS_STEP_GROUP,
                    CREATE_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                    system.getSystemType(), getClass(), createMethod, rollbackMethod, null);
        }
    }

    @SuppressWarnings("unchecked")
    protected void createCGSRDFVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors, Map<URI, Volume> uriVolumeMap) {
        RemoteDirectorGroup group = getRAGroup(targetDescriptors, uriVolumeMap);
        StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, group.getRemoteStorageSystemUri());
        // finding actual volumes from Provider
        Set<String> volumes = findVolumesPartOfRDFGroups(system, group);

        if (group.getVolumes() == null) {
            group.setVolumes(new StringSet());
        }
        // RDF Groups must be in sync with Array, to be able to make the right
        // decision for Async Groups.
        /*
         * Check the following 2 conditions.
         * 1. If there are no volumes in RDFGroup on Array & volumes in RDFGroup in ViPR DB.
         * 2. If there are volumes in RDFGroup on Array & no volumes in RDFGroup in ViPR DB.
         */
        if ((group.getVolumes().isEmpty() && !volumes.isEmpty())
                || (!group.getVolumes().isEmpty() && volumes.isEmpty())) {
            // throw Exception rediscover source and target arrays.
            log.warn("RDF Group {} out of sync with Array", group.getNativeGuid());
            List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
            List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);
            URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
            // Clear source and target

            for (URI sourceUri : sourceURIs) {
                Volume sourceVolume = dbClient.queryObject(Volume.class, sourceUri);
                if (null != sourceVolume) {
                    log.info("Clearing source volume {}-->{}", sourceVolume.getNativeGuid(),
                            sourceVolume.getId());
                    if (null == vpoolChangeUri) {
                        // clear everything if not vpool change
                        sourceVolume.setPersonality(NullColumnValueGetter.getNullStr());
                        sourceVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());

                        sourceVolume.setInactive(true);
                        sourceVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    }
                    if (null != sourceVolume.getSrdfTargets()) {
                        sourceVolume.getSrdfTargets().clear();
                    }
                    dbClient.updateAndReindexObject(sourceVolume);
                }

            }

            for (URI targetUri : targetURIs) {
                Volume targetVolume = dbClient.queryObject(Volume.class, targetUri);
                if (null != targetVolume) {
                    log.info("Clearing target volume {}-->{}", targetVolume.getNativeGuid(),
                            targetVolume.getId());
                    targetVolume.setPersonality(NullColumnValueGetter.getNullStr());
                    targetVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                    targetVolume.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(),
                            NullColumnValueGetter.getNullStr()));
                    targetVolume.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                    targetVolume.setSrdfGroup(NullColumnValueGetter.getNullURI());
                    targetVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    targetVolume.setInactive(true);
                    dbClient.updateAndReindexObject(targetVolume);
                }

            }
            throw DeviceControllerException.exceptions.srdfAsyncStepCreationfailed(group
                    .getNativeGuid());

        }
        group.getVolumes().replace(volumes);
        dbClient.persistObject(group);

        if (volumes.isEmpty() && SupportedCopyModes.ALL.toString().equalsIgnoreCase(group.getSupportedCopyMode())) {
            log.info("RA Group {} was empty", group.getId());
            waitFor = createSrdfCgPairStepsOnEmptyGroup(sourceDescriptors, targetDescriptors, group, waitFor, workflow);
        } else {
            log.info("RA Group {} not empty", group.getId());
            waitFor = createSrdfCGPairStepsOnPopulatedGroup(sourceDescriptors, group, uriVolumeMap, waitFor, workflow);
        }
        // Generate workflow step to refresh target system after CG creation.
        if (null != system) {
            waitFor = addStepToRefreshSystem(CREATE_SRDF_MIRRORS_STEP_GROUP, system, null, waitFor, workflow);
        }
        if (null != targetSystem) {
            waitFor = addStepToRefreshSystem(CREATE_SRDF_MIRRORS_STEP_GROUP, targetSystem, null, waitFor, workflow);
        }

        // Refresh target volume properties
        Mode SRDFMode = getSRDFMode(sourceDescriptors, uriVolumeMap);
        if (Mode.ACTIVE.equals(SRDFMode)) {
            refreshVolumeProperties(targetDescriptors, targetSystem, waitFor, workflow);
        }
    }

    private String addStepToRefreshSystem(String stepGroup, StorageSystem system, List<URI> volumeIds, String waitFor, Workflow workflow) {
        Workflow.Method refreshTargetSystemsMethod = new Method(REFRESH_SRDF_TARGET_SYSTEM, system, volumeIds);
        return workflow.createStep(stepGroup, REFRESH_SYSTEM_STEP_DESC, waitFor, system.getId(),
                system.getSystemType(), getClass(), refreshTargetSystemsMethod, rollbackMethodNullMethod(), null);
    }

    public void refreshStorageSystemStep(StorageSystem sourceSystem, List<URI> volumeIds, String opId) {
        log.info("START refreshing system {} {}", sourceSystem.getLabel(), sourceSystem.getId());
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            getRemoteMirrorDevice().refreshStorageSystem(sourceSystem.getId(), volumeIds);
        } catch (Exception e) {
            log.warn("Refreshing system step failed", e);
        } finally {
            WorkflowStepCompleter.stepSucceded(opId);
        }
        log.info("END refreshing system {} {}", sourceSystem.getLabel(), sourceSystem.getId());
    }

    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    private String createSrdfCgPairStepsOnEmptyGroup(List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors, RemoteDirectorGroup group,
            String waitFor, Workflow workflow) {

        StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
        log.info("VPoolChange URI {}", vpoolChangeUri);
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);

        Workflow.Method createGroupsMethod = createSrdfCgPairsMethod(system.getId(), sourceURIs, targetURIs, vpoolChangeUri);
        Workflow.Method rollbackGroupsMethod = rollbackSRDFLinksMethod(system.getId(), sourceURIs, targetURIs, true);
        return workflow.createStep(CREATE_SRDF_MIRRORS_STEP_GROUP, CREATE_SRDF_MIRRORS_STEP_DESC, waitFor,
                system.getId(), system.getSystemType(), getClass(), createGroupsMethod, rollbackGroupsMethod, null);
    }

    private String createSrdfCGPairStepsOnPopulatedGroup(List<VolumeDescriptor> sourceDescriptors,
            RemoteDirectorGroup group, Map<URI, Volume> uriVolumeMap,
            String waitFor, Workflow workflow) {

        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
        log.info("VPoolChange URI {}", vpoolChangeUri);
        String stepId = waitFor;
        List<URI> targetURIs = new ArrayList<>();

        for (URI sourceURI : sourceURIs) {
            Volume source = uriVolumeMap.get(sourceURI);
            StringSet srdfTargets = source.getSrdfTargets();
            for (String targetStr : srdfTargets) {
                URI targetURI = URI.create(targetStr);
                targetURIs.add(targetURI);
            }
        }

        Mode SRDFMode = getSRDFMode(sourceDescriptors, uriVolumeMap);
        if (Mode.ACTIVE.equals(SRDFMode)) {
            /*
             * Invoke Suspend on the SRDF group as more ACTIVE pairs cannot be added until all other
             * existing pairs are in NOT-READY state
             */
            Method suspendGroupMethod = suspendSRDFGroupMethod(system.getId(), group, sourceURIs, targetURIs);
            Method resumeRollbackMethod = resumeSRDFGroupMethod(system.getId(), group, sourceURIs, targetURIs);

            stepId = workflow.createStep(CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP,
                    SUSPEND_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                    system.getSystemType(), getClass(), suspendGroupMethod, resumeRollbackMethod, null);
        }

        /*
         * 1. Invoke CreateListReplica with all source/target pairings.
         */
        Method createListMethod = createListReplicasMethod(system.getId(), sourceURIs, targetURIs, false);
        // false here because we want to rollback individual links not the entire (pre-existing) group.
        Method rollbackMethod = rollbackSRDFLinksMethod(system.getId(), sourceURIs, targetURIs, false);

        workflow.createStep(CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_GROUP,
                CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_DESC, stepId, system.getId(),
                system.getSystemType(), getClass(), createListMethod, rollbackMethod, null);

        /**
         * If R1/R2 has group snap/clone/mirror, add pair to group is not supported unless we provide force flag.
         * Force flag is implemented by default
         * Create new snap/clone/mirror for new R1/R2 volumes,
         * add them to DeviceMaskingGroup (DMG) which is equivalent to its ReplicationGroup (RG)
         * (adding new devices to existing RG is not supported. As a workaround, add them to DMG)
         * 
         * Note: This is supported from SMI-S 8.0.3.11 onwards.
         * It will be called from API to create replica objects for new volumes and add them to DMG.
         */

        /*
         * 2. Invoke AddSyncpair with the created StorageSynchronized from Step 1
         */

        Workflow.Method addMethod = addVolumePairsToCgMethod(system.getId(), sourceURIs, group.getId(), vpoolChangeUri);
        Workflow.Method rollbackAddMethod = rollbackAddSyncVolumePairMethod(system.getId(), sourceURIs, targetURIs, false);
        String addVolumestoCgStep = workflow.createStep(CREATE_SRDF_MIRRORS_STEP_GROUP,
                CREATE_SRDF_MIRRORS_STEP_DESC, CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_GROUP, system.getId(),
                system.getSystemType(), getClass(), addMethod, rollbackAddMethod,
                null);

        if (Mode.ACTIVE.equals(SRDFMode)) {
            /*
             * Invoke Resume on the SRDF group to get all pairs back in the READY state.
             */
            Method resumeGroupMethod = resumeSRDFGroupMethod(system.getId(), group, sourceURIs, targetURIs);
            return workflow.createStep(CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP,
                    RESUME_SRDF_MIRRORS_STEP_DESC, addVolumestoCgStep, system.getId(),
                    system.getSystemType(), getClass(), resumeGroupMethod, rollbackMethodNullMethod(), null);

        } else {
            return addVolumestoCgStep;
        }
    }

    private void createSrdfCGPairStepsOnPopulatedGroup(Volume source,
            String waitFor, Workflow workflow) {
        List<URI> sourceURIs = new ArrayList<URI>();
        sourceURIs.add(source.getId());
        StorageSystem system = null;
        String stepId = waitFor;
        RemoteDirectorGroup group = null;
        StringSet srdfTargets = source.getSrdfTargets();
        if (null == srdfTargets) {
            return;
        }
        List<URI> targetURIS = new ArrayList<URI>();
        for (String targetStr : srdfTargets) {
            /* 1. Create Element Replicas for each source/target pairing */
            URI targetURI = URI.create(targetStr);
            targetURIS.add(targetURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            group = dbClient.queryObject(RemoteDirectorGroup.class,
                    target.getSrdfGroup());
            system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
            Workflow.Method createMethod = createSRDFVolumePairMethod(
                    system.getId(), source.getId(), targetURI, null);
            Workflow.Method rollbackMethod = rollbackSRDFLinkMethod(system.getId(),
                    source.getId(), targetURI, false);
            stepId = workflow.createStep(CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_GROUP,
                    CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_DESC, stepId, system.getId(),
                    system.getSystemType(), getClass(), createMethod, rollbackMethod,
                    null);
        }
        /* 2. Invoke AddSyncpair with the created StorageSynchronized from Step 1 */
        Workflow.Method addMethod = addVolumePairsToCgMethod(system.getId(), sourceURIs, group.getId(), null);
        Workflow.Method rollbackAddMethod = rollbackAddSyncVolumePairMethod(system.getId(), sourceURIs, targetURIS, false);
        workflow.createStep(CREATE_SRDF_MIRRORS_STEP_GROUP,
                CREATE_SRDF_MIRRORS_STEP_DESC, CREATE_SRDF_SYNC_VOLUME_PAIR_STEP_GROUP, system.getId(),
                system.getSystemType(), getClass(), addMethod, rollbackAddMethod,
                null);
    }

    private RemoteDirectorGroup getRAGroup(List<VolumeDescriptor> descriptors, Map<URI, Volume> uriVolumeMap) {
        Volume firstTarget = getFirstTarget(descriptors, uriVolumeMap);
        return dbClient.queryObject(RemoteDirectorGroup.class, firstTarget.getSrdfGroup());
    }

    private Mode getSRDFMode(List<VolumeDescriptor> sourceDescriptors, Map<URI, Volume> uriVolumeMap) {
        Volume firstTarget = getFirstTarget(sourceDescriptors, uriVolumeMap);
        return Mode.valueOf(firstTarget.getSrdfCopyMode());
    }

    private boolean isVolumePartOfCG(List<VolumeDescriptor> sourceDescriptors, Map<URI, Volume> uriVolumeMap) {
        Volume source = uriVolumeMap.get(sourceDescriptors.get(0).getVolumeURI());
        return (source != null && source.getConsistencyGroup() != null);
    }

    private Volume getFirstTarget(List<VolumeDescriptor> descriptors, Map<URI, Volume> uriVolumeMap) {
        List<VolumeDescriptor> targetDescriptors = VolumeDescriptor.filterByType(descriptors, VolumeDescriptor.Type.SRDF_TARGET);

        if (targetDescriptors.isEmpty()) {
            for (VolumeDescriptor volumeDescriptor : descriptors) {
                if (VolumeDescriptor.Type.SRDF_SOURCE.equals(volumeDescriptor.getType())
                        || VolumeDescriptor.Type.SRDF_EXISTING_SOURCE.equals(volumeDescriptor.getType())) {
                    Volume source = uriVolumeMap.get(volumeDescriptor.getVolumeURI());
                    return getFirstTarget(source);
                }
            }
        } else {
            for (VolumeDescriptor volumeDescriptor : descriptors) {
                if (VolumeDescriptor.Type.SRDF_TARGET.equals(volumeDescriptor.getType())) {
                    return uriVolumeMap.get(volumeDescriptor.getVolumeURI());
                }
            }
        }

        throw new IllegalStateException("Expected a target volume to exist");
    }

    private Volume getFirstTarget(Volume sourceVolume) {
        StringSet targets = sourceVolume.getSrdfTargets();

        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("Source has no targets");
        }

        return dbClient.queryObject(Volume.class, URI.create(targets.iterator().next()));
    }

    private boolean canRemoveSrdfCg(Map<URI, Volume> volumeMap) {
        Volume targetVol = null;
        boolean volumePartOfCG = false;
        for (Volume source : volumeMap.values()) {
            volumePartOfCG = null != source.getConsistencyGroup();
            StringSet targets = source.getSrdfTargets();
            if (targets == null) {
                return false;
            }
            for (String target : targets) {
                targetVol = dbClient.queryObject(Volume.class, URI.create(target));
                break;
            }
            break;
        }
        RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                targetVol.getSrdfGroup());
        if (null == group) {
            return true;
        }
        StorageSystem system = getStorageSystem(group.getSourceStorageSystemUri());
        Set<String> volumes = findVolumesPartOfRDFGroups(system, group);
        if (group.getVolumes() == null) {
            group.setVolumes(new StringSet());
        }
        group.getVolumes().replace(volumes);
        log.info("# volumes : {}  in RDF Group {} after refresh", Joiner.on(",").join(group.getVolumes()), group.getNativeGuid());
        dbClient.persistObject(group);

        if (null != volumes && volumes.size() == volumeMap.size() && volumePartOfCG) {
            log.info("Deleting all the volumes {} in CG  in one attempt", Joiner.on(",").join(volumeMap.keySet()));
            return true;
        }
        return false;

    }

    /**
     * Delete All SRDF Volumes in CG in one attempt.
     * 
     * @param sourcesVolumeMap
     * @param workflow
     * @param waitFor
     * @return
     */
    private String deleteAllSrdfVolumesInCG(Map<URI, Volume> sourcesVolumeMap, final Workflow workflow,
            String waitFor, final List<VolumeDescriptor> sourceDescriptors) {

        // TODO Improve this logic
        Volume sourceVolume = sourcesVolumeMap.get(sourceDescriptors.get(0).getVolumeURI());
        Volume targetVolume = getFirstTarget(sourceVolume);
        if (targetVolume == null) {
            log.info("No target volume available for source {}", sourceVolume.getId());
            return waitFor;
        }
        RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, targetVolume.getSrdfGroup());
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, group.getRemoteStorageSystemUri());
        // Suspend all members in the group
        Method method = suspendSRDFLinkMethod(targetSystem.getId(), sourceVolume.getId(), targetVolume.getId(), false);
        String splitStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                SPLIT_SRDF_MIRRORS_STEP_DESC, waitFor, targetSystem.getId(), targetSystem.getSystemType(), getClass(),
                method, null, null);

        // Second we detach the group...
        Workflow.Method detachMethod = detachGroupPairsMethod(targetSystem.getId(), sourceVolume.getId(),
                targetVolume.getId());
        String detachMirrorStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                DETACH_SRDF_MIRRORS_STEP_DESC, splitStep, targetSystem.getId(), targetSystem.getSystemType(),
                getClass(), detachMethod, null, null);

        waitFor = detachMirrorStep;
        List<URI> targetVolumeIds = new ArrayList<URI>();

        for (Volume source : sourcesVolumeMap.values()) {
            StringSet srdfTargets = source.getSrdfTargets();
            for (String srdfTarget : srdfTargets) {
                log.info("suspend and detach: source:{}, target:{}", source.getId(), srdfTarget);
                URI targetURI = URI.create(srdfTarget);
                Volume target = dbClient.queryObject(Volume.class, targetURI);
                if (null == target) {
                    log.warn("Target volume {} not available for SRDF source volume {}", source.getId(), targetURI);
                    return DELETE_SRDF_MIRRORS_STEP_GROUP;
                }
                log.info("target Volume {} with srdf group {}", target.getNativeGuid(), target.getSrdfGroup());
                // Third we remove the device groups, a defensive step to remove
                // members from deviceGroups if it exists.
                Workflow.Method removeGroupsMethod = removeDeviceGroupsMethod(sourceSystem.getId(), source.getId(),
                        targetURI);
                waitFor = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP, REMOVE_DEVICE_GROUPS_STEP_DESC,
                        waitFor, sourceSystem.getId(), sourceSystem.getSystemType(), getClass(), removeGroupsMethod, null,
                        null);

            }
        }
        // refresh provider before invoking deleteVolume call
        if (null != targetSystem) {
            addStepToRefreshSystem(DELETE_SRDF_MIRRORS_STEP_GROUP, targetSystem, targetVolumeIds, waitFor, workflow);
        }
        return DELETE_SRDF_MIRRORS_STEP_GROUP;
    }

    /**
     * Deletion of SRDF Volumes with/without CGs.
     * 
     * @param workflow
     * @param waitFor
     * @param sourceDescriptors
     * @return
     */
    private String deleteSRDFMirrorSteps(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> sourceDescriptors) {
        log.info("START delete SRDF mirrors workflow");
        Map<URI, Volume> sourcesVolumeMap = queryVolumes(sourceDescriptors);
        StorageSystem system = null;
        StorageSystem targetSystem = null;
        List<URI> targetVolumeURIs = new ArrayList<URI>();

        /**
         * Locks that must be acquired before continuing.
         */
        acquireWorkflowLockOrThrow(workflow, generateLocks(sourceDescriptors, sourcesVolumeMap));

        if (canRemoveSrdfCg(sourcesVolumeMap)) {
            // invoke workflow to delete CG
            log.info("Invoking SRDF Consistency Group Deletion with all its volumes");
            return deleteAllSrdfVolumesInCG(sourcesVolumeMap, workflow, waitFor, sourceDescriptors);
        }

        Map<URI, RemoteDirectorGroup> srdfGroupMap = new HashMap<URI, RemoteDirectorGroup>();
        Map<URI, List<URI>> srdfGroupToSourceVolumeMap = new HashMap<URI, List<URI>>();
        Map<URI, List<URI>> srdfGroupToTargetVolumeMap = new HashMap<URI, List<URI>>();
        Map<URI, String> srdfGroupToTargetVolumeAccessState = new HashMap<URI, String>();
        Map<URI, String> srdfGroupToLastWaitFor = new HashMap<URI, String>();
        // invoke deletion of volume within CG
        for (Volume source : sourcesVolumeMap.values()) {
            StringSet srdfTargets = source.getSrdfTargets();
            for (String srdfTarget : srdfTargets) {
                log.info("suspend and detach: source:{}, target:{}", source.getId(), srdfTarget);
                URI targetURI = URI.create(srdfTarget);
                Volume target = dbClient.queryObject(Volume.class, targetURI);
                if (null == target) {
                    log.warn("Target volume {} not available for SRDF source vol {}", source.getId(), targetURI);
                    // We need to proceed with the operation, as it could be because of a left over from last operation.
                    return waitFor;
                }
                log.info("target Volume {} with srdf group {}", target.getNativeGuid(),
                        target.getSrdfGroup());
                RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
                system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
                targetSystem = dbClient.queryObject(StorageSystem.class, group.getRemoteStorageSystemUri());

                if (!source.hasConsistencyGroup()) {
                    // No CG, so suspend single link (cons_exempt used in case of Asynchronous)
                    boolean consExempt = true;
                    boolean activeMode = target.getSrdfCopyMode() != null && target.getSrdfCopyMode().equals(Mode.ACTIVE.toString());
                    if (activeMode) {
                        consExempt = false;
                    }
                    Workflow.Method suspendMethod = suspendSRDFLinkMethod(system.getId(),
                            source.getId(), targetURI, consExempt);
                    String suspendStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                            SUSPEND_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                            system.getSystemType(), getClass(), suspendMethod, null, null);
                    // Second we detach the mirrors...
                    Workflow.Method detachMethod = detachVolumePairMethod(system.getId(), source.getId(), targetURI);
                    String detachStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                            DETACH_SRDF_MIRRORS_STEP_DESC, suspendStep, system.getId(),
                            system.getSystemType(), getClass(), detachMethod, null, null);
                    waitFor = detachStep;
                    if (activeMode) {
                        // We need to fill up necessary maps to be able to call Resume once on the SRDF
                        // group when all the requested volumes are removed from the SRDF group.
                        URI groupId = group.getId();
                        srdfGroupMap.put(groupId, group);
                        if (srdfGroupToSourceVolumeMap.get(groupId) == null) {
                            srdfGroupToSourceVolumeMap.put(groupId, new ArrayList<URI>());
                        }
                        if (srdfGroupToTargetVolumeMap.get(groupId) == null) {
                            srdfGroupToTargetVolumeMap.put(groupId, new ArrayList<URI>());
                        }
                        srdfGroupToSourceVolumeMap.get(groupId).add(source.getId());
                        srdfGroupToTargetVolumeMap.get(groupId).add(targetURI);
                        srdfGroupToLastWaitFor.put(groupId, waitFor);
                        srdfGroupToTargetVolumeAccessState.put(groupId, target.getAccessState());
                    }

                } else {
                    // Defensive steps to prevent orphaned SRDF Volumes, which cannot be deleted.
                    // First we remove the sync pair from Async CG...
                    targetVolumeURIs.add(targetURI);
                    Workflow.Method removePairFromGroupMethod = removePairFromGroup(system.getId(),
                            source.getId(), targetURI, true);
                    String removePairFromGroupWorkflowDesc = String.format(REMOVE_SRDF_PAIR_STEP_DESC, target.getSrdfCopyMode());
                    String detachVolumePairWorkflowDesc = String.format(DETACH_SRDF_PAIR_STEP_DESC, target.getSrdfCopyMode());

                    String removePairFromGroupStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                            removePairFromGroupWorkflowDesc, waitFor, system.getId(),
                            system.getSystemType(), getClass(), removePairFromGroupMethod, null, null);
                    // suspend the removed async pair
                    Workflow.Method suspendPairMethod = suspendSRDFLinkMethod(system.getId(),
                            source.getId(), targetURI, true);
                    String suspendPairStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                            SUSPEND_SRDF_MIRRORS_STEP_DESC, removePairFromGroupStep, system.getId(),
                            system.getSystemType(), getClass(), suspendPairMethod, null, null);
                    // Finally we detach the removed async pair...
                    // don't proceed if detach fails, earlier we were allowing the delete operation
                    // to proceed even if there is a failure on detach.
                    Workflow.Method detachPairMethod = detachVolumePairMethod(system.getId(),
                            source.getId(), targetURI);
                    waitFor = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                            detachVolumePairWorkflowDesc, suspendPairStep, system.getId(),
                            system.getSystemType(), getClass(), detachPairMethod, null, null);
                }
            }
        }

        String lastDeleteSRDFMirrorStep = null;
        if (!srdfGroupMap.isEmpty()) {
            // Add step to resume each Active SRDF group
            for (URI srdfGroupURI : srdfGroupMap.keySet()) {
                RemoteDirectorGroup group = srdfGroupMap.get(srdfGroupURI);
                if(srdfGroupToTargetVolumeAccessState.get(srdfGroupURI).equals(Volume.VolumeAccessState.NOT_READY.name())){
                    log.info("Srdf group {} {} was already in a suspended state hence skipping resume on this group.", srdfGroupURI, group.getNativeGuid());
                    continue;
                }
                List<URI> sourceVolumes = srdfGroupToSourceVolumeMap.get(srdfGroupURI);
                List<URI> targetVolumes = srdfGroupToTargetVolumeMap.get(srdfGroupURI);
                String lastWaitFor = srdfGroupToLastWaitFor.get(srdfGroupURI);
                system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
                Workflow.Method resumeSRDFGroupMethod = resumeSRDFGroupMethod(system.getId(), group, sourceVolumes, targetVolumes);
                lastDeleteSRDFMirrorStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                        RESUME_SRDF_MIRRORS_STEP_DESC, lastWaitFor, system.getId(),
                        system.getSystemType(), getClass(), resumeSRDFGroupMethod, null, null);
            }
        }

        // refresh provider before invoking deleteVolume call
        if (null != targetSystem) {
            addStepToRefreshSystem(DELETE_SRDF_MIRRORS_STEP_GROUP, targetSystem, targetVolumeURIs, lastDeleteSRDFMirrorStep, workflow);
        }
        return DELETE_SRDF_MIRRORS_STEP_GROUP;
    }

    private Workflow.Method convertToNonSrdfDevicesMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI, final boolean rollback) {
        return new Workflow.Method(CONVERT_TO_NONSRDF_DEVICES_METHOD, systemURI, sourceURI, targetURI,
                rollback);
    }

    public boolean convertToNonSrdfDevicesMethodStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final boolean rollback, final String opId) {
        log.info("START conversion of srdf to non srdf devices");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            Volume source = dbClient.queryObject(Volume.class, sourceURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            // Change source and target RDF devices to non-srdf devices in DB
            source.setPersonality(NullColumnValueGetter.getNullStr());
            source.setAccessState(Volume.VolumeAccessState.READWRITE.name());
            source.getSrdfTargets().clear();
            target.setPersonality(NullColumnValueGetter.getNullStr());
            target.setAccessState(Volume.VolumeAccessState.READWRITE.name());
            target.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
            target.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
            target.setSrdfGroup(NullColumnValueGetter.getNullURI());
            dbClient.persistObject(source);
            dbClient.persistObject(target);
            log.info("SRDF Devices source {} and target {} converted to non srdf devices", source.getId(), target.getId());
            completer = new SRDFTaskCompleter(sourceURI, targetURI, opId);
            completer.ready(dbClient);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    private String reSyncSRDFMirrorSteps(final Workflow workflow, final String waitFor,
            final Volume source) {
        log.info("START resync SRDF mirrors workflow");
        StorageSystem system = getStorageSystem(source.getStorageController());
        StringSet srdfTargets = source.getSrdfTargets();
        for (String srdfTarget : srdfTargets) {
            URI targetURI = URI.create(srdfTarget);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            if (null == target) {
                return waitFor;
            }
            log.info("target Volume {} with srdf group {}", target.getNativeGuid(),
                    target.getSrdfGroup());
            Workflow.Method reSyncMethod = reSyncSRDFLinkMethod(system.getId(),
                    source.getId(), targetURI);
            String reSyncStep = workflow.createStep(RESYNC_SRDF_MIRRORS_STEP_GROUP,
                    RESYNC_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                    system.getSystemType(), getClass(), reSyncMethod, null, null);
        }
        return RESYNC_SRDF_MIRRORS_STEP_GROUP;
    }

    private Method detachVolumePairMethod(URI systemURI, URI sourceURI, URI targetURI) {
        return new Workflow.Method(DETACH_SRDF_PAIR_METHOD, systemURI, sourceURI, targetURI, false);
    }

    private Method detachGroupPairsMethod(URI systemURI, URI sourceURI, URI targetURI) {
        return new Workflow.Method(DETACH_SRDF_PAIR_METHOD, systemURI, sourceURI, targetURI, true);
    }

    public boolean detachVolumePairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final boolean onGroup, final String opId) {
        log.info("START Detach Pair onGroup={}", onGroup);
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new SRDFTaskCompleter(sourceURI, targetURI, opId);
            getRemoteMirrorDevice().doDetachLink(system, sourceURI, targetURI, onGroup, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    private Method removePairFromGroup(URI systemURI, URI sourceURI, URI targetURI, final boolean rollback) {
        return new Workflow.Method(REMOVE_ASYNC_PAIR_METHOD, systemURI, sourceURI, targetURI, rollback);
    }

    public boolean removePairFromGroup(final URI systemURI, final URI sourceURI,
            final URI targetURI, final boolean rollback, final String opId) {
        log.info("START Remove Pair from Group");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            dbClient.queryObject(Volume.class, targetURI);
            completer = new SRDFTaskCompleter(sourceURI, targetURI, opId);
            getRemoteMirrorDevice().doRemoveVolumePair(system, sourceURI, targetURI, rollback,
                    completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    /**
     * Returns a Workflow.Method for resuming SRDF group
     * 
     * @param systemURI Reference to storage system URI
     * @param group Reference to RemoteDirectorGroup which represents SRDF group.
     * @param sourceVolumes List of source volumes URI
     * @param targetVolumes List of target volumes URI
     * @return workflow Method
     */
    public Method resumeSRDFGroupMethod(final URI systemURI, final RemoteDirectorGroup group, final List<URI> sourceVolumes,
            final List<URI> targetVolumes) {
        return new Workflow.Method(RESUME_SRDF_GROUP_METHOD, systemURI, group, sourceVolumes, targetVolumes);
    }

    /**
     * Method to resume SRDF group called a workflow step.
     * 
     * @param systemURI Reference to storage system URI
     * @param group Reference to RemoteDirectorGroup which represents SRDF group.
     * @param sourceVolumes List of source volumes URI
     * @param targetVolumes List of target volumes URI
     * @param opId The stepId used for completion
     * @return true if resume is successful else false
     */
    public boolean resumeSrdfGroupStep(final URI systemURI, final RemoteDirectorGroup group, final List<URI> sourceVolumes,
            final List<URI> targetVolumes, String opId) {
        log.info("START Resume SRDF group {} for {}", group.getLabel(), systemURI);
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            List<Volume> volumes = utils.getAssociatedVolumesForSRDFGroup(system, group);
            Collection<Volume> tgtVolumes = newArrayList(filter(volumes, utils.volumePersonalityPredicate(TARGET)));

            if (!tgtVolumes.isEmpty() && tgtVolumes.iterator().hasNext()) {
                List<URI> combinedVolumeList = new ArrayList<URI>();
                combinedVolumeList.addAll(sourceVolumes);
                combinedVolumeList.addAll(targetVolumes);
                completer = new SRDFTaskCompleter(combinedVolumeList, opId);
                getRemoteMirrorDevice().doResumeLink(system, tgtVolumes.iterator().next(), false, completer);
            } else {
                log.info("There are no more volumes in the SRDF group {} {}, so no need to call resume.", group.getLabel(), group.getId());
                WorkflowStepCompleter.stepSucceded(opId);
            }

        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    /**
     * Returns a Workflow.Method for suspending SRDF group
     * 
     * @param systemURI Reference to storage system URI
     * @param group Reference to RemoteDirectorGroup which represents SRDF group.
     * @param sourceVolumes List of source volumes URI
     * @param targetVolumes List of target volumes URI
     * @return workflow Method
     */
    public Method suspendSRDFGroupMethod(final URI systemURI, final RemoteDirectorGroup group, final List<URI> sourceVolumes,
            final List<URI> targetVolumes) {
        return new Workflow.Method(SUSPEND_SRDF_GROUP_METHOD, systemURI, group, sourceVolumes, targetVolumes);
    }

    /**
     * Method to suspend SRDF group called a workflow step.
     * 
     * @param systemURI Reference to storage system URI
     * @param group Reference to RemoteDirectorGroup which represents SRDF group.
     * @param sourceVolumes List of source volumes URI
     * @param targetVolumes List of target volumes URI
     * @param opId The stepId used for completion.
     * @return true if suspend is successful else false
     */
    public boolean suspendSrdfGroupStep(final URI systemURI, final RemoteDirectorGroup group, final List<URI> sourceVolumes,
            final List<URI> targetVolumes, String opId) {
        log.info("START Suspend SRDF group {} for {}", group.getLabel(), systemURI);
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            List<Volume> volumes = utils.getAssociatedVolumesForSRDFGroup(system, group);
            Collection<Volume> tgtVolumes = newArrayList(filter(volumes, utils.volumePersonalityPredicate(TARGET)));

            if (!tgtVolumes.isEmpty() && tgtVolumes.iterator().hasNext()) {
                List<URI> combinedVolumeList = new ArrayList<URI>();
                combinedVolumeList.addAll(sourceVolumes);
                combinedVolumeList.addAll(targetVolumes);
                completer = new SRDFTaskCompleter(combinedVolumeList, opId);
                getRemoteMirrorDevice().doSuspendLink(system, tgtVolumes.iterator().next(), false, false, completer);
            } else {
                log.info("There are no more volumes in the SRDF group {} {}, so no need to call suspend.", group.getLabel(), group.getId());
                WorkflowStepCompleter.stepSucceded(opId);
            }

        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    public boolean resumeSyncPairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final String opId) {
        log.info("START Resume Sync Pair");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            Volume targetVolume = dbClient.queryObject(Volume.class, targetURI);
            completer = new SRDFTaskCompleter(sourceURI, targetURI, opId);
            getRemoteMirrorDevice().doResumeLink(system, targetVolume, false, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    public Method resumeSyncPairMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI) {
        return new Workflow.Method(CREATE_SRDF_RESUME_PAIR_METHOD, systemURI, sourceURI, targetURI);
    }

    public boolean restoreStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final String opId) {
        log.info("START Restore");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            Volume targetVolume = dbClient.queryObject(Volume.class, targetURI);
            completer = new SRDFLinkSyncCompleter(Arrays.asList(sourceURI, targetURI), opId);
            getRemoteMirrorDevice().doSyncLink(system, targetVolume, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    public Method restoreMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI) {
        return new Workflow.Method(RESTORE_METHOD, systemURI, sourceURI, targetURI);
    }

    private Method reSyncSRDFLinkMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI) {
        return new Workflow.Method(CREATE_SRDF_RESYNC_PAIR_METHOD, systemURI, sourceURI, targetURI);
    }

    public boolean reSyncSRDFLinkStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final String opId) {
        log.info("START ReSync SRDF Links");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new SRDFTaskCompleter(sourceURI, targetURI, opId);
            getRemoteMirrorDevice().doResyncLink(system, sourceURI, targetURI, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    private Workflow.Method rollbackSRDFLinksMethod(final URI systemURI, final List<URI> sourceURIs,
            final List<URI> targetURIs, final boolean isGroupRollback) {
        return new Workflow.Method(ROLLBACK_SRDF_LINKS_METHOD, systemURI, sourceURIs, targetURIs, isGroupRollback);
    }

    // Convenience method for singular usage of #rollbackSRDFLinksMethod
    private Workflow.Method rollbackSRDFLinkMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI, final boolean isGroupRollback) {
        return rollbackSRDFLinksMethod(systemURI, asList(sourceURI), asList(targetURI), isGroupRollback);
    }

    public boolean rollbackSRDFLinksStep(URI systemURI, List<URI> sourceURIs,
            List<URI> targetURIs, boolean isGroupRollback, String opId) {
        log.info("START rollback multiple SRDF links");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new SRDFMirrorRollbackCompleter(sourceURIs, opId);
            getRemoteMirrorDevice().doRollbackLinks(system, sourceURIs, targetURIs, isGroupRollback, completer);
        } catch (Exception e) {
            log.error("Ignoring exception while rolling back SRDF sources: {}", sourceURIs, e);
            // Succeed here, to allow other rollbacks to run
            if (null != completer) {
                completer.ready(dbClient);
            }
            WorkflowStepCompleter.stepSucceded(opId);
            return false;
        }
        return true;
    }

    private Workflow.Method
            createListReplicasMethod(URI systemURI, List<URI> sourceURIs, List<URI> targetURIs, boolean addWaitForCopyState) {
        return new Workflow.Method(CREATE_LIST_REPLICAS_METHOD, systemURI, sourceURIs, targetURIs, addWaitForCopyState);
    }

    public boolean createListReplicas(URI systemURI, List<URI> sourceURIs, List<URI> targetURIs, boolean addWaitForCopyState, String opId) {
        log.info("START Creating list of replicas");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);

            List<URI> combined = new ArrayList<>();
            combined.addAll(sourceURIs);
            combined.addAll(targetURIs);

            completer = new SRDFMirrorCreateCompleter(combined, null, opId);
            getRemoteMirrorDevice().doCreateListReplicas(system, sourceURIs, targetURIs, addWaitForCopyState, completer);
            log.info("Sources: {}", Joiner.on(',').join(sourceURIs));
            log.info("Targets: {}", Joiner.on(',').join(targetURIs));
            log.info("OpId: {}", opId);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    /**
     * Returns a Workflow.Method for updating volume properties.
     * 
     * @param volumeURIs List of volume URIs
     * @param systemURI Reference to storage system URI
     * @return Workflow.Method
     */
    private Workflow.Method updateVolumePropertiesMethod(List<URI> volumeURIs, URI systemURI) {
        return new Workflow.Method(UPDATE_VOLUME_PROEPERTIES_METHOD, volumeURIs, systemURI);
    }

    /**
     * Method to update volume properties called as a workflow step.
     * 
     * @param volumeURIs List of volume URIs
     * @param systemURI Reference to storage system URI
     * @param opId The stepId used for completion.
     * @return true if update is successful else false
     */
    public boolean updateVolumeProperties(List<URI> volumeURIs, URI systemURI, String opId) {
        log.info("Update volume properties...");
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            getRemoteMirrorDevice().refreshVolumeProperties(systemURI, volumeURIs);
            log.info("Volumes: {}", Joiner.on(',').join(volumeURIs));
            log.info("OpId: {}", opId);
            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            log.warn("Failed to update properties for volumes {} " + volumeURIs);
            log.error("Failed to update properties for volumes: {} " + e);
            // We don't want to fail the workflow if we fail to update volume properties this is going to be the best effort.
            WorkflowStepCompleter.stepSucceded(opId);
            return true;
        }
        return true;
    }

    private Workflow.Method createSRDFVolumePairMethod(final URI systemURI,
            final URI sourceURI, final URI targetURI, final URI vpoolChangeUri) {
        return new Workflow.Method(CREATE_SRDF_VOLUME_PAIR, systemURI, sourceURI, targetURI, vpoolChangeUri);
    }

    public boolean createSRDFVolumePairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final URI vpoolChangeUri, final String opId) {
        log.info("START Add srdf volume pair");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new SRDFMirrorCreateCompleter(sourceURI, targetURI, vpoolChangeUri, opId);
            getRemoteMirrorDevice().doCreateLink(system, sourceURI, targetURI, completer);
            log.info("Source: {}", sourceURI);
            log.info("Target: {}", targetURI);
            log.info("OpId: {}", opId);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    /**
     * This method creates steps to create SRDF pairs in an empty SRDF group.
     * 
     * @param sourceDescriptors list of source volume descriptors
     * @param targetDescriptors list of target volume descriptors
     * @param group reference to RemoteDirectorGroup
     * @param uriVolumeMap map of volume URI to volume object
     * @param waitFor String waitFor of previous step, we wait on this to complete
     * @param workflow Reference to Workflow
     * @return stepId
     **/

    private String createNonCGSrdfPairStepsOnEmptyGroup(List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors, RemoteDirectorGroup group, Map<URI, Volume> uriVolumeMap,
            String waitFor, Workflow workflow) {

        StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
        log.info("VPoolChange URI {}", vpoolChangeUri);
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = new ArrayList<>();

        for (URI sourceURI : sourceURIs) {
            Volume source = uriVolumeMap.get(sourceURI);
            StringSet srdfTargets = source.getSrdfTargets();
            for (String targetStr : srdfTargets) {
                URI targetURI = URI.create(targetStr);
                targetURIs.add(targetURI);
            }
        }

        /*
         * Invoke CreateListReplica with all source/target pairings.
         */
        Method createListMethod = createListReplicasMethod(system.getId(), sourceURIs, targetURIs, true);
        // false here because we want to rollback individual links not the entire (pre-existing) group.
        Method rollbackMethod = rollbackSRDFLinksMethod(system.getId(), sourceURIs, targetURIs, false);

        String stepId = workflow.createStep(CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP,
                CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_DESC, waitFor, system.getId(),
                system.getSystemType(), getClass(), createListMethod, rollbackMethod, null);

        return stepId;

    }

    /**
     * This method creates steps to create SRDF pairs in a populated SRDF group.
     * 
     * @param sourceDescriptors list of source volume descriptors
     * @param targetDescriptors list of target volume descriptors
     * @param group reference to RemoteDirectorGroup
     * @param uriVolumeMap map of volume URI to volume object
     * @param waitFor String waitFor of previous step, we wait on this to complete
     * @param workflow Reference to Workflow
     * @return stepId
     **/
    private String createNonCGSrdfPairStepsOnPopulatedGroup(List<VolumeDescriptor> sourceDescriptors,
            List<VolumeDescriptor> targetDescriptors, RemoteDirectorGroup group, Map<URI, Volume> uriVolumeMap,
            String waitFor, Workflow workflow) {

        StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
        URI vpoolChangeUri = getVirtualPoolChangeVolume(sourceDescriptors);
        log.info("VPoolChange URI {}", vpoolChangeUri);
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = new ArrayList<>();

        for (URI sourceURI : sourceURIs) {
            Volume source = uriVolumeMap.get(sourceURI);
            StringSet srdfTargets = source.getSrdfTargets();
            for (String targetStr : srdfTargets) {
                URI targetURI = URI.create(targetStr);
                targetURIs.add(targetURI);
            }
        }

        /*
         * Invoke Suspend on the SRDF group as more ACTIVE pairs cannot added until all other
         * existing pairs are in NOT-READY state
         */
        Method suspendGroupMethod = suspendSRDFGroupMethod(system.getId(), group, sourceURIs, targetURIs);
        Method resumeRollbackMethod = resumeSRDFGroupMethod(system.getId(), group, sourceURIs, targetURIs);

        String suspendGroupStep = workflow.createStep(CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP,
                SUSPEND_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                system.getSystemType(), getClass(), suspendGroupMethod, resumeRollbackMethod, null);

        /*
         * Invoke CreateListReplica with all source/target pairings.
         */
        Method createListMethod = createListReplicasMethod(system.getId(), sourceURIs, targetURIs, false);
        // false here because we want to rollback individual links not the entire (pre-existing) group.
        Method rollbackMethod = rollbackSRDFLinksMethod(system.getId(), sourceURIs, targetURIs, false);

        String createListReplicaStep = workflow.createStep(CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP,
                CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_DESC, suspendGroupStep, system.getId(),
                system.getSystemType(), getClass(), createListMethod, rollbackMethod, null);

        /*
         * Invoke Resume on the SRDF group to get all pairs back in the READY state.
         */
        Method resumeGroupMethod = resumeSRDFGroupMethod(system.getId(), group, sourceURIs, targetURIs);
        String resumeGroupStep = workflow.createStep(CREATE_SRDF_ACTIVE_VOLUME_PAIR_STEP_GROUP,
                RESUME_SRDF_MIRRORS_STEP_DESC, createListReplicaStep, system.getId(),
                system.getSystemType(), getClass(), resumeGroupMethod, rollbackMethodNullMethod(), null);

        return resumeGroupStep;
    }

    /**
     * This method creates step to refresh volume properties.
     * 
     * @param volumeDescriptors List of volume descriptors
     * @param system reference to storage system
     * @param waitFor String waitFor of previous step, we wait on this to complete
     * @param workflow Reference to Workflow
     * @return stepId
     */
    private String refreshVolumeProperties(List<VolumeDescriptor> volumeDescriptors, StorageSystem system,
            String waitFor, Workflow workflow) {

        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);

        Method updateVolumePropertiesMethod = updateVolumePropertiesMethod(targetURIs, system.getId());
        Method rollbackMethod = rollbackMethodNullMethod();

        String stepId = workflow.createStep(REFRESH_VOLUME_PROPERTIES_STEP,
                REFRESH_VOLUME_PROPERTIES_STEP_DESC, waitFor, system.getId(),
                system.getSystemType(), getClass(), updateVolumePropertiesMethod, rollbackMethod, null);

        return stepId;
    }

    private Method rollbackAddSyncVolumePairMethod(final URI systemURI,
            final List<URI> sourceURIs, final List<URI> targetURIs,
            final boolean isGroupRollback) {
        return new Workflow.Method(ROLLBACK_ADD_SYNC_VOLUME_PAIR_METHOD,
                systemURI, sourceURIs, targetURIs, isGroupRollback);
    }

    public boolean rollbackAddSyncVolumePairStep(final URI systemURI,
            final List<URI> sourceURIs, final List<URI> targetURIs,
            final boolean isGroupRollback, final String opId) {
        log.info("START rollback srdf volume pair");
        TaskCompleter completer = new SRDFMirrorRollbackCompleter(sourceURIs,
                opId);
        try {
            // removePairFromGroup step not required as addPair failed
            completer.ready(dbClient);
            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            log.warn("Error during rollback for adding sync pairs", e);
        }
        return true;
    }

    private Method addVolumePairsToCgMethod(URI systemURI, List<URI> sourceURIs, URI remoteDirectorGroupURI, URI vpoolChangeUri) {
        return new Workflow.Method(ADD_SYNC_VOLUME_PAIRS_METHOD, systemURI, sourceURIs, remoteDirectorGroupURI, vpoolChangeUri);
    }

    public boolean addVolumePairsToCgMethodStep(URI systemURI, List<URI> sourceURIs, URI remoteDirectorGroupURI, URI vpoolChangeUri,
            String opId) {
        log.info("START Add VolumePair to CG");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new SRDFAddPairToGroupCompleter(sourceURIs, vpoolChangeUri, opId);
            getRemoteMirrorDevice().doAddVolumePairsToCg(system, sourceURIs, remoteDirectorGroupURI, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    public Workflow.Method suspendSRDFLinkMethod(URI systemURI, URI sourceURI, URI targetURI, boolean consExempt) {
        return new Workflow.Method(SUSPEND_SRDF_LINK_METHOD, systemURI, sourceURI, targetURI, consExempt);
    }

    public boolean suspendSRDFLinkStep(URI systemURI, URI sourceURI, URI targetURI, boolean consExempt, String opId) {
        log.info("START Suspend SRDF link");
        TaskCompleter completer = null;

        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            List<URI> combined = Arrays.asList(sourceURI, targetURI);
            completer = new SRDFLinkPauseCompleter(combined, opId);
            getRemoteMirrorDevice().doSuspendLink(system, target, consExempt, false, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }

        return true;
    }

    public Workflow.Method splitSRDFLinkMethod(URI systemURI, URI sourceURI, URI targetURI, boolean rollback) {
        return new Workflow.Method(SPLIT_SRDF_LINK_METHOD, systemURI, sourceURI, targetURI, rollback);
    }

    public boolean splitSRDFLinkStep(URI systemURI, URI sourceURI, URI targetURI,
            boolean rollback, String opId) {
        log.info("START Split SRDF link");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            Volume targetVolume = dbClient.queryObject(Volume.class, targetURI);
            List<URI> combined = Arrays.asList(sourceURI, targetURI);
            completer = new SRDFLinkPauseCompleter(combined, opId);
            getRemoteMirrorDevice().doSplitLink(system, targetVolume, rollback, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return true;
    }

    private Workflow.Method removeDeviceGroupsMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI) {
        return new Workflow.Method(REMOVE_DEVICE_GROUPS_METHOD, systemURI, sourceURI, targetURI);
    }

    public boolean removeDeviceGroupsStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final String opId) {
        log.info("START remove device groups");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            List<URI> combined = Arrays.asList(sourceURI, targetURI);
            completer = new SRDFRemoveDeviceGroupsCompleter(combined, opId);
            getRemoteMirrorDevice().doRemoveDeviceGroups(system, sourceURI, targetURI, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return false;
    }

    private Method createSrdfCgPairsMethod(URI system, List<URI> sourceURIs, List<URI> targetURIs, URI vpoolChangeUri) {
        return new Method(CREATE_SRDF_ASYNC_MIRROR_METHOD, system, sourceURIs, targetURIs, vpoolChangeUri);
    }

    public boolean createSrdfCgPairsStep(URI systemURI, List<URI> sourceURIs, List<URI> targetURIs, URI vpoolChangeUri, String opId) {
        log.info("START creating SRDF Pairs in CGs");
        SRDFMirrorCreateCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            List<URI> combined = new ArrayList<URI>(sourceURIs);
            combined.addAll(targetURIs);
            completer = new SRDFMirrorCreateCompleter(combined, vpoolChangeUri, opId);
            getRemoteMirrorDevice().doCreateCgPairs(system, sourceURIs, targetURIs, completer);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }
        return false;
    }

    /**
     * Convenience method to build a Map of URI's to their respective Volumes based on a List of
     * VolumeDescriptor.
     * 
     * @param volumeDescriptors List of volume descriptors
     * @return Map of URI to Volume
     */
    private Map<URI, Volume> queryVolumes(final List<VolumeDescriptor> volumeDescriptors) {
        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        List<Volume> volumes = dbClient.queryObject(Volume.class, volumeURIs);
        Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
        for (Volume volume : volumes) {
            if (volume != null) {
                volumeMap.put(volume.getId(), volume);
            }
        }
        return volumeMap;
    }

    private Set<String> findVolumesPartOfRDFGroups(StorageSystem system, RemoteDirectorGroup rdfGroup) {
        return getRemoteMirrorDevice().findVolumesPartOfRemoteGroup(system, rdfGroup);
    }

    private RemoteMirroring getRemoteMirrorDevice() {
        return (RemoteMirroring) devices.get(StorageSystem.Type.vmax.toString());
    }

    private StorageSystem getStorageSystem(final URI systemURI) {
        return dbClient.queryObject(StorageSystem.class, systemURI);
    }

    @Override
    public void connect(final URI protection) throws InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void disconnect(final URI protection) throws InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void discover(final AsyncTask[] tasks) throws InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void performProtectionOperation(final URI systemUri, final Copy copy,
            final String op, final String task) throws InternalException {
        TaskCompleter completer = null;
        try {
            URI sourceVolumeUri = null;
            StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
            Volume volume = dbClient.queryObject(Volume.class, copy.getCopyID());
            List<String> targetVolumeUris = new ArrayList<String>();
            List<URI> combined = new ArrayList<URI>();
            if (PersonalityTypes.SOURCE.toString().equalsIgnoreCase(volume.getPersonality())) {
                targetVolumeUris.addAll(volume.getSrdfTargets());
                sourceVolumeUri = volume.getId();
                combined.add(sourceVolumeUri);
                combined.addAll(transform(volume.getSrdfTargets(), FCTN_STRING_TO_URI));
            } else {
                sourceVolumeUri = volume.getSrdfParent().getURI();
                targetVolumeUris.add(volume.getId().toString());
                combined.add(sourceVolumeUri);
                combined.add(volume.getId());
            }

            /**
             * Async WITHOUT CG
             * SRDF operations will be happening for all volumes available on ra group.
             * Hence adding the missing source volume ids in the taskCompleter to change the accessState and linkStatus field.
             */
            Volume targetVol, sourceVol = null;
            sourceVol = dbClient.queryObject(Volume.class, sourceVolumeUri);
            Iterator<String> taregtVolumeUrisIterator = targetVolumeUris.iterator();
            if (taregtVolumeUrisIterator.hasNext()) {
                targetVol = dbClient.queryObject(Volume.class, URI.create(taregtVolumeUrisIterator.next()));
                if (targetVol != null && Mode.ASYNCHRONOUS.toString().equalsIgnoreCase(targetVol.getSrdfCopyMode())
                        && !targetVol.hasConsistencyGroup()) {
                    List<Volume> associatedSourceVolumeList = utils.getRemainingSourceVolumesForAsyncRAGroup(sourceVol, targetVol);

                    for (Volume vol : associatedSourceVolumeList) {
                        if (!combined.contains(vol.getId())) {
                            combined.add(vol.getId());
                        }
                    }
                }
            }
            /**
             * Needs to add all SRDF source volumes id to change the linkStatus and accessState
             * for Sync/Async with CG
             */
            if (sourceVol != null && sourceVol.hasConsistencyGroup()) {
                List<URI> srcVolumeUris = dbClient.queryByConstraint(
                        getVolumesByConsistencyGroup(sourceVol.getConsistencyGroup()));
                for (URI uri : srcVolumeUris) {
                    if (!combined.contains(uri)) {
                        combined.add(uri);
                    }
                }
            }
            log.info("Combined ids : {}", Joiner.on("\t").join(combined));
            if (op.equalsIgnoreCase("failover")) {
                completer = new SRDFLinkFailOverCompleter(combined, task);
                getRemoteMirrorDevice().doFailoverLink(system, volume, completer);
            } else if (op.equalsIgnoreCase("failover-cancel")) {
                completer = new SRDFLinkFailOverCancelCompleter(combined, task);
                getRemoteMirrorDevice().doFailoverCancelLink(system, volume, completer);
            } else if (op.equalsIgnoreCase("swap")) {
                completer = new SRDFSwapCompleter(combined, task);
                getRemoteMirrorDevice().doSwapVolumePair(system, volume, completer);
            } else if (op.equalsIgnoreCase("pause")) {
                completer = new SRDFLinkPauseCompleter(combined, task);
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doSplitLink(targetSystem, targetVolume, false, completer);
                }
            } else if (op.equalsIgnoreCase("suspend")) {
                completer = new SRDFLinkSuspendCompleter(combined, task);
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doSuspendLink(targetSystem, targetVolume, false, true, completer);
                }
            } else if (op.equalsIgnoreCase("resume")) {
                completer = new SRDFLinkResumeCompleter(combined, task);
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doResumeLink(targetSystem, targetVolume, true, completer);
                }
            } else if (op.equalsIgnoreCase("start")) {
                completer = new SRDFLinkStartCompleter(combined, task);
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doStartLink(targetSystem, targetVolume, completer);
                }
            } else if (op.equalsIgnoreCase("sync")) {
                completer = new SRDFLinkSyncCompleter(combined, task);
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doSyncLink(targetSystem, targetVolume, completer);
                }
            } else if (op.equalsIgnoreCase("stop")) {
                completer = new SRDFLinkStopCompleter(combined, task);
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doStopLink(targetSystem, targetVolume, completer);
                }
            } else if (op.equalsIgnoreCase("change-copy-mode")) {
                completer = new SRDFChangeCopyModeTaskCompleter(combined, task, copy.getCopyMode());
                for (String target : targetVolumeUris) {
                    Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(target));
                    StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                            targetVolume.getStorageController());
                    getRemoteMirrorDevice().doChangeCopyMode(targetSystem, targetVolume, completer);
                }
            }
        } catch (Exception e) {
            log.error("Failed operation {}", op, e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
        }
    }

    private String addExpandBlockVolumeSteps(Workflow workflow, String waitFor, URI pool, URI sourceVolumeUri, Long size, String token)
            throws InternalException {
        Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolumeUri);
        // add step to expand the source
        createExpandStep(workflow, waitFor, size, sourceVolume.getId().toString(), "Source volume expand subtask: ");
        // add steps to expand the targets
        StringSet targets = sourceVolume.getSrdfTargets();
        for (String target : targets) {
            createExpandStep(workflow, waitFor, size, target, "Target volume expand subtask: ");
        }
        return STEP_VOLUME_EXPAND;
    }

    private void createExpandStep(Workflow workflow, String waitFor, Long size, String volumeURI, String description) {
        Volume volume = dbClient.queryObject(Volume.class, URI.create(volumeURI));
        if (volume != null) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
            String createStepId = workflow.createStepId();
            workflow.createStep(STEP_VOLUME_EXPAND, description.concat(volume.getLabel()),
                    waitFor, system.getId(), system.getSystemType(), BlockDeviceController.class,
                    BlockDeviceController.expandVolumesMethod(system.getId(), volume.getPool(), volume.getId(), size),
                    BlockDeviceController.rollbackExpandVolumeMethod(system.getId(), volume.getId(), createStepId), createStepId);
        }

    }

    @Override
    public void expandVolume(URI storage, URI pool, URI volumeId, Long size, String task) throws InternalException {
        TaskCompleter completer = null;
        Workflow workflow = workflowService.getNewWorkflow(this, "expandVolume", true, task);
        String waitFor = null;
        try {
            Volume source = dbClient.queryObject(Volume.class, volumeId);
            StringSet targets = source.getSrdfTargets();
            List<URI> combined = Lists.newArrayList();

            combined.add(source.getId());
            combined.addAll(transform(targets, FCTN_STRING_TO_URI));
            completer = new SRDFExpandCompleter(combined, task);

            if (null != targets) {
                for (String targetURI : targets) {
                    Volume target = dbClient.queryObject(Volume.class, URI.create(targetURI));
                    log.info("target Volume {} with srdf group {}", target.getNativeGuid(),
                            target.getSrdfGroup());
                    RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
                    StorageSystem system = dbClient.queryObject(StorageSystem.class, group.getSourceStorageSystemUri());
                    Set<String> volumes = findVolumesPartOfRDFGroups(system, group);
                    if (group.getVolumes() == null) {
                        group.setVolumes(new StringSet());
                    }
                    group.getVolumes().replace(volumes);
                    dbClient.persistObject(group);

                    if (!source.hasConsistencyGroup()) {
                        // First we suspend the mirror...
                        Workflow.Method suspendMethod = suspendSRDFLinkMethod(system.getId(),
                                source.getId(), target.getId(), true);
                        // TODO Belongs as a rollback for the detach step
                        Workflow.Method rollbackMethod = createSRDFVolumePairMethod(system.getId(),
                                source.getId(), target.getId(), null);
                        String suspendStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                SPLIT_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                                system.getSystemType(), getClass(), suspendMethod, rollbackMethod, null);

                        // Second we detach the mirror...
                        Workflow.Method detachMethod = detachVolumePairMethod(system.getId(),
                                source.getId(), target.getId());
                        String detachStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                DETACH_SRDF_MIRRORS_STEP_DESC, suspendStep, system.getId(),
                                system.getSystemType(), getClass(), detachMethod, null, null);

                        // Expand the source and target Volumes
                        String expandStep = addExpandBlockVolumeSteps(workflow, detachStep, pool, volumeId, size, task);

                        // resync source and target again
                        createSyncSteps(workflow, expandStep, source, system);
                    } else {

                        if (volumes.size() == 1) {

                            // split all members the group
                            Workflow.Method splitMethod = splitSRDFLinkMethod(system.getId(),
                                    source.getId(), target.getId(), false);
                            String splitStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                    SPLIT_SRDF_MIRRORS_STEP_DESC, waitFor, system.getId(),
                                    system.getSystemType(), getClass(), splitMethod, null, null);

                            // Second we detach the group...
                            Workflow.Method detachMethod = detachGroupPairsMethod(system.getId(),
                                    source.getId(), target.getId());
                            Workflow.Method resumeSyncPairMethod = resumeSyncPairMethod(system.getId(),
                                    source.getId(), target.getId());
                            String detachMirrorStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                    DETACH_SRDF_MIRRORS_STEP_DESC, splitStep, system.getId(),
                                    system.getSystemType(), getClass(), detachMethod, resumeSyncPairMethod, null);

                            // Expand the source and target Volumes
                            String expandStep = addExpandBlockVolumeSteps(workflow, detachMirrorStep, pool, volumeId, size, task);

                            // re-establish again
                            List<URI> sourceURIs = new ArrayList<URI>();
                            sourceURIs.add(source.getId());
                            List<URI> targetURIs = new ArrayList<URI>();
                            targetURIs.add(target.getId());

                            Workflow.Method createGroupsMethod = createSrdfCgPairsMethod(system.getId(), sourceURIs, targetURIs, null);
                            workflow.createStep(CREATE_SRDF_MIRRORS_STEP_GROUP, CREATE_SRDF_MIRRORS_STEP_DESC, expandStep,
                                    system.getId(), system.getSystemType(), getClass(), createGroupsMethod, null, null);

                        } else {

                            // First we remove the sync pair from Async CG...
                            Workflow.Method removeAsyncPairMethod = removePairFromGroup(system.getId(),
                                    source.getId(), target.getId(), true);
                            List<URI> sourceUris = new ArrayList<URI>();
                            sourceUris.add(system.getId());

                            String removePairFromGroupWorkflowDesc = String.format(REMOVE_SRDF_PAIR_STEP_DESC, target.getSrdfCopyMode());
                            String detachVolumePairWorkflowDesc = String.format(DETACH_SRDF_PAIR_STEP_DESC, target.getSrdfCopyMode());

                            Workflow.Method addSyncPairMethod = addVolumePairsToCgMethod(system.getId(),
                                    sourceUris, group.getId(), null);

                            String removeAsyncPairStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                    removePairFromGroupWorkflowDesc, waitFor, system.getId(),
                                    system.getSystemType(), getClass(), removeAsyncPairMethod, addSyncPairMethod, null);

                            // split the removed async pair
                            Workflow.Method suspend = suspendSRDFLinkMethod(system.getId(),
                                    source.getId(), target.getId(), true);
                            Workflow.Method resumeSyncPairMethod = resumeSyncPairMethod(system.getId(),
                                    source.getId(), target.getId());
                            String suspendStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                    SPLIT_SRDF_MIRRORS_STEP_DESC, removeAsyncPairStep, system.getId(),
                                    system.getSystemType(), getClass(), suspend, resumeSyncPairMethod, null);

                            // Finally we detach the removed async pair...
                            Workflow.Method detachAsyncPairMethod = detachVolumePairMethod(system.getId(),
                                    source.getId(), target.getId());
                            Workflow.Method createSyncPairMethod = createSRDFVolumePairMethod(system.getId(),
                                    source.getId(), target.getId(), null);
                            String detachStep = workflow.createStep(DELETE_SRDF_MIRRORS_STEP_GROUP,
                                    detachVolumePairWorkflowDesc, suspendStep, system.getId(),
                                    system.getSystemType(), getClass(), detachAsyncPairMethod, createSyncPairMethod, null);

                            // Expand the source and target Volumes
                            String expandStep = addExpandBlockVolumeSteps(workflow, detachStep, pool, volumeId, size, task);

                            // create Relationship again
                            createSrdfCGPairStepsOnPopulatedGroup(source, expandStep, workflow);
                        }
                    }

                }
            }
            String successMessage = String.format("Workflow of SRDF Expand Volume %s successfully created",
                    volumeId);
            workflow.executePlan(completer, successMessage);
        } catch (Exception e) {
            log.error("Failed SRDF Expand Volume operation ", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
        }
    }

    private URI getVirtualPoolChangeVolume(List<VolumeDescriptor> volumeDescriptors) {
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            if (volumeDescriptor.getParameters() != null) {
                if (volumeDescriptor.getParameters().get(
                        VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID) != null) {
                    return (URI) volumeDescriptor.getParameters().get(
                            VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID);
                }
            }
        }
        return null;
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId,
            VolumeWorkflowCompleter completer) {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws InternalException {
        // TODO : JIRA CTRL-5335 SRDF expand needs to go via BlockOrchestrationController. Implement expand here.
        return null;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow,
            String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor,
            URI storage, URI pool, URI volume, URI snapshot,
            Boolean updateOpStatus, String syncDirection, String taskId,
            BlockSnapshotRestoreCompleter completer) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    public void setUtils(SRDFUtils utils) {
        this.utils = utils;
    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     * 
     * @return A workflow method
     */
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

    /**
     * Attempts to acquire a workflow lock based on the RDF group name.
     * 
     * @param workflow
     * @param locks
     * @throws LockRetryException
     */
    private void acquireWorkflowLockOrThrow(Workflow workflow, List<String> locks) throws LockRetryException {
        log.info("Attempting to acquire workflow lock {}", Joiner.on(',').join(locks));
        workflowService.acquireWorkflowLocks(workflow, locks,
                LockTimeoutValue.get(LockType.SRDF_PROVISIONING));
    }

    private List<String> generateLocks(List<VolumeDescriptor> volumeDescriptors, Map<URI, Volume> uriVolumeMap) {
        // List of resulting locks
        List<String> locks = new ArrayList<>();

        // Resources for building the locks
        Volume firstTarget = getFirstTarget(volumeDescriptors, uriVolumeMap);
        Volume source = uriVolumeMap.get(firstTarget.getSrdfParent().getURI());

        if (source == null) {
            log.error("Source volume was not found: {}", firstTarget.getSrdfParent().getURI());
            throw DeviceControllerException.exceptions.invalidObjectNull();
        }

        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, source.getStorageController());
        RemoteDirectorGroup rdfGroup = dbClient.queryObject(RemoteDirectorGroup.class, firstTarget.getSrdfGroup());

        // Generate the locks
        locks.add(generateRDFGroupLock(sourceSystem, rdfGroup));

        return locks;
    }

    private String generateRDFGroupLock(StorageSystem sourceSystem, RemoteDirectorGroup rdfGroup) {
        return sourceSystem.getSerialNumber() + "-rdfg-" + rdfGroup.getSourceGroupId();
    }
}
