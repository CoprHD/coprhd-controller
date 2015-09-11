/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

public class ReplicaDeviceController implements BlockOrchestrationInterface {
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
        final VolumeDescriptor firstVolume = volumeDescriptors.get(0);
        if (firstVolume == null || NullColumnValueGetter.isNullURI(firstVolume.getConsistencyGroupURI())) {
            return waitFor;
        }

        // find member volumes in the group
        List<Volume> volumeList = ControllerUtils.getVolumesPartOfCG(firstVolume.getConsistencyGroupURI(), _dbClient);
        if (checkIfCGHasCloneReplica(volumeList)) {
            log.info("Adding clone steps for create volumes");
            // create new clones for the newly created volumes
            // add the created clones to clone groups
            waitFor = createCloneSteps(workflow, waitFor, volumeDescriptors, volumeList);
        }

        if (checkIfCGHasMirrorReplica(volumeList)) {
            log.info("Adding mirror steps for create volumes");
            // create new mirrors for the newly created volumes
            // add the created mirrors to mirror groups
            // TODO
        }

        if (checkIfCGHasSnapshotReplica(volumeList)) {
            log.info("Adding snapshot steps for create volumes");
            // create new snapshots for the newly created volumes
            // add the created snapshots to snapshot groups
            // TODO
        }

