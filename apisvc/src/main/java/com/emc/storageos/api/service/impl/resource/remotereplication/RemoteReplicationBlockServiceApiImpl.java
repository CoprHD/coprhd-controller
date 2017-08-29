/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.remotereplication;


import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.RemoteReplicationScheduler;
import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.api.service.impl.resource.AbstractBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationController;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

public class RemoteReplicationBlockServiceApiImpl extends AbstractBlockServiceApiImpl<RemoteReplicationScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationBlockServiceApiImpl.class);

    public RemoteReplicationBlockServiceApiImpl() {
        super(Constants.REMOTE_REPLICATION);
    }

    @Override
    protected Set<URI> getConnectedVarrays(final URI varrayUID) {
        Set<URI> vArrays = new HashSet<URI>();
        
        URIQueryResultList poolUris = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVirtualArrayStoragePoolsConstraint(varrayUID.toString()), poolUris);
        Iterator<StoragePool> poolItr = _dbClient.queryIterativeObjects(StoragePool.class, poolUris);
        
        Set<URI> sourceSystemUris = new HashSet<URI>();

        while (poolItr.hasNext()) {
            StoragePool pool = poolItr.next();
            if (null == pool || pool.getStorageDevice() == null) {
                continue;
            }
            sourceSystemUris.add(pool.getStorageDevice());
        }

        _log.info("Source System Uris : {}", Joiner.on("\t").join(sourceSystemUris));
        Iterator<StorageSystem> systemItr = _dbClient.queryIterativeObjectField(
                StorageSystem.class, "connectedTo", new ArrayList<URI>(sourceSystemUris));

        Set<URI> remoteSystemUris = new HashSet<URI>();
        while (systemItr.hasNext()) {
            StorageSystem system = systemItr.next();

            if (null == system || system.getRemotelyConnectedTo() == null
                    || system.getRemotelyConnectedTo().isEmpty()) {
                continue;
            }

            remoteSystemUris.addAll(Collections2.transform(system.getRemotelyConnectedTo(),
                    new Function<String, URI>() {

                        @Override
                        public URI apply(final String arg0) {
                            // TODO Auto-generated method stub
                            return URI.create(arg0);
                        }
                    }));
        }

        _log.info("Remote System Uris : {}", Joiner.on("\t").join(remoteSystemUris));
        
        Set<URI> remotePoolUriSet = new HashSet<URI>();
        for (URI remoteUri : remoteSystemUris) {
            URIQueryResultList remotePoolUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(remoteUri), remotePoolUris);
            remotePoolUriSet.addAll(remotePoolUris);
        }
        
        _log.info("Remote Pool Uris : {}", Joiner.on("\t").join(remotePoolUriSet));
        
        Set<String> names = new HashSet<String>();
        names.add("storageDevice");
        names.add("taggedVirtualArrays");
        Collection<StoragePool> remotePools = _dbClient.queryObjectFields(StoragePool.class, names,
                new ArrayList<URI>(remotePoolUriSet));

        for (StoragePool pool : remotePools) {

            if (null == pool || pool.getStorageDevice() == null
                    || pool.getTaggedVirtualArrays() == null) {
                continue;
            }
            vArrays.addAll(Collections2.transform(pool.getTaggedVirtualArrays(),
                    new Function<String, URI>() {

                        @Override
                        public URI apply(final String arg0) {
                            // TODO Auto-generated method stub
                            return URI.create(arg0);
                        }
                    }));

        }

        _log.info("Remote Varray Uris : {}", Joiner.on("\t").join(vArrays));

        return vArrays;
    }

    @Override
    public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI, List<URI> volumeURIs, String deletionType) {
        // We get URIs of source volumes. Get rrPairs with these sources and get targets.
        // Build block descriptors for source and target block volumes (to be processed by block device controller)
        // Build descriptors for rr source volumes (to be processed by rr device controller)

        // Build block descriptors for source volumes
        List<VolumeDescriptor> sourceVolumeDescriptors = new ArrayList<>();
        for (URI uri : volumeURIs) {
            sourceVolumeDescriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA, systemURI, uri, null, null));
        }
        _log.info("Source volume descriptors: \n \t\t {}", sourceVolumeDescriptors);

        // List of remote replication pairs with specified source volumes
        List<RemoteReplicationPair> remoteReplicationPairs = new ArrayList<>();
        for (URI volumeURI : volumeURIs) {
            List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, volumeURI, RemoteReplicationPair.class, "sourceElement");
            remoteReplicationPairs.addAll(rrPairs);
        }
        if (remoteReplicationPairs.isEmpty()) {
            // no pairs to delete
            _log.warn("No remote replication pairs for source volumes \n" +
                    " \t\t{}", volumeURIs);
            return sourceVolumeDescriptors;
        }
        _log.info("Remote replication pairs for source volumes: \n \t\t {}", remoteReplicationPairs);

        // Get target volumes from remote replication pairs
        List<URI> targetVolumeURIs = new ArrayList<>();
        for (RemoteReplicationPair pair : remoteReplicationPairs) {
            targetVolumeURIs.add(pair.getTargetElement().getURI());
        }

        List<Volume> targetVolumes = _dbClient.queryObject(Volume.class, targetVolumeURIs);
        if (targetVolumes == null || targetVolumes.isEmpty()) {
            _log.warn("No target volumes in database for remote replication pairs \n\t\t {}", remoteReplicationPairs);
            // continue: should build RR source descriptors to delete rr pairs
        }

        // Target volumes may belong to different storage systems.
        Map<URI, List<URI>> targetSystemToVolumeMap = new HashMap<>();
        for (Volume volume : targetVolumes) {
            List<URI> targetURIs = targetSystemToVolumeMap.get(volume.getStorageController());
            if ( targetURIs == null) {
                targetURIs = new ArrayList<>();
                targetSystemToVolumeMap.put(volume.getStorageController(), targetURIs);
            }
            targetURIs.add(volume.getId());
        }

        List<VolumeDescriptor> targetVolumeDescriptors = new ArrayList<>();
        for (URI sysURI : targetSystemToVolumeMap.keySet()) {
            List<URI> volURIs = targetSystemToVolumeMap.get(sysURI);
            for (URI uri : volURIs) {
                targetVolumeDescriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA, systemURI, uri, null, null));
            }
        }
        _log.info("Target volume descriptors: \n \t\t {}", targetVolumeDescriptors);

        // Build descriptors for source remote replication volumes
        List<VolumeDescriptor> sourceRRVolumeDescriptors = new ArrayList<>();
        for (URI uri : volumeURIs) {
            sourceRRVolumeDescriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE,
                   systemURI, uri, null, null));
        }
        _log.info("Remote replication source volume descriptors: \n \t\t {}", sourceRRVolumeDescriptors);

        List<VolumeDescriptor> result = new ArrayList<>(sourceVolumeDescriptors);
        result.addAll(targetVolumeDescriptors);
        result.addAll(sourceRRVolumeDescriptors);
        return result;
    }

    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool currentVpool, VirtualPool newVpool, StringBuffer notSuppReasonBuff) {
        return null;
    }

    @Override
    public Collection<? extends String> getReplicationGroupNames(VolumeGroup group) {
        return null;
    }

    @Override
    public TaskList createVolumes(final VolumeCreate param, final Project project,
                                  final VirtualArray varray, final VirtualPool vpool,
                                  final Map<VpoolUse, List<Recommendation>> recommendationMap, TaskList taskList,
                                  final String task, final VirtualPoolCapabilityValuesWrapper capabilities) throws InternalException {
        List<Recommendation> volRecommendations = recommendationMap.get(VpoolUse.ROOT);
        Long size = SizeUtil.translateSize(param.getSize());
        BlockOrchestrationController controller = getController(
                BlockOrchestrationController.class,
                BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);

        List<VolumeDescriptor> existingDescriptors = new ArrayList<>();
        List<Recommendation> sourceVolumeRecommendations = getSourceVolumeRecommendations(volRecommendations);
        List<VolumeDescriptor> sourceVolumeDescriptors = createVolumesAndDescriptors(existingDescriptors,
                param.getName(), size, project, varray, vpool, sourceVolumeRecommendations, taskList, task, capabilities,
                Volume.PersonalityTypes.SOURCE);
        _log.info("Source volume descriptors: {}", sourceVolumeDescriptors);
        List<URI> sourceVolumeURIs = VolumeDescriptor.getVolumeURIs(sourceVolumeDescriptors);


        // get varray and vpool from recommendations
        URI targetVArrayUri = volRecommendations.get(0).getVirtualArray();
        VirtualArray targetVArray = _dbClient.queryObject(VirtualArray.class, targetVArrayUri);
        VirtualPool targetVPool = volRecommendations.get(0).getVirtualPool();
        VirtualPoolCapabilityValuesWrapper targetCapabilities = RemoteReplicationScheduler.buildTargetCapabilities(targetVArray, targetVPool,
                capabilities, _dbClient);

        List<VolumeDescriptor> targetVolumeDescriptors = createVolumesAndDescriptors(existingDescriptors,
                param.getName()+"_TARGET", size, project, targetVArray, targetVPool, volRecommendations, taskList, task, targetCapabilities,
                Volume.PersonalityTypes.TARGET);
        _log.info("Target volume descriptors: {}", targetVolumeDescriptors);
        List<VolumeDescriptor> blockVolumeDescriptors = new ArrayList<>(sourceVolumeDescriptors);
        blockVolumeDescriptors.addAll(targetVolumeDescriptors);

        // We prepared source and target volumes and built descriptors for source and target block volumes.
        // Now we need to build descriptors for remote replication volumes
        // Build descriptors for remote replication source/target
        List<VolumeDescriptor> remoteReplicationSourceDescriptor = buildRemoteReplicationDescriptors(sourceVolumeDescriptors, capabilities, VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE);
        _log.info("RR Source volume descriptors: {}", remoteReplicationSourceDescriptor);

        List<VolumeDescriptor> remoteReplicationTargetDescriptor = buildRemoteReplicationDescriptors(targetVolumeDescriptors, targetCapabilities, VolumeDescriptor.Type.REMOTE_REPLICATION_TARGET);
        _log.info("RR Target volume descriptors: {}", remoteReplicationTargetDescriptor);

        // We are done with all descriptors. Collect them in one final list.
        List<VolumeDescriptor> allDescriptors = new ArrayList<>(sourceVolumeDescriptors);
        allDescriptors.addAll(targetVolumeDescriptors);
        allDescriptors.addAll(remoteReplicationSourceDescriptor);
        allDescriptors.addAll(remoteReplicationTargetDescriptor);

        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(blockVolumeDescriptors);

        try {
            controller.createVolumes(allDescriptors, task);
        } catch (InternalException e) {
            _log.error("Controller error when creating volumes: ", e);
            failVolumeCreateRequest(volumeURIs, task, taskList, e.getMessage());
        } catch (Exception e) {
            _log.error("Controller error when creating volumes", e);
            failVolumeCreateRequest(volumeURIs, task, taskList, e.getMessage());
        }
        return taskList;
    }


    private void failVolumeCreateRequest(List<URI> volumeURIs, String task, TaskList taskList, String errorMessage) {
        String errorMsg = String.format("Controller error: %s", errorMessage);
        if (volumeURIs != null) {
            List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
            for (Volume volume : volumes) {
                Operation op = new Operation();
                ServiceCoded coded = ServiceError.buildServiceError(
                        ServiceCode.API_REMOTE_REPLICATION_VOLUME_CREATE_ERROR, errorMsg);
                op.setMessage(errorMsg);
                op.error(coded);
                _dbClient.createTaskOpStatus(Volume.class, volume.getId(), task, op);
                TaskResourceRep volumeTask = toTask(volume, task, op);
                if (volume.getPersonality() != null
                        && volume.getPersonality().equals(
                        Volume.PersonalityTypes.SOURCE.toString())) {
                    taskList.getTaskList().add(volumeTask);
                }
            }

            for (Volume volume : volumes) {
                volume.setInactive(true);
            }
            _dbClient.updateObject(volumes);
        }
    }

    /**
     * Extract source volume recommendations from list of target recommendations.
     *
     * @param targetVolumeRecommendations
     * @return
     */
    private List<Recommendation> getSourceVolumeRecommendations(List<Recommendation> targetVolumeRecommendations) {

        List<Recommendation> sourceVolumeRecommendations = new ArrayList<>();
        for (Recommendation recommendation : targetVolumeRecommendations) {
            Recommendation sourceVolumeRecommendation = recommendation.getRecommendation();
            sourceVolumeRecommendations.add(sourceVolumeRecommendation);
        }

        return sourceVolumeRecommendations;
    }


    private List<VolumeDescriptor> buildRemoteReplicationDescriptors(List<VolumeDescriptor> blockVolumeDescriptors,
                                                                     VirtualPoolCapabilityValuesWrapper capabilities,
                                                                     VolumeDescriptor.Type type) {

        List<VolumeDescriptor> volumeDescriptors = new ArrayList<>();
        for (VolumeDescriptor volumeDescriptor : blockVolumeDescriptors) {
            VolumeDescriptor rrDescriptor = new VolumeDescriptor(type,
                    volumeDescriptor.getDeviceURI(), volumeDescriptor.getVolumeURI(),
                    volumeDescriptor.getPoolURI(), volumeDescriptor.getConsistencyGroupURI(), capabilities);
            volumeDescriptors.add(rrDescriptor);
        }
        return volumeDescriptors;
    }


    private List<VolumeDescriptor> createVolumesAndDescriptors(
            List<VolumeDescriptor> descriptors, String volumeLabel, Long size, Project project,
            VirtualArray varray, VirtualPool vpool, List<Recommendation> recommendations,
            TaskList taskList, String task,
            VirtualPoolCapabilityValuesWrapper capabilities, Volume.PersonalityTypes personality) {

        // Build descriptors and prepare database volumes for block volumes
        // We will use default logic for this.
        BlockServiceApi api = BlockService.getBlockServiceImpl("default");
        List<VolumeDescriptor> volumeDescriptors = api.createVolumesAndDescriptors(descriptors, volumeLabel, size, project,
                varray, vpool, recommendations, taskList, task, capabilities);

        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
        for (Volume volume : volumes) {
            volume.setPersonality(personality.toString());
        }
        _dbClient.updateObject(volumes);

        return volumeDescriptors;
    }

    /**
     * {@inheritDoc}
     *
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(final URI systemURI, final List<URI> volumeURIs,
                              final String deletionType, final String task) throws InternalException {
        _log.info("Request to delete {} volume(s) with Remote Replication Protection", volumeURIs.size());
        super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expandVolume(Volume volume, long newSize, String taskId)
            throws InternalException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DataObject> String checkForDelete(T object, List<Class<? extends DataObject>> excludeTypes) throws InternalException {
        URI objectURI = object.getId();
        List<Class<? extends DataObject>> excludes = new ArrayList<>();
        if (excludeTypes != null) {
            excludes.addAll(excludeTypes);
        }
        excludes.add(Task.class);

        if (object instanceof Volume) {
            List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, objectURI, RemoteReplicationPair.class, "targetElement");
            if (rrPairs != null && !rrPairs.isEmpty()) {
                // target rr volume, do not allow delete by direct request
                String depMsg = getDependencyChecker().checkDependencies(objectURI, object.getClass(), true, excludes);
                if (depMsg != null) {
                    return depMsg;
                }
            }
        }
        // The dependency checker does not pick up dependencies on
        // BlockSnapshotSession because the containment constraint
        // use the base class BlockObject as the parent i.e., source
        // for a BlockSnapshotSession could be a Volume or BlockSnapshot.
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(objectURI));
        if (!snapSessions.isEmpty()) {
            return BlockSnapshotSession.class.getSimpleName();
        }

        return object.canBeDeleted();
    }

    public void createRemoteReplicationGroup(URI replicationGroupId, List<URI> sourcePorts, List<URI> targetPorts, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.createRemoteReplicationGroup(replicationGroupId, sourcePorts, targetPorts, taskId);
    }

    public void failoverRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.failover(remoterReplicationElement, taskId);
    }

    public void failbackRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.failback(remoterReplicationElement, taskId);
    }

    public void establishRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.establish(remoterReplicationElement, taskId);
    }

    public void splitRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.split(remoterReplicationElement, taskId);
    }

    public void suspendRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.suspend(remoterReplicationElement, taskId);
    }

    public void resumeRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.resume(remoterReplicationElement, taskId);
    }

    public void restoreRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.restore(remoterReplicationElement, taskId);
    }

    public void swapRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.swap(remoterReplicationElement, taskId);
    }

    public void stopRemoteReplicationElementLink(RemoteReplicationElement remoterReplicationElement, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.stop(remoterReplicationElement, taskId);
    }

    public void changeRemoteReplicationMode(RemoteReplicationElement remoterReplicationElement, String newReplicationMode, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.changeReplicationMode(remoterReplicationElement, newReplicationMode, taskId);
    }

    public void moveRemoteReplicationPair(URI remoterReplicationPair, URI replicationGroup, String taskId) {
        RemoteReplicationController controller = getController(
                RemoteReplicationController.class, Constants.EXTERNALDEVICE);

        controller.movePair(remoterReplicationPair, replicationGroup, taskId);
    }


}
