/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remoterreplicationcontroller;


import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationArgument;
import com.emc.storageos.workflow.Workflow;

import java.net.URI;
import java.util.List;

public class RemoteReplicationDeviceController implements RemoteReplicationController, BlockOrchestrationInterface {

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
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        return null;
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
}