        return waitFor;
    }

    /*
     * 1. for each newly created volumes in a CG, create a clone
     * 2. add all clones to an existing replication group
     */
    private String createCloneSteps(final Workflow workflow, String waitFor,
            final List<VolumeDescriptor> volumeDescriptors, List<Volume> volumeList) {
        log.info("START create clone steps");
        List<URI> sourceList = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        List<Volume> volumes = _dbClient.queryObject(Volume.class, sourceList);
        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

        Set<String> repGroupNames = ControllerUtils.getCloneReplicationGroupNames(volumeList, _dbClient);
        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        for (String repGroupName : repGroupNames) {
            waitFor = addClonesToReplicationGroupStep(workflow, waitFor, storageSystem, volumes, repGroupName);
        }

        return waitFor;
    }

    private String addClonesToReplicationGroupStep(final Workflow workflow, String waitFor, StorageSystem storageSystem,
            List<Volume> volumes, String replicationGroupName) {
        log.info("START create clone step");
        URI storage = storageSystem.getId();
        List<URI> cloneList = new ArrayList<URI>();
        for (Volume volume : volumes) {
            // create clone for the source
            Volume clone = new Volume();
            URI uri = URIUtil.createId(Volume.class);
            clone.setId(uri);
            clone.setPool(volume.getPool());
            clone.setStorageController(storage);
            // TODO - set other necessary properties
            clone.setAssociatedSourceVolume(volume.getId());

            StringSet fullCopies = volume.getFullCopies();
            if (fullCopies == null) {
                fullCopies = new StringSet();
            }

            volume.setFullCopies(fullCopies);
            _dbClient.persistObject(clone);
            _dbClient.persistObject(volume);
            cloneList.add(uri);

            waitFor = _blockDeviceController.createSingleCloneStep(workflow, storage, storageSystem, volume, uri, waitFor);
        }

        URI cgURI = volumes.get(0).getConsistencyGroup();
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                addToReplicationGroupMethod(storage, cgURI, replicationGroupName, cloneList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for adding clone [%s] to group on device [%s]",
                Joiner.on("\t").join(cloneList), storage));

        return waitFor;
    }

    static Workflow.Method addToReplicationGroupMethod(URI storage, URI consistencyGroup, String replicationGroupName,
            List<URI> addVolumesList) {
        return new Workflow.Method("addToReplicationGroup", storage, consistencyGroup, replicationGroupName, addVolumesList);
    }

    public boolean addToReplicationGroup(URI storage, URI consistencyGroup, String replicationGroupName, List<URI> addVolumesList,
            String opId)
                    throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            // TODO - define new completer
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            _blockDeviceController.getDevice(storageSystem.getSystemType()).doAddToReplicationGroup(
                    storageSystem, consistencyGroup, replicationGroupName, addVolumesList, taskCompleter);
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

    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // Get the list of descriptors which represent source volumes that have
        // just been created and added to CG possibly
        List<VolumeDescriptor> volumeDescriptors = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA, VolumeDescriptor.Type.SRDF_SOURCE,
                        VolumeDescriptor.Type.SRDF_EXISTING_SOURCE,
                        VolumeDescriptor.Type.SRDF_TARGET }, null);

        // If no source volumes, just return
        if (volumeDescriptors.isEmpty()) {
            log.info("No replica steps required");
            return waitFor;
        }

        // Get the consistency groups. If no consistency group for source
        // volumes,
        // just return. Get CGs from all descriptors.
        Map<URI, Set<URI>> cgToVolumes = new HashMap<URI, Set<URI>>();
        for (VolumeDescriptor volume : volumeDescriptors) {
            URI cg = volume.getConsistencyGroupURI();
            if (NullColumnValueGetter.isNullURI(cg)) {
                Set<URI> volumeList = cgToVolumes.get(cg);
                if (volumeList == null) {
                    volumeList = new HashSet<URI>();
                    cgToVolumes.put(cg, volumeList);
                }

                volumeList.add(volume.getVolumeURI());
            }
        }

        if (cgToVolumes.isEmpty()) {
            return waitFor;
        }

        Set<Entry<URI, Set<URI>>> entrySet = cgToVolumes.entrySet();
        for (Entry<URI, Set<URI>> entry : entrySet) {
            // find member volumes in the group
            Set<URI> volumeURIs = entry.getValue();
            List<Volume> volumeList = _dbClient.queryObject(Volume.class, volumeURIs);
            if (checkIfCGHasCloneReplica(volumeList)) {
                log.info("Adding clone steps for deleting volumes");
                // delete clones for the to be deleted volumes

                // check is all volumes in CG get deleted or only subset of it
                // if all, use alternative logic - detach then delete
                waitFor = deleteCloneSteps(workflow, waitFor, volumeURIs, volumeList);
            }

            if (checkIfCGHasMirrorReplica(volumeList)) {
                log.info("Adding mirror steps for deleting volumes");
                // delete mirrors for the to be deleted volumes
                // TODO
            }

            if (checkIfCGHasSnapshotReplica(volumeList)) {
                log.info("Adding snapshot steps for deleting volumes");
                // delete snapshots for the to be deleted volumes
                // TODO
            }
        }

        return waitFor;
    }

    /*
     * remove all clones of the to be deleted volumes in a CG
     */
    private String deleteCloneSteps(final Workflow workflow, String waitFor, Set<URI> volumeURIs, List<Volume> volumes) {
        log.info("START delete clone steps");
        URI storage = volumes.get(0).getStorageController();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);

        Set<String> repGroupNames = ControllerUtils.getCloneReplicationGroupNames(volumes, _dbClient);
        if (repGroupNames.isEmpty()) {
            return waitFor;
        }

        for (String repGroupName : repGroupNames) {
            waitFor = removeClonesFromReplicationGroupStep(workflow, waitFor, storageSystem, volumeURIs, volumes, repGroupName);
        }

        return waitFor;
    }

    private String removeClonesFromReplicationGroupStep(final Workflow workflow, String waitFor, StorageSystem storageSystem,
            Set<URI> volumeURIs, List<Volume> volumes, String repGroupName) {
        log.info("START remove clone steps");
        URI storage = storageSystem.getId();
        List<URI> cloneList = getClonesToBeRemoved(volumeURIs, repGroupName);
        URI cgURI = volumes.get(0).getConsistencyGroup();
        waitFor = workflow.createStep(BlockDeviceController.UPDATE_CONSISTENCY_GROUP_STEP_GROUP,
                String.format("Updating consistency group  %s", cgURI), waitFor, storage,
                _blockDeviceController.getDeviceType(storage), this.getClass(),
                removeFromReplicationGroupMethod(storage, cgURI, repGroupName, cloneList),
                _blockDeviceController.rollbackMethodNullMethod(), null);
        log.info(String.format("Step created for adding clone [%s] to group on device [%s]",
                Joiner.on("\t").join(cloneList), storage));

        return waitFor;
    }

    private List<URI> getClonesToBeRemoved(Set<URI> volumes, String replicationGroupName) {
        List<URI> replicas = new ArrayList<URI>();
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getMirrorReplicationGroupInstanceConstraint(replicationGroupName), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            Volume clone = _dbClient.queryObject(Volume.class, resultsIter.next());
            if (volumes.contains(clone.getAssociatedSourceVolume())) {
                replicas.add(clone.getId());
            }
        }

        return replicas;
    }

    static Workflow.Method removeFromReplicationGroupMethod(URI storage, URI consistencyGroup, String replicationGroupName,
            List<URI> addVolumesList) {
        return new Workflow.Method("removeFromReplicationGroup", storage, consistencyGroup, replicationGroupName, addVolumesList);
    }

    public boolean removeFromReplicationGroup(URI storage, URI consistencyGroup, String replicationGroupName, List<URI> addVolumesList,
            String opId)
                    throws ControllerException {
        TaskCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            // TODO - define new completer
            taskCompleter = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, opId);
            _blockDeviceController.getDevice(storageSystem.getSystemType()).doRemoveFromReplicationGroup(
                    storageSystem, consistencyGroup, replicationGroupName, addVolumesList, taskCompleter);
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors,
            String taskId) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes,
            String taskId) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor, URI storage, URI pool, URI volume, URI snapshot,
            Boolean updateOpStatus, String taskId, BlockSnapshotRestoreCompleter completer) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }
}
