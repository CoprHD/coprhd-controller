/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remoterreplicationcontroller;


import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationArgument;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDevice;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    public RemoteReplicationDevice getDevice() {
        return remoteReplicationdevice;
    }

    public void setDevice(RemoteReplicationDevice device) {
        this.remoteReplicationdevice = device;
    }


    @Override
    public void createRemoteReplicationGroup(URI replicationGroup, String opId) {

    }

    @Override
    public void createGroupReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId) {

    }

    @Override
    public void createSetReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId) {

    }

    @Override
    public void deleteReplicationPairs(List<URI> replicationPairs, String opId) {

    }

    @Override
    public void suspend(URI replicationArgument, String opId) {

    }

    @Override
    public void resume(URI replicationArgument, String opId) {

    }

    @Override
    public void split(URI replicationArgument, String opId) {

    }

    @Override
    public void establish(URI replicationArgument, String opId) {

    }

    @Override
    public void failover(URI replicationArgument, String opId) {

    }

    @Override
    public void failback(URI replicationArgument, String opId) {

    }

    @Override
    public void swap(URI replicationArgument, String opId) {

    }

    @Override
    public void movePair(URI replicationPair, URI targetGroup, String opId) {

    }

    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
            List<VolumeDescriptor> rrDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE,
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
            waitFor = createRemoteReplicationSteps(workflow, waitFor, sourceDescriptors, targetDescriptors);
            return waitFor;
    }

    public String createRemoteReplicationSteps(Workflow workflow, String waitFor, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors) {

        // all volumes belong to the same device type
        VolumeDescriptor descriptor = sourceDescriptors.get(0);
        Volume volume = dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
        StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        List<URI> sourceURIs = VolumeDescriptor.getVolumeURIs(sourceDescriptors);
        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);
                String stepId = workflow.createStep(null,
                        String.format("Creating remote replication links for source-target pairs: %s", getVolumePairs(sourceURIs, targetURIs)),
                        waitFor, volume.getStorageController(), system.getSystemType(),
                        this.getClass(),
                        createRemoteReplicationLinksMethod(system.getSystemType(), sourceDescriptors, targetDescriptors),
                        rollbackCreateRemoteReplicationLinksMethod(system.getSystemType(), sourceDescriptors, targetDescriptors), null);
        return stepId;
    }

    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId, VolumeWorkflowCompleter completer) {
        return null;
    }

    @Override
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForRestoreVolume(Workflow workflow, String waitFor, URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String syncDirection, String taskId, BlockSnapshotRestoreCompleter completer) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForPreCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForCreateFullCopy(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        return null;
    }

    @Override
    public String addStepsForPostCreateReplica(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) throws InternalException {
        return null;
    }

    public Workflow.Method createRemoteReplicationLinksMethod(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors) {

        return new Workflow.Method("createRemoteReplicationLinks", systemType, sourceDescriptors, targetDescriptors);
    }

    public Workflow.Method rollbackCreateRemoteReplicationLinksMethod(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors) {

        return null;
    }

    public void createRemoteReplicationLinks(String systemType, List<VolumeDescriptor> sourceDescriptors, List<VolumeDescriptor> targetDescriptors,
                                             String opId) {

        boolean createGroupPairs = false;

        // build remote replication pairs and call device layer
        List<URI> targetURIs = VolumeDescriptor.getVolumeURIs(targetDescriptors);
        List<URI> elementURIs = new ArrayList<>();
        elementURIs.addAll(VolumeDescriptor.getVolumeURIs(sourceDescriptors));
        elementURIs.addAll(targetURIs);
        RemoteReplicationTaskCompleter taskCompleter = new RemoteReplicationTaskCompleter(elementURIs, opId);
        List<RemoteReplicationPair> rrPairs = prepareRemoteReplicationPairs(sourceDescriptors, targetURIs);

        RemoteReplicationDevice rrDevice = getDevice();

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
                rrPair.setReplicationState(RemoteReplicationSet.ReplicationState.ACTIVE);
            } else {
                rrPair.setReplicationState(RemoteReplicationSet.ReplicationState.SPLIT);
            }

            rrPair.setSourceElement(new NamedURI(sourceDescriptor.getVolumeURI(), RemoteReplicationPair.ElementType.VOLUME.toString()));
            rrPair.setTargetElement(new NamedURI(targetURI, RemoteReplicationPair.ElementType.VOLUME.toString()));
        }
        return rrPairs;
    }


}
