/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remotereplicationcontroller;


import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType;
import com.emc.storageos.storagedriver.storagecapabilities.RemoteReplicationAttributes;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDevice;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationChangeModeCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationEstablishCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationFailbackCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationFailoverCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationGroupCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationMovePairCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationPairCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationResumeCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationSplitCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationStopCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationSuspendCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationSwapCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RemoteReplicationDeviceController implements RemoteReplicationController, BlockOrchestrationInterface {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationDeviceController.class);

    private WorkflowService workflowService;
    private DbClient dbClient;
    private RemoteReplicationDevice remoteReplicationdevice;

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

    public RemoteReplicationDevice getRemoteReplicationDevice() {
        return remoteReplicationdevice;
    }

    public void setRemoteReplicationDevice(RemoteReplicationDevice device) {
        this.remoteReplicationdevice = device;
    }


    @Override
    public void createRemoteReplicationGroup(URI replicationGroup, List<URI> sourcePorts, List<URI> targetPorts, String opId) {
        RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, replicationGroup);
        _log.info("Create remote replication group: {} : {}", rrGroup.getLabel(), replicationGroup);

        List<URI> elementURIs = new ArrayList<>();
        elementURIs.add(rrGroup.getSourceSystem());
        elementURIs.add(rrGroup.getTargetSystem());
        RemoteReplicationGroupCompleter taskCompleter = new RemoteReplicationGroupCompleter(replicationGroup, opId);

        // call device
        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.createRemoteReplicationGroup(replicationGroup, sourcePorts, targetPorts, taskCompleter);
    }

    @Override
    public void deleteReplicationPairs(List<URI> replicationPairs, String opId) {
        _log.info("Delete remote replication pairs: {}", replicationPairs);

        List<URI> volumeURIs = null;
        try {
            volumeURIs = RemoteReplicationUtils.getElements(dbClient, replicationPairs);
            RemoteReplicationPairCompleter taskCompleter = new RemoteReplicationPairCompleter(volumeURIs, opId);

            RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
            WorkflowStepCompleter.stepExecuting(opId);
            rrDevice.deleteReplicationPairs(replicationPairs, taskCompleter);
        } catch (InternalException e) {
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, DeviceControllerException.exceptions.unexpectedCondition(e.getMessage()));
        }
    }

    @Override
    public void suspend(RemoteReplicationElement replicationElement, String opId) {
        RemoteReplicationSuspendCompleter taskCompleter = new RemoteReplicationSuspendCompleter(replicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.suspend(replicationElement, taskCompleter);
    }

    @Override
    public void resume(RemoteReplicationElement replicationElement, String opId) {
        RemoteReplicationResumeCompleter taskCompleter = new RemoteReplicationResumeCompleter(replicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.resume(replicationElement, taskCompleter);
    }

    @Override
    public void split(RemoteReplicationElement replicationElement, String opId) {
        RemoteReplicationSplitCompleter taskCompleter = new RemoteReplicationSplitCompleter(replicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.split(replicationElement, taskCompleter);
    }

    @Override
    public void stop(RemoteReplicationElement replicationElement, String opId) {
        RemoteReplicationStopCompleter taskCompleter = new RemoteReplicationStopCompleter(replicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.stop(replicationElement, taskCompleter);
    }

    @Override
    public void establish(RemoteReplicationElement replicationElement, String opId) {
        RemoteReplicationEstablishCompleter taskCompleter = new RemoteReplicationEstablishCompleter(replicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.establish(replicationElement, taskCompleter);
    }

    @Override
    public void failover(RemoteReplicationElement remoteReplicationElement, String opId) {
        RemoteReplicationFailoverCompleter taskCompleter = new RemoteReplicationFailoverCompleter(remoteReplicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.failover(remoteReplicationElement, taskCompleter);
    }

    @Override
    public void failback(RemoteReplicationElement remoteReplicationElement, String opId) {
        RemoteReplicationFailbackCompleter taskCompleter = new RemoteReplicationFailbackCompleter(remoteReplicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.failback(remoteReplicationElement, taskCompleter);
    }

    @Override
    public void swap(RemoteReplicationElement remoteReplicationElement, String opId) {
        RemoteReplicationSwapCompleter taskCompleter = new RemoteReplicationSwapCompleter(remoteReplicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.swap(remoteReplicationElement, taskCompleter);
    }

    @Override
    public void changeReplicationMode(RemoteReplicationElement replicationElement, String newRemoteReplicationMode, String opId) {
        RemoteReplicationChangeModeCompleter taskCompleter = new RemoteReplicationChangeModeCompleter(replicationElement, opId);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.changeReplicationMode(replicationElement, newRemoteReplicationMode, taskCompleter);
    }

    @Override
    public void movePair(URI replicationPair, URI targetGroup, String opId) {
        RemoteReplicationMovePairCompleter taskCompleter = new RemoteReplicationMovePairCompleter(
                new RemoteReplicationElement(ElementType.REPLICATION_PAIR, replicationPair), opId);
        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();
        rrDevice.movePair(replicationPair, targetGroup, taskCompleter);
    }

    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        List<VolumeDescriptor> rrDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[]{VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE,
                        VolumeDescriptor.Type.REMOTE_REPLICATION_TARGET}, new VolumeDescriptor.Type[] {});
        if (rrDescriptors.isEmpty()) {
            _log.info("No Remote Replication Steps required");
            return waitFor;
        }

        List<VolumeDescriptor> sourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE);
        List<VolumeDescriptor> targetDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                VolumeDescriptor.Type.REMOTE_REPLICATION_TARGET);

        _log.info("Adding steps to create remote replication links for volumes");
        waitFor = createRemoteReplicationLinksSteps(workflow, waitFor, sourceDescriptors, targetDescriptors);
        return waitFor;
    }

    public String createRemoteReplicationLinksSteps(Workflow workflow, String waitFor, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors) {

        // all volumes belong to the same device type
        VolumeDescriptor descriptor = sourceDescriptors.get(0);
        Volume volume = dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
        StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);
        String stepId = workflow.createStep(null,
                String.format("Creating remote replication links for source-target pairs: %s", getVolumePairs(sourceURIs, targetURIs)),
                waitFor, volume.getStorageController(), //  pairs are not storage system objects, but passing null is not working
                system.getSystemType(),
                this.getClass(),
                createRemoteReplicationLinksMethod(system.getSystemType(), sourceDescriptors, targetDescriptors),
                rollbackCreateRemoteReplicationLinksMethod(system.getSystemType(), sourceDescriptors, targetDescriptors), null);
        return stepId;
    }

    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        List<VolumeDescriptor> sourceDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE);
        if (sourceDescriptors.isEmpty()) {
            _log.info("No Remote Replication Steps required");
            return waitFor;
        }

        _log.info("Adding steps to delete remote replication links for volumes");
        // Get rr pairs for the source volumes
        List<URI> volumeURIs = new ArrayList<>();
        for (VolumeDescriptor descriptor : volumeDescriptors) {
            volumeURIs.add(descriptor.getVolumeURI());
        }

        List<RemoteReplicationPair> remoteReplicationPairs = new ArrayList<>();
        for (URI volumeURI : volumeURIs) {
            List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, volumeURI, RemoteReplicationPair.class, "sourceElement");
            remoteReplicationPairs.addAll(rrPairs);
        }

        if (remoteReplicationPairs.isEmpty()) {
            // no pairs to delete
            _log.warn("No remote replication pairs for source volumes {}", volumeURIs);
            return waitFor;
        }

        _log.info("Remote replication pairs to delete: {}", remoteReplicationPairs);
        List<URI> pairURIs = new ArrayList<>();
        for (RemoteReplicationPair pair : remoteReplicationPairs) {
            pairURIs.add(pair.getId());
        }

        // all volumes belong to the same device type
        VolumeDescriptor descriptor = sourceDescriptors.get(0);
        Volume volume = dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
        StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());

        // For remote pairs storage system URI is not important. Should we pass null instead of system URI?
        String stepId = workflow.createStep(null,
                String.format("Deleting remote replication links for source-target pairs: %s", remoteReplicationPairs),
                waitFor, system.getId(),  // pairs span two systems, however passing null results in NPE  in dispatcher
                system.getSystemType(),
                this.getClass(),
                deleteRemoteReplicationLinksMethod(pairURIs),
                null, null);
        return stepId;
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId, VolumeWorkflowCompleter completer) {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor, URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String syncDirection, String taskId, BlockSnapshotRestoreCompleter completer) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForPreCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForCreateFullCopy(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForPostCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }

    public Workflow.Method deleteRemoteReplicationLinksMethod(List<URI> rrPairs) {

        return new Workflow.Method("deleteReplicationPairs", rrPairs);
    }

    public Workflow.Method createRemoteReplicationLinksMethod(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors) {

        return new Workflow.Method("createRemoteReplicationLinks", systemType, sourceDescriptors, targetDescriptors);
    }

    public void createRemoteReplicationLinks(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors,
                                             String opId) {

        boolean createGroupPairs = false;

        // build remote replication pairs and call device layer
        _log.info("Source volume descriptors: {}", sourceDescriptors);
        _log.info("Target volume descriptors: {}", targetDescriptors);

        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);
        List<URI> elementURIs = new ArrayList<>();
        elementURIs.addAll(VolumeDescriptor.getVolumeURIs(sourceDescriptors));
        elementURIs.addAll(targetURIs);
        RemoteReplicationPairCompleter taskCompleter = new RemoteReplicationPairCompleter(elementURIs, opId);
        List<RemoteReplicationPair> rrPairs = prepareRemoteReplicationPairs(sourceDescriptors, targetURIs);

        RemoteReplicationDevice rrDevice = getRemoteReplicationDevice();

        // All replication pairs should have the same link characteristics
        VirtualPoolCapabilityValuesWrapper capabilities = sourceDescriptors.get(0).getCapabilitiesValues();
        if (capabilities.getRemoteReplicationGroup() != null) {
            createGroupPairs = true;
        }
        //String linkState = (String) parameters.get(VolumeDescriptor.PARAM_REMOTE_REPLICATION_LINK_STATE);
        //boolean createActive = RemoteReplicationSet.ReplicationState.ACTIVE.toString().
        //        equalsIgnoreCase(linkState);
        if (createGroupPairs) {
            rrDevice.createGroupReplicationPairs(rrPairs, taskCompleter);
        } else {
            rrDevice.createSetReplicationPairs(rrPairs, taskCompleter);
        }
    }

    public Workflow.Method rollbackCreateRemoteReplicationLinksMethod(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors) {
        return new Workflow.Method("rollbackCreateRemoteReplicationLinks", systemType, sourceDescriptors, targetDescriptors);
    }

    public void rollbackCreateRemoteReplicationLinks(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors, String opId) {

        List<URI> sourceVolumesURIs = new ArrayList<>();
        List<URI> targetVolumesURIs = new ArrayList<>();

        List<URI> pairsURIs = new ArrayList<>();
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            for (VolumeDescriptor descriptor : sourceDescriptors) {
                sourceVolumesURIs.add(descriptor.getVolumeURI());
            }
            for (VolumeDescriptor descriptor : targetDescriptors) {
                targetVolumesURIs.add(descriptor.getVolumeURI());
            }

            String logMsg = String.format(
                    "rollbackCreateRemoteReplicationLinks start - System type :%s, Source volumes: %s, Target volumes: %s", systemType, Joiner.on(',').join(sourceVolumesURIs),
                    Joiner.on(',').join(targetVolumesURIs));
            _log.info(logMsg);

            for (URI volumeURI : sourceVolumesURIs) {
                List<RemoteReplicationPair> rrPairsTemp = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, volumeURI, RemoteReplicationPair.class, "sourceElement");
                // select pairs which have target volume from the list
                if (!rrPairsTemp.isEmpty()) {
                    for (RemoteReplicationPair rrp : rrPairsTemp) {
                        if (targetVolumesURIs.contains(rrp.getTargetElement().getURI())) {
                            pairsURIs.add(rrp.getId());
                        }
                    }
                }
            }
            if (!pairsURIs.isEmpty()) {
                deleteReplicationPairs(pairsURIs, opId);
                logMsg = String.format(
                        "rollbackCreateRemoteReplicationLinks end - System type :%s, Remote Replication Pairs: %s", systemType, Joiner.on(',').join(pairsURIs));
                _log.info(logMsg);
            } else {
                logMsg = String.format(
                        "rollbackCreateRemoteReplicationLinks end - System type :%s, No remote replication pairs to delete.", systemType);
                _log.info(logMsg);
                WorkflowStepCompleter.stepSucceded(opId);
            }
        } catch (InternalException e) {
            _log.error(String.format("rollbackCreateRemoteReplicationLinks Failed - System type :%s, Source volumes: %s, Target volumes: %s",
                    systemType, Joiner.on(',').join(sourceVolumesURIs),
                    Joiner.on(',').join(targetVolumesURIs)), e);
            List<URI> volumes = new ArrayList<>(sourceVolumesURIs);
            volumes.addAll(targetVolumesURIs);
            doFailTask(Volume.class, volumes, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackCreateRemoteReplicationLinks Failed - System type :%s, Source volumes: %s, Target volumes: %s",
                    systemType, Joiner.on(',').join(sourceVolumesURIs),
                    Joiner.on(',').join(targetVolumesURIs)), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            List<URI> volumes = new ArrayList<>(sourceVolumesURIs);
            volumes.addAll(targetVolumesURIs);
            doFailTask(Volume.class, volumes, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        } finally {
            // always clean db --- delete replication pairs regardless of outcome
            if (!pairsURIs.isEmpty()) {
                try {
                    List<RemoteReplicationPair> systemReplicationPairs = dbClient.queryObject(RemoteReplicationPair.class, pairsURIs, true);
                    dbClient.markForDeletion(systemReplicationPairs);
                    _log.warn("Deleted left-over replication pairs: \n \t\t{}", systemReplicationPairs);
                } catch (Throwable th) {
                    _log.error("Failed to delete replication pairs \n \t\t {}", pairsURIs, th);
                }
            }
        }
    }

    /**
     * Builds light weight data for remote replication volume pairs for source and target volumes.
     *
     * @param sourceVolumeIds
     * @param targetVolumeIds
     * @return list of remote replication pairs
     */
    List<RRPair> getVolumePairs(List<URI> sourceVolumeIds, List<URI> targetVolumeIds) {

        List<RRPair> pairs = new ArrayList<>();
        List<Volume> sourceVolumes = dbClient.queryObject(Volume.class, sourceVolumeIds);
        List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetVolumeIds);
        for (int i=0; i<sourceVolumeIds.size(); i++) {
            Volume sourceVolume = sourceVolumes.get(i);
            Volume targetVolume = targetVolumes.get(i);
            RRPair pair = new RRPair(sourceVolume.getId(), sourceVolume.getLabel(), targetVolume.getId(), targetVolume.getLabel());
            pairs.add(pair);
        }
        return pairs;
    }

    class RRPair {
        URI sourceUri;
        String sourceLabel;
        URI targetUri;
        String targetLabel;

        public RRPair(URI sourceUri, String sourceLabel, URI targetUri, String targetLabel) {
            this.sourceUri = sourceUri;
            this.sourceLabel = sourceLabel;
            this.targetUri = targetUri;
            this.targetLabel = targetLabel;
        }

        @Override
        public String toString() {

            return String.format("Source volume id: %s, source volume label: %s ; target volume id: %s, target volume label: %s", sourceUri, sourceLabel, targetUri, targetLabel);
        }
    }

    private RemoteReplicationDevice getDevice(String deviceType) {
        // always use RemoteReplicationDevice
        return remoteReplicationdevice;
    }


    /**
     * Build system remote replication pairs for a given source descriptor and target volume.
     *
     * @param sourceDescriptors
     * @param targetURIs
     * @return list of system remote replication pairs
     */
    List<RemoteReplicationPair> prepareRemoteReplicationPairs(List<VolumeDescriptor> sourceDescriptors, List<URI> targetURIs) {
        List<RemoteReplicationPair> rrPairs = new ArrayList<>();

        Iterator<URI> targets = targetURIs.iterator();
        for (VolumeDescriptor sourceDescriptor : sourceDescriptors) {
            RemoteReplicationPair rrPair = new RemoteReplicationPair();
            URI targetURI = targets.next();

            rrPair.setId(URIUtil.createId(RemoteReplicationPair.class));
            rrPair.setElementType(RemoteReplicationPair.ElementType.VOLUME);

            VirtualPoolCapabilityValuesWrapper capabilities = sourceDescriptor.getCapabilitiesValues();
            rrPair.setReplicationGroup(capabilities.getRemoteReplicationGroup());
            rrPair.setReplicationSet(capabilities.getRemoteReplicationSet());
            rrPair.setReplicationMode(capabilities.getRemoteReplicationMode());
            if (capabilities.getRemoteReplicationCreateInactive()) {
                rrPair.addProperty(RemoteReplicationAttributes.PROPERTY_NAME.CREATE_STATE.toString(), RemoteReplicationAttributes.CREATE_STATE.INACTIVE.toString());
            } else {
                rrPair.addProperty(RemoteReplicationAttributes.PROPERTY_NAME.CREATE_STATE.toString(), RemoteReplicationAttributes.CREATE_STATE.INACTIVE.toString());
            }

            rrPair.setSourceElement(new NamedURI(sourceDescriptor.getVolumeURI(), RemoteReplicationPair.ElementType.VOLUME.toString()));
            rrPair.setTargetElement(new NamedURI(targetURI, RemoteReplicationPair.ElementType.VOLUME.toString()));
            Volume volume = dbClient.queryObject(Volume.class, sourceDescriptor.getVolumeURI());
            rrPair.setTenant(volume.getTenant());
            rrPair.setProject(volume.getProject());
            rrPair.setLabel(volume.getLabel());
            _log.info("Remote Replication Pair {} ", rrPair);

            rrPairs.add(rrPair);
        }
        return rrPairs;
    }

    /**
     * Fail the task. Called when an exception occurs attempting to
     * execute a task on multiple data objects.
     *
     * @param clazz
     *            The data object class.
     * @param ids
     *            The ids of the data objects for which the task failed.
     * @param opId
     *            The task id.
     * @param serviceCoded
     *            Original exception.
     */
    private void doFailTask(
            Class<? extends DataObject> clazz, List<URI> ids, String opId, ServiceCoded serviceCoded) {
        try {
            for (URI id : ids) {
                dbClient.error(clazz, id, opId, serviceCoded);
            }
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }


}
