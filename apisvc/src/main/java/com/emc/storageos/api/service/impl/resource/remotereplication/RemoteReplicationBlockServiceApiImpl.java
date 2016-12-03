package com.emc.storageos.api.service.impl.resource.remotereplication;


import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.api.service.impl.resource.AbstractBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.RemoteReplicationScheduler;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

public class RemoteReplicationBlockServiceApiImpl extends AbstractBlockServiceApiImpl<RemoteReplicationScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationBlockServiceApiImpl.class);

    public RemoteReplicationBlockServiceApiImpl() {
        super(Constants.REMOTE_REPLICATION);
    }

    @Override
    public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI, List<URI> volumeURIs, String deletionType) {
        return null;
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
        List<URI> sourceVolumeURIs = VolumeDescriptor.getVolumeURIs(sourceVolumeDescriptors);


        // get varray and vpool from recommendations
        URI targetVArrayUri = volRecommendations.get(0).getVirtualArray();
        VirtualArray targetVArray = _dbClient.queryObject(VirtualArray.class, targetVArrayUri);
        VirtualPool targetVPool = volRecommendations.get(0).getVirtualPool();
        List<VolumeDescriptor> targetVolumeDescriptors = createVolumesAndDescriptors(existingDescriptors,
                param.getName(), size, project, targetVArray, targetVPool, volRecommendations, taskList, task, capabilities,
                Volume.PersonalityTypes.TARGET);
        List<VolumeDescriptor> blockVolumeDescriptors = new ArrayList<>(sourceVolumeDescriptors);
        blockVolumeDescriptors.addAll(targetVolumeDescriptors);

        // We prepared source and target volumes and built descriptors for source and target block volumes.
        // Now we need to build descriptors for remote replication volumes
        // Build descriptors for remote replication source/target
        List<VolumeDescriptor> remoteReplicationSourceDescriptor = buildRemoteReplicationDescriptors(sourceVolumeDescriptors, capabilities, VolumeDescriptor.Type.REMOTE_REPLICATION_SOURCE);
        List<VolumeDescriptor> remoteReplicationTargetDescriptor = buildRemoteReplicationDescriptors(sourceVolumeDescriptors, capabilities, VolumeDescriptor.Type.REMOTE_REPLICATION_TARGET);

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
                // todo: add right error code
                ServiceCoded coded = ServiceError.buildServiceError(
                        ServiceCode.API_RP_VOLUME_CREATE_ERROR, errorMsg);
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

}
