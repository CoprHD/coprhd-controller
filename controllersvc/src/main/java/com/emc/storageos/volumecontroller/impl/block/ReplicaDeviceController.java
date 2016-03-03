/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToID;
import static com.emc.storageos.volumecontroller.impl.ControllerUtils.checkSnapshotSessionConsistencyGroup;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Specific controller implementation to support block orchestration for handling replicas of volumes in a consistency group.
 */
public class ReplicaDeviceController implements Controller, BlockOrchestrationInterface {
    private static final Logger log = LoggerFactory.getLogger(ReplicaDeviceController.class);
    private DbClient _dbClient;
    private BlockDeviceController _blockDeviceController;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setBlockDeviceController(BlockDeviceController blockDeviceController) {
        this._blockDeviceController = blockDeviceController;
    }

    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // Get the list of descriptors which represent source volumes that have
        // just been created and added to CG possibly
        List<VolumeDescriptor> volumeDescriptors = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA, VolumeDescriptor.Type.SRDF_SOURCE,
                        VolumeDescriptor.Type.SRDF_EXISTING_SOURCE }, null);

        // If no source volumes, just return
        if (volumeDescriptors.isEmpty()) {
            log.info("No replica steps required");
            return waitFor;
        }

        // Get the consistency group. If no consistency group for source
        // volumes,
        // just return. Get CG from any descriptor.
        URI cgURI = null;
        final VolumeDescriptor firstVolumeDescriptor = volumeDescriptors.get(0);
        if (firstVolumeDescriptor != null) {
            Volume volume = _dbClient.queryObject(Volume.class, firstVolumeDescriptor.getVolumeURI());
            if (!(volume != null && volume.isInCG() &&
                    (ControllerUtils.isVmaxVolumeUsing803SMIS(volume, _dbClient) || ControllerUtils.isNotInRealVNXRG(volume, _dbClient)))) {
                log.info("No replica steps required");
                return waitFor;
            }
            log.info("CG URI:{}", volume.getConsistencyGroup());
            cgURI = volume.getConsistencyGroup();
        }

        // if array consistency in disabled in CG and VPLEX/RP provisioning, skip creating replicas.
        // Reason:Provisioning new volumes for VPLEX/RP CG in Application does not add backend volume to RG
        if (!NullColumnValueGetter.isNullURI(cgURI)) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            if (!cg.getArrayConsistency() && isBackendVolumeForVplexOrRp(volumes)) {
                log.info("No replica steps required for CG {} as array consistency is disabled.", cg.getLabel());
                return waitFor;
            }
        }
        
        List<VolumeDescriptor> nonSrdfVolumeDescriptors = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA }, null);

        if (nonSrdfVolumeDescriptors != null && !nonSrdfVolumeDescriptors.isEmpty()) {
            waitFor = createReplicaIfCGHasReplica(workflow, waitFor,
                    nonSrdfVolumeDescriptors, cgURI);
        } else {
            // Create Replica for SRDF R1 and R2 if any replica available already
            List<VolumeDescriptor> srdfSourceVolumeDescriptors = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.SRDF_SOURCE,
                            VolumeDescriptor.Type.SRDF_EXISTING_SOURCE }, null);
            log.debug("srdfSourceVolumeDescriptors :{}", srdfSourceVolumeDescriptors);
            List<VolumeDescriptor> srdfTargetVolumeDescriptors = VolumeDescriptor.filterByType(volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.SRDF_TARGET }, null);
            log.debug("srdfTargetVolumeDescriptors :{}", srdfTargetVolumeDescriptors);
            // Create replica for R1
            waitFor = createReplicaIfCGHasReplica(workflow, waitFor,
                    srdfSourceVolumeDescriptors, cgURI);

            // get target CG
            // New Target Volume Descriptors and Volume objects will not have CG URI set
            final URIQueryResultList uriQueryResultList = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getBlockObjectsByConsistencyGroup(cgURI.toString()),
                    uriQueryResultList);
            Iterator<URI> volumeItr = uriQueryResultList.iterator();
            List<URI> newSourceVolumes = new ArrayList<URI>();
            for (VolumeDescriptor volumeDesc : srdfSourceVolumeDescriptors) {
                newSourceVolumes.add(volumeDesc.getVolumeURI());
            }
            URI targetVolumeCGURI = null;
            while (volumeItr.hasNext()) {
                URI volumeURI = volumeItr.next();
                if (!newSourceVolumes.contains(volumeURI)) {
                    Volume existingSourceVolume = _dbClient.queryObject(Volume.class, volumeURI);
                    Volume existingTargetVolume = null;
                    // get target
                    StringSet targets = existingSourceVolume.getSrdfTargets();
                    if (targets != null) {
                        for (String target : targets) {
                            if (NullColumnValueGetter.isNotNullValue(target)) {
                                existingTargetVolume = _dbClient.queryObject(Volume.class, URI.create(target));
                                targetVolumeCGURI = existingTargetVolume.getConsistencyGroup();
                                break;
                            }
                        }
                    }
                    break;
                }
            }

            waitFor = createReplicaIfCGHasReplica(workflow, waitFor,
                    srdfTargetVolumeDescriptors, targetVolumeCGURI);

        }

        return waitFor;
    }

    /**
     * Checks if the requested volume descriptors has a mix of Block and VPLEX/RP volumes
     *
     * @param volumes all volume descriptors
     * @return true, if is backend volume for vplex or rp
     */
    private boolean isBackendVolumeForVplexOrRp(List<VolumeDescriptor> volumes) {
        // Get only the block volumes from the descriptors
        List<VolumeDescriptor> blockVolumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA },
                new VolumeDescriptor.Type[] {});

        // Get only the VPlex volumes from the descriptors
        List<VolumeDescriptor> vplexVolumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                new VolumeDescriptor.Type[] {});

        // Get only the RP volumes from the descriptors
        List<VolumeDescriptor> protectedVolumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_TARGET,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET,
                        VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE,
                        VolumeDescriptor.Type.RP_JOURNAL,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL },
                new VolumeDescriptor.Type[] {});

        if (!blockVolumes.isEmpty() && (!vplexVolumes.isEmpty() || !protectedVolumes.isEmpty())) {
            return true;
        }
        return false;
    }

    /**
     * Creates replica snap/clone/mirror for the newly created volume, if the existing CG Volume has any replica.
     * 
     * @param workflow
     * @param waitFor
     * @param volumeDescriptors
     * @param cgURI
     * @return
     */
    private String createReplicaIfCGHasReplica(Workflow workflow,
            String waitFor, List<VolumeDescriptor> volumeDescriptors, URI cgURI) {
        log.info("CG URI {}", cgURI);
        if (volumeDescriptors != null && !volumeDescriptors.isEmpty()) {
            VolumeDescriptor firstVolumeDescriptor = volumeDescriptors.get(0);
            if (firstVolumeDescriptor != null && cgURI != null) {
                // find member volumes in the group
                List<Volume> existingVolumesInCG = ControllerUtils.getVolumesPartOfCG(cgURI, _dbClient);
                URI storage = existingVolumesInCG.get(0).getStorageController();
                //We will not end up in more than 1 RG within a CG, hence taking System from CG is fine.
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
                if (checkIfCGHasCloneReplica(existingVolumesInCG)) {
                    log.info("Adding clone steps for create {} volumes", firstVolumeDescriptor.getType());
                    // create new clones for the newly created volumes
                    // add the created clones to clone groups
                    waitFor = createCloneSteps(workflow, waitFor, volumeDescriptors, existingVolumesInCG, cgURI);
                }

                if (checkIfCGHasMirrorReplica(existingVolumesInCG)) {
                    log.info("Adding mirror steps for create {} volumes", firstVolumeDescriptor.getType());
                    // create new mirrors for the newly created volumes
                    // add the created mirrors to mirror groups
                    waitFor = createMirrorSteps(workflow, waitFor, volumeDescriptors, existingVolumesInCG, cgURI);
                }

                List<BlockSnapshotSession> sessions = getSnapSessionsForCGVolume(existingVolumesInCG.get(0));
                boolean isExistingCGSnapShotAvailable = checkIfCGHasSnapshotReplica(existingVolumesInCG);
                boolean isExistingCGSnapSessionAvailable = sessions != null && !sessions.isEmpty();
                boolean isVMAX3ExistingVolume = ControllerUtils.isVmaxVolumeUsing803SMIS(existingVolumesInCG.get(0), _dbClient);

                List<URI> volumeListtoAddURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
                List<Volume> volumeListToAdd = ControllerUtils.queryVolumesByIterativeQuery(_dbClient, volumeListtoAddURIs);
                if (isVMAX3ExistingVolume) {
                    if (isVMAX3VolumeHasSessionOnly(isExistingCGSnapSessionAvailable, isExistingCGSnapShotAvailable)) {
                        log.info("Existing CG only has Snap Session, adding snap session steps for adding volumes");
                        processSnapSessions(existingVolumesInCG, workflow, waitFor, volumeListToAdd);
                    } else if (isVMAX3VolumeHasSnapshotOnly(isExistingCGSnapSessionAvailable, isExistingCGSnapShotAvailable)) {
                        // create new snapshots for the newly added volumes
                        // add the created snapshots to snapshot groups
                        Set<String> snapGroupNames = ControllerUtils.getSnapshotReplicationGroupNames(existingVolumesInCG,
                                _dbClient);
                        for (String snapGroupName : snapGroupNames) {
                            // we can use the same storage system as RG--> CG is 1:1 mapping
                            log.info("Existing CG only has Snapshots, adding snapshot steps for existing snap group {} adding volumes",
                                    snapGroupName);
                            waitFor = addSnapshotsToReplicationGroupStep(workflow, waitFor, storageSystem, volumeListToAdd,
                                    snapGroupName, cgURI);
                        }
                    } else if (isVMAX3VolumeHasSessionAndSnapshot(isExistingCGSnapSessionAvailable,isExistingCGSnapShotAvailable)) {
                        log.info("Existing CG has both Sessions and linked targets, adding snapshot and session steps");
                        processSnapSessionsAndLinkedTargets(existingVolumesInCG, workflow, waitFor, volumeListToAdd, cgURI);
                    }
                } else if (isExistingCGSnapShotAvailable) {
                    // non VMAX3 volume
                    log.info("Adding snapshot steps for adding volumes");
                    // create new snapshots for the newly added volumes
                    // add the created snapshots to snapshot groups
                    Set<String> snapGroupNames = ControllerUtils.getSnapshotReplicationGroupNames(existingVolumesInCG,
                            _dbClient);
                    for (String snapGroupName : snapGroupNames) {
                        waitFor = addSnapshotsToReplicationGroupStep(workflow, waitFor, storageSystem, volumeListToAdd,
                                snapGroupName, cgURI);
                    }
                }
            }

        }


        return waitFor;
    }

   

    /*
     * 1. for each newly created volumes in a CG, create a clone
     * 2. add all clones to an existing replication group
     */
    private String createCloneSteps(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> volumeDescriptors, List<Volume> volumeList, URI cgURI) {
        log.info("START create clone steps");
        List<URI> sourceList = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        List<Volume> volumes = new ArrayList<Volume>();
        Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class, sourceList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (volume != null && !volume.getInactive()) {
                volumes.add(volume);
            }
        }

        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

        Set<String> repGroupNames = ControllerUtils.getCloneReplicationGroupNames(volumeList, _dbClient);
        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        for (String repGroupName : repGroupNames) {
            waitFor = addClonesToReplicationGroupStep(workflow, waitFor, storageSystem, volumes, repGroupName, cgURI);
        }

        return waitFor;
    }

    private String addSnapshotsToReplicationGroupStep(final Workflow workflow, String waitFor,
            StorageSystem storageSystem,
            List<Volume> volumes,
            String repGroupName, URI cgURI) {
        log.info("START create snapshot step");
        URI storage = storageSystem.getId();
        List<URI> snapshotList = new ArrayList<>();
        for (Volume volume : volumes) {
            BlockSnapshot snapshot = prepareSnapshot(volume, repGroupName);
            URI snapshotId = snapshot.getId();
            snapshotList.add(snapshotId);
        }

        Workflow.Method createMethod = new Workflow.Method(
                BlockDeviceController.CREATE_LIST_SNAPSHOT_METHOD, storage, snapshotList, false, false);
        waitFor = workflow.createStep(BlockDeviceController.CREATE_SNAPSHOTS_STEP_GROUP,
                "Create list snapshot", waitFor, storage, storageSystem.getSystemType(),
                _blockDeviceController.getClass(),
                createMethod, _blockDeviceController.rollbackMethodNullMethod(), null);

        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                addToReplicationGroupMethod(storage, cgURI, repGroupName, snapshotList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for adding snapshot [%s] to group on device [%s]",
                Joiner.on("\t").join(snapshotList), storage));

        return waitFor;
    }

	public BlockSnapshotSession prepareSnapshotSessionFromSource(
			BlockObject sourceObj, String snapSessionLabel, String instanceLabel) {
		BlockSnapshotSession snapSession = new BlockSnapshotSession();
        URI sourceProject = ControllerUtils
				.querySnapshotSessionSourceProject(sourceObj, _dbClient);

		snapSession.setId(URIUtil.createId(BlockSnapshotSession.class));

        snapSession.setProject(new NamedURI(sourceProject, sourceObj
				.getLabel()));
        snapSession.setStorageController(sourceObj.getStorageController());
		snapSession.setParent(new NamedURI(sourceObj.getId(), sourceObj
				.getLabel()));

		snapSession.setLabel(instanceLabel);
		snapSession.setSessionLabel(ResourceOnlyNameGenerator
				.removeSpecialCharsForName(snapSessionLabel,
						SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));

        _dbClient.createObject(snapSession);
		return snapSession;
	}

    public String createSnapshotSessionsStep(final Workflow workflow, String waitFor,
            URI systemURI, List<Volume> volumes, String repGroupName, BlockSnapshotSession existingSession) {
        // create session for each volume (session's parent is volume. i.e as a non-CG session)
        for (Volume volume : volumes) {
            // session label should be same as group session's label
            // append source object name to session label to uniquely identify this session from RG session
            String instanceLabel = String.format("%s-%s", existingSession.getLabel(), volume.getLabel());
            String sessionLabel = existingSession.getSessionLabel();
            BlockSnapshotSession session = prepareSnapshotSessionFromSource(volume, sessionLabel, instanceLabel);
            log.info("adding snapshot session create step for volume {}", volume.getLabel());
            waitFor = _blockDeviceController.
                    addStepToCreateSnapshotSession(workflow, systemURI, session, repGroupName, waitFor);
        }

        return waitFor;
    }

    public String createSnapshotSessionAndLinkSessionStep(final Workflow workflow, String waitFor,
            URI systemURI,
            List<Volume> existingVolumes,
            List<Volume> volumes,
            BlockSnapshotSession existingSession, URI cgURI) {
        log.info("START create snapshot session and link session to targets step");
        // get existing snapshot groups
        Set<String> snapGroupNames = ControllerUtils.getSnapshotReplicationGroupNames(existingVolumes,
                _dbClient);

        for (Volume volume : volumes) {
            // session label should be same as group session's label
            // append source object name to session label to uniquely identify this session from RG session
            String instanceLabel = String.format("%s-%s", existingSession.getLabel(), volume.getLabel());
            String sessionLabel = existingSession.getSessionLabel();
            BlockSnapshotSession session = prepareSnapshotSessionFromSource(volume, sessionLabel, instanceLabel);
            log.info("adding snapshot session create step for volume {}", volume.getLabel());
            waitFor = _blockDeviceController.addStepToCreateSnapshotSession(workflow, systemURI, session,
                    existingSession.getReplicationGroupInstance(), waitFor);

            // Add step to remove volume from its Replication Group before linking its target
            // -volume was added to RG as part of create volume step
            // otherwise linking single target will fail when it sees the source in group
            waitFor = _blockDeviceController.addStepToRemoveFromConsistencyGroup(workflow, systemURI, cgURI, Arrays.asList(volume.getId()),
                    waitFor, true);

            // snapshot targets
            Map<String, List<URI>> snapGroupToSnapshots = new HashMap<String, List<URI>>();
            for (String snapGroupName : snapGroupNames) {
                String copyMode = ControllerUtils.getCopyModeFromSnapshotGroup(snapGroupName, systemURI, _dbClient);
                log.info("Existing snap group {}, copy mode {}", snapGroupName, copyMode);
                List<Map<URI, BlockSnapshot>> snapSessionSnapshots = new ArrayList<>();
                // prepare snapshot target
                BlockSnapshot blockSnapshot = prepareSnapshot(volume, snapGroupName);
                blockSnapshot.setCopyMode(copyMode);
                _dbClient.updateObject(blockSnapshot);
                if (snapGroupToSnapshots.get(snapGroupName) == null) {
                    snapGroupToSnapshots.put(snapGroupName, new ArrayList<URI>());
                }
                snapGroupToSnapshots.get(snapGroupName).add(blockSnapshot.getId());

                // Add steps to create new target and link them to the session
                waitFor = _blockDeviceController.addStepToLinkBlockSnapshotSessionTarget(workflow, systemURI, session,
                        blockSnapshot.getId(), copyMode, waitFor);
            }
            // Add step to add back the source volume to its group which was removed before linking target
            waitFor = _blockDeviceController.addStepToAddToConsistencyGroup(workflow, systemURI, cgURI,
                    volume.getReplicationGroupInstance(), Arrays.asList(volume.getId()), waitFor);

            // Add steps to add new targets to their snap groups
            for (Map.Entry<String, List<URI>> entry : snapGroupToSnapshots.entrySet()) {
                waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                        String.format("Updating consistency group  %s", cgURI), waitFor, systemURI,
                        _blockDeviceController.getDeviceType(systemURI), this.getClass(),
                        addToReplicationGroupMethod(systemURI, cgURI, entry.getKey(), entry.getValue()),
                        _blockDeviceController.rollbackMethodNullMethod(), null);
            }
        }

        return waitFor;
    }

    private String addSnapshotSessionsToReplicationGroupStep(Workflow workflow, String waitFor,
                                                             StorageSystem storageSystem,
                                                             List<Volume> volumes,
                                                             URI cgURI) {

        List<URI> volumeURIs = newArrayList(transform(volumes, fctnDataObjectToID()));
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating SnapVx sessions for consistency group %s", cgURI),
                waitFor, storageSystem.getId(), storageSystem.getSystemType(), this.getClass(),
                addSnapshotSessionsToConsistencyGroupMethod(storageSystem.getId(), cgURI, volumeURIs),
                _blockDeviceController.rollbackMethodNullMethod(), null);

        return waitFor;
    }

    private static Workflow.Method addSnapshotSessionsToConsistencyGroupMethod(URI storage, URI consistencyGroup, List<URI> addVolumesList) {
        return new Workflow.Method("addSnapshotSessionsToConsistencyGroup", storage, consistencyGroup, addVolumesList);
    }

    public boolean addSnapshotSessionsToConsistencyGroup(URI storage, URI consistencyGroup, List<URI> volumes, String opId)
            throws ControllerException {
        TaskCompleter taskCompleter = null;
        WorkflowStepCompleter.stepExecuting(opId);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            _blockDeviceController.getDevice(storageSystem.getSystemType()).doAddSnapshotSessionsToConsistencyGroup(
                    storageSystem, consistencyGroup, volumes, taskCompleter);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private String addClonesToReplicationGroupStep(final Workflow workflow, String waitFor, StorageSystem storageSystem,
            List<Volume> volumes, String repGroupName, URI cgURI) {
        log.info("START create clone step");
        URI storage = storageSystem.getId();
        List<URI> cloneList = new ArrayList<URI>();

        // For clones of new volumes added to Application, get the clone set name and set it
        String cloneSetName = null;
        List<Volume> fullCopies = ControllerUtils.getFullCopiesPartOfReplicationGroup(repGroupName, _dbClient);
        if (!fullCopies.isEmpty()) {
            cloneSetName = fullCopies.get(0).getFullCopySetName();
            if (cloneSetName == null || cloneSetName.isEmpty()) {
                Volume fullcopy = fullCopies.get(0);
                if(fullcopy.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                    // Get vplex virtual volume
                    final List<Volume> vplexVolumes = CustomQueryUtility
                            .queryActiveResourcesByConstraint(_dbClient, Volume.class,
                                    getVolumesByAssociatedId(fullcopy.getId().toString()));
                    if (vplexVolumes != null && !vplexVolumes.isEmpty()) {
                        cloneSetName = vplexVolumes.get(0).getFullCopySetName();
                    }
                }
            }
            log.info(String.format("CloneSetName : %s", cloneSetName));
        }

        for (Volume volume : volumes) {
            Volume clone = prepareClone(volume, repGroupName, cloneSetName);
            cloneList.add(clone.getId());
        }

        // create clone
        waitFor = _blockDeviceController.createListCloneStep(workflow, storageSystem, cloneList, waitFor);
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                addToReplicationGroupMethod(storage, cgURI, repGroupName, cloneList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for adding clone [%s] to group on device [%s]",
                Joiner.on("\t").join(cloneList), storage));

        return waitFor;
    }

    private BlockSnapshot prepareSnapshot(Volume volume, String repGroupName) {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        URI cgUri = volume.getConsistencyGroup();
        if (cgUri != null) {
            snapshot.setConsistencyGroup(cgUri);
        }
        snapshot.setSourceNativeId(volume.getNativeId());
        snapshot.setParent(new NamedURI(volume.getId(), volume.getLabel()));
        snapshot.setReplicationGroupInstance(repGroupName);
        snapshot.setStorageController(volume.getStorageController());
        snapshot.setVirtualArray(volume.getVirtualArray());
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(volume.getProtocol());
        snapshot.setProject(new NamedURI(volume.getProject().getURI(), volume.getProject().getName()));

        String existingSnapSnapSetLabel = ControllerUtils.getSnapSetLabelFromExistingSnaps(repGroupName, volume.getStorageController(), _dbClient);
        if (null == existingSnapSnapSetLabel) {
            log.warn("Not able to find any snapshots with group {}", repGroupName);
            existingSnapSnapSetLabel = repGroupName;
        }

        snapshot.setSnapsetLabel(existingSnapSnapSetLabel);

        String label = null;
        String srcRGName = volume.getReplicationGroupInstance();
        if (NullColumnValueGetter.isNotNullValue(srcRGName)) {
            label = String.format("%s-%s-%s", existingSnapSnapSetLabel, srcRGName, volume.getLabel());
        } else {
            label = String.format("%s-%s", existingSnapSnapSetLabel, volume.getLabel());
        }

        snapshot.setLabel(label);

        snapshot.setTechnologyType(BlockSnapshot.TechnologyType.NATIVE.name());
        _dbClient.createObject(snapshot);

        return snapshot;
    }

    private Volume prepareClone(Volume volume, String repGroupName, String cloneSetName) {
        // create clone for the source
        Volume clone = new Volume();
        clone.setId(URIUtil.createId(Volume.class));
        clone.setLabel(volume.getLabel() + "_" + repGroupName);
        clone.setPool(volume.getPool());
        clone.setStorageController(volume.getStorageController());
        clone.setProject(new NamedURI(volume.getProject().getURI(), clone.getLabel()));
        clone.setTenant(new NamedURI(volume.getTenant().getURI(), clone.getLabel()));
        clone.setVirtualPool(volume.getVirtualPool());
        clone.setVirtualArray(volume.getVirtualArray());
        clone.setProtocol(new StringSet());
        clone.getProtocol().addAll(volume.getProtocol());
        clone.setThinlyProvisioned(volume.getThinlyProvisioned());
        clone.setOpStatus(new OpStatusMap());
        clone.setAssociatedSourceVolume(volume.getId());
        clone.setReplicationGroupInstance(repGroupName);

        // For clones of new volumes added to Application, get the clone set name and set it
        if (cloneSetName != null) {
            clone.setFullCopySetName(cloneSetName);
        }

        StringSet fullCopies = volume.getFullCopies();
        if (fullCopies == null) {
            fullCopies = new StringSet();
            volume.setFullCopies(fullCopies);
        }

        fullCopies.add(clone.getId().toString());
        _dbClient.createObject(clone);
        _dbClient.updateObject(volume);

        return clone;
    }

    /*
     * 1. for each newly created volumes in a CG, create a mirror
     * 2. add all mirrors to an existing replication group
     */
    private String createMirrorSteps(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> volumeDescriptors, List<Volume> volumeList, URI cgURI) {
        log.info("START create mirror steps");
        List<URI> sourceList = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        List<Volume> volumes = new ArrayList<Volume>();
        Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class, sourceList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (volume != null && !volume.getInactive()) {
                volumes.add(volume);
            }
        }

        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

        Set<String> repGroupNames = ControllerUtils.getMirrorReplicationGroupNames(volumeList, _dbClient);
        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        for (String repGroupName : repGroupNames) {
            waitFor = addMirrorToReplicationGroupStep(workflow, waitFor, storageSystem, volumes, repGroupName, cgURI);
        }

        return waitFor;
    }

    private String addMirrorToReplicationGroupStep(final Workflow workflow, String waitFor, StorageSystem storageSystem,
            List<Volume> volumes, String repGroupName, URI cgURI) {
        log.info("START create mirror step");
        URI storage = storageSystem.getId();
        List<URI> mirrorList = new ArrayList<URI>();
        for (Volume volume : volumes) {
            String mirrorLabel = volume.getLabel() + "-" + repGroupName;
            BlockMirror mirror = createMirror(volume, volume.getVirtualPool(), volume.getPool(), mirrorLabel);
            URI mirrorId = mirror.getId();
            mirrorList.add(mirrorId);
        }

        waitFor = _blockDeviceController.createListMirrorStep(workflow, waitFor, storageSystem, mirrorList);
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                addToReplicationGroupMethod(storage, cgURI, repGroupName, mirrorList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for adding mirror [%s] to group on device [%s]",
                Joiner.on("\t").join(mirrorList), storage));

        return waitFor;
    }

    /**
     * Adds a BlockMirror structure for a Volume. It also calls addMirrorToVolume to
     * link the mirror into the volume's mirror set.
     * 
     * @param volume Volume
     * @param vPoolURI
     * @param recommendedPoolURI Pool that should be used to create the mirror
     * @param volumeLabel
     * @return BlockMirror (persisted)
     */
    private BlockMirror createMirror(Volume volume, URI vPoolURI, URI recommendedPoolURI, String volumeLabel) {
        BlockMirror createdMirror = new BlockMirror();
        createdMirror.setSource(new NamedURI(volume.getId(), volume.getLabel()));
        createdMirror.setId(URIUtil.createId(BlockMirror.class));
        URI cgUri = volume.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgUri)) {
            createdMirror.setConsistencyGroup(cgUri);
        }
        createdMirror.setLabel(volumeLabel);
        createdMirror.setStorageController(volume.getStorageController());
        createdMirror.setVirtualArray(volume.getVirtualArray());
        createdMirror.setProtocol(new StringSet());
        createdMirror.getProtocol().addAll(volume.getProtocol());
        createdMirror.setCapacity(volume.getCapacity());
        createdMirror.setProject(new NamedURI(volume.getProject().getURI(), createdMirror.getLabel()));
        createdMirror.setTenant(new NamedURI(volume.getTenant().getURI(), createdMirror.getLabel()));
        createdMirror.setPool(recommendedPoolURI);
        createdMirror.setVirtualPool(vPoolURI);
        createdMirror.setSyncState(SynchronizationState.UNKNOWN.toString());
        createdMirror.setSyncType(BlockMirror.MIRROR_SYNC_TYPE);
        createdMirror.setThinlyProvisioned(volume.getThinlyProvisioned());
        _dbClient.createObject(createdMirror);
        addMirrorToVolume(volume, createdMirror);
        return createdMirror;
    }

    /**
     * Adds a Mirror structure to a Volume's mirror set.
     * 
     * @param volume
     * @param mirror
     */
    private void addMirrorToVolume(Volume volume, BlockMirror mirror) {
        StringSet mirrors = volume.getMirrors();
        if (mirrors == null) {
            mirrors = new StringSet();
        }
        mirrors.add(mirror.getId().toString());
        volume.setMirrors(mirrors);
        // Persist changes
        _dbClient.updateObject(volume);
    }

    public Workflow.Method addToReplicationGroupMethod(URI storage, URI consistencyGroup, String repGroupName,
            List<URI> addVolumesList) {
        return new Workflow.Method("addToReplicationGroup", storage, consistencyGroup, repGroupName, addVolumesList);
    }

    /**
     * Orchestration method for adding members to a replication group.
     * 
     * @param storage
     * @param consistencyGroup
     * @param replicationGroupName
     * @param addVolumesList
     * @param opId
     * @return
     * @throws ControllerException
     */
    public boolean addToReplicationGroup(URI storage, URI consistencyGroup, String replicationGroupName, List<URI> addVolumesList,
            String opId)
            throws ControllerException {
        WorkflowStepCompleter.stepExecuting(opId);
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            _blockDeviceController.getDevice(storageSystem.getSystemType()).doAddToReplicationGroup(
                    storageSystem, consistencyGroup, replicationGroupName, addVolumesList, taskCompleter);
            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private boolean checkIfCGHasCloneReplica(List<Volume> volumes) {
        for (Volume volume : volumes) {
            StringSet fullCopies = volume.getFullCopies();
            if (fullCopies != null && !fullCopies.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfCGHasMirrorReplica(List<Volume> volumes) {
        for (Volume volume : volumes) {
            StringSet mirrors = volume.getMirrors();
            if (mirrors != null && !mirrors.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfCGHasSnapshotReplica(List<Volume> volumes) {
        for (Volume volume : volumes) {
            URIQueryResultList list = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volume.getId()),
                    list);
            Iterator<URI> it = list.iterator();
            while (it.hasNext()) {
                URI snapshotID = it.next();
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);
                if (snapshot != null) {
                    log.debug("There are Snapshot(s) available for volume {}", volume.getId());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets the snap sessions for a CG volume.
     *
     * @param volume the volume
     * @return the snap sessions for cg volume
     */
    private List<BlockSnapshotSession> getSnapSessionsForCGVolume(Volume volume) {
        /**
         * Get all snap sessions for Volume's CG
         * filter the snap sessions which matches the Volume's Replication Group
         */
        List<BlockSnapshotSession> cgVolumeSessions = new ArrayList<BlockSnapshotSession>();
        String rgName = volume.getReplicationGroupInstance();
        if (NullColumnValueGetter.isNotNullValue(rgName)) {
            URI cgURI = volume.getConsistencyGroup();
            List<BlockSnapshotSession> sessionsList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getBlockSnapshotSessionByConsistencyGroup(cgURI));

            for (BlockSnapshotSession session : sessionsList) {
                if (rgName.equals(session.getReplicationGroupInstance())) {
                    cgVolumeSessions.add(session);
                }
            }
        }

        return cgVolumeSessions;
    }

    private String processSnapSessions(List<Volume> existingVolumesInCG, Workflow workflow,
            String waitFor, List<Volume> volumesToAdd) {
        // Get # of existing sessions for RG volumes, and create session for new volumes for every existing session
        Volume existingVolume = existingVolumesInCG.get(0);
        URI system = existingVolume.getStorageController();
        log.info("Processing RG {}", existingVolume.getReplicationGroupInstance());
        List<BlockSnapshotSession> sessions = getSnapSessionsForCGVolume(existingVolumesInCG.get(0));
        for (BlockSnapshotSession session : sessions) {
            log.info("Processing SnapSession {} for RG {}", session.getSessionLabel(), session.getReplicationGroupInstance());
            waitFor = createSnapshotSessionsStep(workflow, waitFor, system,
                    volumesToAdd, existingVolume.getReplicationGroupInstance(), session);
        }
        return waitFor;
    }

    private String processSnapSessionsAndLinkedTargets(List<Volume> existingVolumesInCG, Workflow workflow,
            String waitFor, List<Volume> volumesToAdd, URI cgUri) {
        /**
         * Get # of existing sessions for RG volumes
         * for every existing session:
         * -get # of existing snapshot groups
         * -for every new volume:
         * --create new session
         * --For every new session, create new linked targets as many as existing snap groups
         */
        Volume existingVolume = existingVolumesInCG.get(0);
        log.info("Processing RG {}", existingVolume.getReplicationGroupInstance());
        URI system = existingVolume.getStorageController();
        List<BlockSnapshotSession> sessions = getSnapSessionsForCGVolume(existingVolume);
        for (BlockSnapshotSession session : sessions) {
            log.info("Processing SnapSession {} for RG {}", session.getSessionLabel(), session.getReplicationGroupInstance());
            waitFor = createSnapshotSessionAndLinkSessionStep(workflow, waitFor, system, existingVolumesInCG,
                    volumesToAdd, session, cgUri);
        }
        return waitFor;
    }


    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // Get the list of descriptors which represent source volumes to be deleted
        List<VolumeDescriptor> volumeDescriptors = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA, VolumeDescriptor.Type.SRDF_SOURCE,
                        VolumeDescriptor.Type.SRDF_EXISTING_SOURCE,
                        VolumeDescriptor.Type.SRDF_TARGET }, null);

        // If no source volumes, just return
        if (volumeDescriptors.isEmpty()) {
            log.info("No replica steps required");
            return waitFor;
        }

        // Get the consistency group. If no consistency group for source
        // volumes,
        // just return. Get CG from any descriptor.
        final VolumeDescriptor firstVolumeDescriptor = volumeDescriptors.get(0);
        if (firstVolumeDescriptor != null) {
            Volume volume = _dbClient.queryObject(Volume.class, firstVolumeDescriptor.getVolumeURI());
            if (!(volume != null && volume.isInCG() &&
                    (ControllerUtils.isVmaxVolumeUsing803SMIS(volume, _dbClient) || ControllerUtils.isNotInRealVNXRG(volume, _dbClient)))) {
                return waitFor;
            }
        }

        // Sort the volumes by its system, and replicationGroup
        Map<String, Set<URI>> rgVolsMap = new HashMap<String, Set<URI>>();
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            URI volumeURI = volumeDescriptor.getVolumeURI();
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            if (volume != null) {
                String replicationGroup = volume.getReplicationGroupInstance(); 
                if (NullColumnValueGetter.isNotNullValue(replicationGroup)) {
                    URI storage = volume.getStorageController();
                    String key = storage.toString() + replicationGroup;
                    Set<URI> rgVolumeList = rgVolsMap.get(key);
                    if (rgVolumeList == null) {
                        rgVolumeList = new HashSet<URI>();
                        rgVolsMap.put(key, rgVolumeList);
                    }
                    rgVolumeList.add(volumeURI);
                }
            }
        }

        if (rgVolsMap.isEmpty()) {
            return waitFor;
        }

        for (Set<URI> volumeURIs : rgVolsMap.values()) {
            // find member volumes in the group
            List<Volume> volumeList = new ArrayList<Volume>();
            Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class, volumeURIs);
            while (volumeIterator.hasNext()) {
                Volume volume = volumeIterator.next();
                if (volume != null && !volume.getInactive()) {
                    volumeList.add(volume);
                }
            }

            Volume firstVol = volumeList.get(0);
            String rpName = firstVol.getReplicationGroupInstance();
            URI storage = firstVol.getStorageController();

            boolean isRemoveAllFromRG = ControllerUtils.replicationGroupHasNoOtherVolume(_dbClient, rpName, volumeURIs, storage);
            log.info("isRemoveAllFromRG {}", isRemoveAllFromRG);
            if (checkIfCGHasCloneReplica(volumeList)) {
                log.info("Adding clone steps for deleting volumes");
                waitFor = detachCloneSteps(workflow, waitFor, volumeURIs, volumeList, isRemoveAllFromRG);
            }

            if (checkIfCGHasMirrorReplica(volumeList)) {
                log.info("Adding mirror steps for deleting volumes");
                // delete mirrors for the to be deleted volumes
                waitFor = deleteMirrorSteps(workflow, waitFor, volumeURIs, volumeList, isRemoveAllFromRG);
            }

            if (checkIfCGHasSnapshotReplica(volumeList)) {
                log.info("Adding snapshot steps for deleting volumes");
                // delete snapshots for the to be deleted volumes
                waitFor = deleteSnapshotSteps(workflow, waitFor, volumeURIs, volumeList, isRemoveAllFromRG);
            }
        }

        return waitFor;
    }

    /**
     * Remove all snapshots from the volumes to be deleted.
     * 
     * @param workflow
     * @param waitFor
     * @param volumeURIs
     * @param volumes
     * @return
     */
    private String deleteSnapshotSteps(Workflow workflow, String waitFor, Set<URI> volumeURIs, List<Volume> volumes, boolean isRemoveAll) {
        log.info("START delete snapshot steps");
        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

        Set<String> repGroupNames = ControllerUtils.getSnapshotReplicationGroupNames(volumes, _dbClient);
        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        for (String repGroupName : repGroupNames) {
            List<URI> snapList = getSnapshotsToBeRemoved(volumeURIs, repGroupName);
            if (!isRemoveAll) {
                URI cgURI = volumes.get(0).getConsistencyGroup();
                waitFor = removeSnapshotsFromReplicationGroupStep(workflow, waitFor, storageSystem, cgURI, snapList, repGroupName);
            }
            log.info("Adding delete snapshot steps");
            waitFor = _blockDeviceController.deleteSnapshotStep(workflow, waitFor, storage, storageSystem, snapList, isRemoveAll);
        }

        return waitFor;
    }

    /*
     * Detach all clones of the to be deleted volumes in a CG
     */
    private String
            detachCloneSteps(final Workflow workflow, String waitFor, Set<URI> volumeURIs, List<Volume> volumes, boolean isRemoveAll) {
        log.info("START detach clone steps");
        Set<String> repGroupNames = ControllerUtils.getCloneReplicationGroupNames(volumes, _dbClient);
        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
        for (String repGroupName : repGroupNames) {
            List<URI> cloneList = getClonesToBeRemoved(volumeURIs, repGroupName);
            if (!isRemoveAll) {
                URI cgURI = volumes.get(0).getConsistencyGroup();
                waitFor = removeClonesFromReplicationGroupStep(workflow, waitFor, storageSystem, cgURI, cloneList, repGroupName);
            }

            waitFor = _blockDeviceController.detachCloneStep(workflow, waitFor, storageSystem, cloneList, isRemoveAll);
        }

        return waitFor;
    }

    /*
     * Delete all mirrors of the to be deleted volumes in a CG
     */
    private String deleteMirrorSteps(final Workflow workflow, String waitFor,
            Set<URI> volumeURIs, List<Volume> volumes, boolean isRemoveAll) {
        log.info("START delete mirror steps");
        Set<String> repGroupNames = ControllerUtils.getMirrorReplicationGroupNames(volumes, _dbClient);

        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        List<URI> mirrorList = new ArrayList<>();
        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
        for (String repGroupName : repGroupNames) {
            mirrorList = getMirrorsToBeRemoved(volumeURIs, repGroupName);
            if (!isRemoveAll) {
                URI cgURI = volumes.get(0).getConsistencyGroup();
                waitFor = removeMirrorsFromReplicationGroupStep(workflow, waitFor, storageSystem, cgURI, mirrorList, repGroupName);
            }

            waitFor = _blockDeviceController.deleteListMirrorStep(workflow, waitFor, storage, storageSystem, mirrorList, isRemoveAll);
        }

        return waitFor;
    }

    private String removeSnapshotsFromReplicationGroupStep(final Workflow workflow, String waitFor,
            StorageSystem storageSystem,
            URI cgURI, List<URI> snapshots, String repGroupName) {
        log.info("START remove snapshot from CG steps");
        URI storage = storageSystem.getId();
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                removeFromReplicationGroupMethod(storage, cgURI, repGroupName, snapshots),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for remove snapshots [%s] to group on device [%s]",
                Joiner.on("\t").join(snapshots), storage));

        return waitFor;
    }

    private String removeClonesFromReplicationGroupStep(final Workflow workflow, String waitFor, StorageSystem storageSystem,
            URI cgURI, List<URI> cloneList, String repGroupName) {
        log.info("START remove clone from CG steps");
        URI storage = storageSystem.getId();
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                removeFromReplicationGroupMethod(storage, cgURI, repGroupName, cloneList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for remove clones [%s] from group on device [%s]",
                Joiner.on("\t").join(cloneList), storage));

        return waitFor;
    }

    private String removeMirrorsFromReplicationGroupStep(final Workflow workflow, String waitFor, StorageSystem storageSystem,
            URI cgURI, List<URI> mirrorList, String repGroupName) {
        log.info("START remove mirror from CG steps");
        URI storage = storageSystem.getId();
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                removeFromReplicationGroupMethod(storage, cgURI, repGroupName, mirrorList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for remove mirrors [%s] from group on device [%s]",
                Joiner.on("\t").join(mirrorList), storage));

        return waitFor;
    }

    private List<URI> getSnapshotsToBeRemoved(Set<URI> volumes, String repGroupName) {
        List<URI> replicas = new ArrayList<>();
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getSnapshotReplicationGroupInstanceConstraint(repGroupName), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, resultsIter.next());
            if (volumes.contains(snapshot.getParent().getURI())) {
                replicas.add(snapshot.getId());
            }
        }

        return replicas;
    }

    private List<URI> getClonesToBeRemoved(Set<URI> volumes, String repGroupName) {
        List<URI> replicas = new ArrayList<URI>();
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeReplicationGroupInstanceConstraint(repGroupName), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            Volume clone = _dbClient.queryObject(Volume.class, resultsIter.next());
            if (volumes.contains(clone.getAssociatedSourceVolume())) {
                replicas.add(clone.getId());
            }
        }

        return replicas;
    }

    private List<URI> getMirrorsToBeRemoved(Set<URI> volumes, String repGroupName) {
        List<URI> replicas = new ArrayList<URI>();
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getMirrorReplicationGroupInstanceConstraint(repGroupName), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, resultsIter.next());
            if (volumes.contains(mirror.getSource().getURI())) {
                replicas.add(mirror.getId());
            }
        }

        return replicas;
    }

    static Workflow.Method removeFromReplicationGroupMethod(URI storage, URI consistencyGroup, String repGroupName,
            List<URI> addVolumesList) {
        return new Workflow.Method("removeFromReplicationGroup", storage, consistencyGroup, repGroupName, addVolumesList);
    }

    /**
     * Orchestration method for removing members from a replication group.
     * 
     * @param storage
     * @param consistencyGroup
     * @param repGroupName
     * @param addVolumesList
     * @param opId
     * @return
     * @throws ControllerException
     */
    public boolean removeFromReplicationGroup(URI storage, URI consistencyGroup, String repGroupName, List<URI> addVolumesList,
            String opId)
            throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            _blockDeviceController.getDevice(storageSystem.getSystemType()).doRemoveFromReplicationGroup(
                    storageSystem, consistencyGroup, repGroupName, addVolumesList, taskCompleter);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId, VolumeWorkflowCompleter completer) {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors,
            String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor, URI storage, URI pool, URI volume, URI snapshot,
            Boolean updateOpStatus, String syncDirection, String taskId, BlockSnapshotRestoreCompleter completer) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }
    
    /**
     * Add steps to create clones/snapshots when add volumes to a replication group.
     * @param workflow - The workflow that the steps would be added to
     * @param waitFor -  a waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @param cgURI - CG URI
     * @param volumeListToAdd -  The volumes to be added.
     * @param replicationGroup - replication group name
     * @param taskId - top level operation's taskId
     * @return - a waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForAddingVolumesToRG(Workflow workflow, String waitFor, URI cgURI, List<URI> volumeListToAdd,
            String replicationGroup, String taskId) throws InternalException {
        log.info(String.format("addStepsForAddingVolumesToRG %s", replicationGroup));
        List<Volume> volumesToAdd = ControllerUtils.queryVolumesByIterativeQuery(_dbClient, volumeListToAdd);

        if (!volumesToAdd.isEmpty()) {
            Volume firstVolume = volumesToAdd.get(0);
            if (!ControllerUtils.isVmaxVolumeUsing803SMIS(firstVolume, _dbClient) &&
                    !ControllerUtils.isVnxVolume(firstVolume, _dbClient)) {
                return waitFor;
            }

            URI storage = firstVolume.getStorageController();
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            // find member volumes in the group
            List<Volume> existingRGVolumes = ControllerUtils.getVolumesPartOfRG(storage, replicationGroup, _dbClient);
            if (checkIfCGHasCloneReplica(existingRGVolumes)) {
                log.info("Adding clone steps for adding volumes");
                // create new clones for the newly added volumes
                // add the created clones to clone groups
                Set<String> repGroupNames = ControllerUtils.getCloneReplicationGroupNames(existingRGVolumes, _dbClient);
                for (String repGroupName : repGroupNames) {
                    waitFor = addClonesToReplicationGroupStep(workflow, waitFor, storageSystem, volumesToAdd, repGroupName, cgURI);
                }
            }

            if (checkIfCGHasMirrorReplica(existingRGVolumes)) {
                log.info("Adding mirror steps for adding volumes");
                // create new mirrors for the newly added volumes
                // add the created mirrors to mirror groups
                Set<String> repGroupNames = ControllerUtils.getMirrorReplicationGroupNames(existingRGVolumes, _dbClient);
                for (String repGroupName : repGroupNames) {
                    waitFor = addMirrorToReplicationGroupStep(workflow, waitFor, storageSystem, volumesToAdd, repGroupName, cgURI);
                }
            }
            
            List<BlockSnapshotSession> sessions = getSnapSessionsForCGVolume(existingRGVolumes.get(0));
            boolean isExistingCGSnapShotAvailable = checkIfCGHasSnapshotReplica(existingRGVolumes);
            boolean isExistingCGSnapSessionAvailable = sessions != null && !sessions.isEmpty();
            boolean isVMAX3ExistingVolume = ControllerUtils.isVmaxVolumeUsing803SMIS(existingRGVolumes.get(0), _dbClient);

            if (isVMAX3ExistingVolume) {
                if (isVMAX3VolumeHasSessionOnly(isExistingCGSnapSessionAvailable, isExistingCGSnapShotAvailable)) {
                    log.info("Existing CG only has Snap Session, adding snap session steps for adding volumes");
                    processSnapSessions(existingRGVolumes, workflow, waitFor, volumesToAdd);
                } else if (isVMAX3VolumeHasSnapshotOnly(isExistingCGSnapSessionAvailable, isExistingCGSnapShotAvailable)) {
                    // create new snapshots for the newly added volumes
                    // add the created snapshots to snapshot groups
                    Set<String> snapGroupNames = ControllerUtils.getSnapshotReplicationGroupNames(existingRGVolumes,
                            _dbClient);
                    for (String snapGroupName : snapGroupNames) {
                        // This method is invoked per RG, so need to get storage system separately
                        log.info("Existing CG only has Snapshots, adding snapshot steps for existing snap group {} adding volumes",
                                snapGroupName);
                        waitFor = addSnapshotsToReplicationGroupStep(workflow, waitFor, storageSystem, volumesToAdd,
                                snapGroupName, cgURI);
                    }
                } else if (isVMAX3VolumeHasSessionAndSnapshot(isExistingCGSnapSessionAvailable, isExistingCGSnapShotAvailable)) {
                    log.info("Existing CG has both Sessions and linked targets, adding snapshot and session steps");
                    processSnapSessionsAndLinkedTargets(existingRGVolumes, workflow, waitFor, volumesToAdd, cgURI);
                }

            } else if (isExistingCGSnapShotAvailable) {
                // non VMAX3 volume
                log.info("Adding snapshot steps for adding volumes");
                // create new snapshots for the newly added volumes
                // add the created snapshots to snapshot groups
                Set<String> snapGroupNames = ControllerUtils.getSnapshotReplicationGroupNames(existingRGVolumes, _dbClient);
                for (String snapGroupName : snapGroupNames) {
                    waitFor = addSnapshotsToReplicationGroupStep(workflow, waitFor, storageSystem, volumesToAdd,
                            snapGroupName, cgURI);
                }
            }
        }

        return waitFor;
    }
    
    

    /**
     * adding new snap sessions
     * 
     * @param workflow
     * @param waitFor
     * @param cgURI
     * @param volumeList
     * @param taskId
     * @return
     * @throws InternalException
     */
    public String addStepsForAddingSessionsToCG(Workflow workflow, String waitFor, URI cgURI, List<URI> volumeListToAdd,
            String replicationGroup, String taskId) throws InternalException {
        log.info("addStepsForAddingVolumesToCG {}", cgURI);
        List<Volume> volumes = ControllerUtils.queryVolumesByIterativeQuery(_dbClient, volumeListToAdd);
        Volume firstVolume = volumes.get(0);
        if (!ControllerUtils.isVmaxVolumeUsing803SMIS(firstVolume, _dbClient)) {
            return waitFor;
        }

        URI storage = firstVolume.getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

        if (checkIfCGHasSnapshotSessions(volumes)) {
            log.info("Adding snapshot session steps for adding volumes");
            // Consolidate multiple snapshot sessions into one CG-based snapshot
            // session
            waitFor = addSnapshotSessionsToReplicationGroupStep(workflow, waitFor, storageSystem, volumes, cgURI);
        }

        return waitFor;

    }

    private boolean checkIfCGHasSnapshotSessions(List<Volume> volumes) {
        for (BlockObject volume : volumes) {
            List<BlockSnapshotSession> sessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(volume.getId()));
            if (!sessions.isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isVMAX3VolumeHasSessionOnly(boolean isExistingCGSnapSessionAvailable, boolean isExistingCGSnapShotAvailable) {
        if (!isExistingCGSnapSessionAvailable)
            return false;
        if (isExistingCGSnapShotAvailable)
            return false;
        return true;
    }

    private boolean isVMAX3VolumeHasSnapshotOnly(boolean isExistingCGSnapSessionAvailable, boolean isExistingCGSnapShotAvailable) {
        if (!isExistingCGSnapShotAvailable)
            return false;
        if (isExistingCGSnapSessionAvailable)
            return false;
        return true;
    }

    private boolean isVMAX3VolumeHasSessionAndSnapshot(boolean isExistingCGSnapSessionAvailable, boolean isExistingCGSnapShotAvailable) {
        return isExistingCGSnapSessionAvailable && isExistingCGSnapShotAvailable;
    }

    /**
     * Adds the steps necessary for removing one or more volumes from replication groups to the given Workflow.
     * volume list could contain volumes from different storage systems and different replication groups
     *
     * @param workflow - a Workflow
     * @param waitFor - a String key that should be used in the Workflow.createStep
     *            waitFor parameter in order to wait on the previous controller's actions to complete.
     * @param cgURI - URI list of consistency group
     * @param volumeList - URI list of volumes
     * @param taskId - top level operation's taskId
     * @return - a waitFor key that can be used by subsequent controllers to wait on
     *         the Steps created by this controller.
     * @throws InternalException
     */
    public String addStepsForRemovingVolumesFromCG(Workflow workflow, String waitFor, URI cgURI, List<URI> volumeList,
            String taskId) throws InternalException {
        
        Map<URI, List<URI>> storageToVolMap = new HashMap<URI, List<URI>>();
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeList);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            URI system = volume.getStorageController();
            if (storageToVolMap.get(system) == null) {
                storageToVolMap.put(system, new ArrayList<URI>());
            }
            storageToVolMap.get(system).add(volume.getId());
        }
        
        for (Entry<URI, List<URI>> entry : storageToVolMap.entrySet()) {
            waitFor = addStepsForRemovingVolumesFromCG(workflow, waitFor, cgURI, entry.getKey(), entry.getValue(), taskId);
        }
        
        return waitFor;
        
    }

    /**
     * add steps to remove replicas from storage groups for a single storage system; could be multiple replication groups
     * @param workflow
     * @param waitFor
     * @param cgURI
     * @param storage
     * @param volumeList
     * @param taskId
     * @return
     * @throws InternalException
     */
    private String addStepsForRemovingVolumesFromCG(Workflow workflow, String waitFor, URI cgURI, URI storage, List<URI> volumeList,
            String taskId) throws InternalException {
        
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeList);
        Map<String, List<URI>> groupMap = new HashMap<String, List<URI>>();
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            String groupName = volume.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(groupName)) {
                if (groupMap.get(groupName) == null) {
                    groupMap.put(groupName, new ArrayList<URI>());
                }
                groupMap.get(groupName).add(volume.getId());
            }
        }
        
        for (Entry<String, List<URI>> entry : groupMap.entrySet()) {
            waitFor = addStepsForRemovingVolumesFromCG(workflow, waitFor, cgURI, storage, entry.getKey(), entry.getValue(), taskId);
        }
        
        return waitFor;
    }

    /**
     * add steps to remove replicas from storage group for a single storage system and a single replication group
     * @param workflow
     * @param waitFor
     * @param cgURI
     * @param storage
     * @param groupName
     * @param volumeList
     * @param taskId
     * @return
     * @throws InternalException
     */
    private String addStepsForRemovingVolumesFromCG(Workflow workflow, String waitFor, URI cgURI, URI storage, String groupName, List<URI> volumeList,
            String taskId) throws InternalException {
        log.info("addStepsForRemovingVolumesFromCG {}", cgURI);
        List<Volume> volumes = new ArrayList<Volume>();
        Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class, volumeList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (volume != null && !volume.getInactive()) {
                volumes.add(volume);
            }
        }
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);

        if (!volumes.isEmpty()) {
            Volume firstVolume = volumes.get(0);
            if (!(firstVolume.isInCG() && ControllerUtils.isVmaxVolumeUsing803SMIS(firstVolume, _dbClient)) &&
                  !ControllerUtils.isNotInRealVNXRG(firstVolume, _dbClient)) {
                log.info(String.format("Remove from replication group not supported for volume %s", firstVolume.getLabel()));
                return waitFor;
            }

            boolean isRemoveAllFromCG = ControllerUtils.cgHasNoOtherVolume(_dbClient, cgURI, volumes);
            log.info("isRemoveAllFromCG {}", isRemoveAllFromCG);
            if (checkIfCGHasCloneReplica(volumes)) {
                log.info("Adding steps to process clones for removing volumes");
                // get clone volumes
                Map<String, List<URI>> cloneGroupCloneURIMap = new HashMap<String, List<URI>>();
                for (Volume volume : volumes) {
                    if (volume.getFullCopies() != null && !volume.getFullCopies().isEmpty()) {
                        for (String cloneUri : volume.getFullCopies()) {
                            Volume clone = _dbClient.queryObject(Volume.class, URI.create(cloneUri));
                            if (clone != null && !clone.getInactive() && NullColumnValueGetter.isNotNullValue(clone.getReplicationGroupInstance())) {
                                if (cloneGroupCloneURIMap.get(clone.getReplicationGroupInstance()) == null) {
                                    cloneGroupCloneURIMap.put(clone.getReplicationGroupInstance(), new ArrayList<URI>());
                                }
                                cloneGroupCloneURIMap.get(clone.getReplicationGroupInstance()).add(clone.getId());
                            }
                        }
                    }
                }
                // add steps to remove clones from the replication group
                for (Entry<String, List<URI>> entry : cloneGroupCloneURIMap.entrySet()) {
                    waitFor = removeClonesFromReplicationGroupStep(workflow, waitFor, system, cgURI, entry.getValue(), entry.getKey());
                }
            }

            if (checkIfCGHasMirrorReplica(volumes)) {
                log.info("Adding steps to process mirrors for removing volumes");
                Map<String, List<URI>> mirrorGroupCloneURIMap = new HashMap<String, List<URI>>();
                for (Volume volume : volumes) {
                    StringSet mirrors = volume.getMirrors();
                    if (mirrors != null && !mirrors.isEmpty()) {
                        for (String mirrorUri : mirrors) {
                            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, URI.create(mirrorUri));
                            if (mirror != null && !mirror.getInactive() && NullColumnValueGetter.isNotNullValue(mirror.getReplicationGroupInstance())) {
                                if (mirrorGroupCloneURIMap.get(mirror.getReplicationGroupInstance()) == null) {
                                    mirrorGroupCloneURIMap.put(mirror.getReplicationGroupInstance(), new ArrayList<URI>());
                                }
                                mirrorGroupCloneURIMap.get(mirror.getReplicationGroupInstance()).add(mirror.getId());
                            }
                        }
                    }
                }
                // add steps to remove mirrors from replication group
                for (Entry<String, List<URI>> entry : mirrorGroupCloneURIMap.entrySet()) {
                    waitFor = removeMirrorsFromReplicationGroupStep(workflow, waitFor, system, cgURI, entry.getValue(), entry.getKey());
                }
            }

            if (checkIfCGHasSnapshotReplica(volumes)) {
                log.info("Adding steps to process snapshots for removing volumes");
                Map<String, List<URI>> snapGroupCloneURIMap = new HashMap<String, List<URI>>();
                for (Volume volume : volumes) {
                    URIQueryResultList list = new URIQueryResultList();
                    _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volume.getId()),
                            list);
                    Iterator<URI> it = list.iterator();
                    while (it.hasNext()) {
                        BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, it.next());
                        String snapGroupName = null;
                        if (NullColumnValueGetter.isNotNullValue(snapshot.getReplicationGroupInstance())) {
                            snapGroupName = snapshot.getReplicationGroupInstance();
                        } else if (NullColumnValueGetter.isNotNullValue(snapshot.getSnapsetLabel())) {
                            snapGroupName = snapshot.getSnapsetLabel();
                        }

                        if (snapGroupName != null) {
                            if (snapGroupCloneURIMap.get(snapGroupName) == null) {
                                snapGroupCloneURIMap.put(snapGroupName, new ArrayList<URI>());
                            }
                            snapGroupCloneURIMap.get(snapGroupName).add(snapshot.getId());
                        }
                    }
                }

                // add steps to removed snapshots from the replication group
                for (Entry<String, List<URI>> entry : snapGroupCloneURIMap.entrySet()) {
                    waitFor = removeSnapshotsFromReplicationGroupStep(workflow, waitFor, system, cgURI, entry.getValue(), entry.getKey());
                }
            }
        }

        return waitFor;
    }
}
