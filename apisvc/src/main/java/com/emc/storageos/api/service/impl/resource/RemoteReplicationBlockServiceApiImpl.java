package com.emc.storageos.api.service.impl.resource;


import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
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
import com.emc.storageos.volumecontroller.SRDFCopyRecommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
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
                param.getName(), size, project, varray, vpool, sourceVolumeRecommendations, taskList, task, capabilities);

        // get varray and vpool from recommendations
        URI targetVArrayUri = volRecommendations.get(0).getVirtualArray();
        VirtualArray targetVArray = _dbClient.queryObject(VirtualArray.class, targetVArrayUri);
        VirtualPool targetVPool = volRecommendations.get(0).getVirtualPool();
        List<VolumeDescriptor> targetVolumeDescriptors = createVolumesAndDescriptors(existingDescriptors,
                param.getName(), size, project, targetVArray, targetVPool, volRecommendations, taskList, task, capabilities);

        // We prepared source and target volumes and built descriptors for source and target block volumes.
        // Now we need to build descriptors for remote replication volumes

        // copy source and target descriptors and modify their type to be source/target remote replica

//////////////////////////////////////////////////////////////////////////////////////////


        for (Recommendation volRecommendation : volRecommendations) {
            List<VolumeDescriptor> existingDescriptors = new ArrayList<>();
            List<VolumeDescriptor> volumeDescriptors = createVolumesAndDescriptors(existingDescriptors,
                    param.getName(), size, project, varray, vpool, volRecommendations, taskList, task, capabilities);
            List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);

            try {
                controller.createVolumes(volumeDescriptors, task);
            } catch (InternalException e) {
                if (_log.isErrorEnabled()) {
                    _log.error("Controller error", e);
                }

                String errorMsg = String.format("Controller error: %s", e.getMessage());
                if (volumeURIs != null) {
                    for (URI volumeURI : volumeURIs) {
                        Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                        if (volume != null) {
                            Operation op = new Operation();
                            // todo: add right error code
                            ServiceCoded coded = ServiceError.buildServiceError(
                                    ServiceCode.API_RP_VOLUME_CREATE_ERROR, errorMsg);
                            op.setMessage(errorMsg);
                            op.error(coded);
                            _dbClient.createTaskOpStatus(Volume.class, volumeURI, task, op);
                            TaskResourceRep volumeTask = toTask(volume, task, op);
                            if (volume.getPersonality() != null
                                    && volume.getPersonality().equals(
                                    Volume.PersonalityTypes.SOURCE.toString())) {
                                taskList.getTaskList().add(volumeTask);
                            }
                        }
                    }
                }
            }
        }
        return taskList;
    }

    @Override
    public List<VolumeDescriptor> createVolumesAndDescriptors(
            List<VolumeDescriptor> descriptors, String volumeLabel, Long size, Project project,
            VirtualArray varray, VirtualPool vpool, List<Recommendation> recommendations,
            TaskList taskList, String task,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();

        // Build descriptors and prepare database volumes for block source and target volumes
        // We will use default logic for this.
        BlockServiceApi api = BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.srdf.name());

        for (Recommendation recommendation: recommendations) {
            if (recommendation instanceof SRDFCopyRecommendation) {
                SRDFRecommendation srdfRecommendation = (SRDFRecommendation) recommendation.getRecommendation();
                // Get the Target structure
                SRDFRecommendation.Target target = srdfRecommendation.getVirtualArrayTargetMap()
                        .get(recommendation.getVirtualArray());
                if (target.getDescriptors() != null) {
                    volumeDescriptors.addAll(target.getDescriptors());
                }
            }
            // We never mix recommendation types SRDFCopyRecommendation and SRDFRecommendation,
            // so if we had SRDFCopyRecommendations, just return their descriptors now.
            if (!volumeDescriptors.isEmpty()) {
                return volumeDescriptors;
            }
        }

        // Prepare the Bourne Volumes to be created and associated
        // with the actual storage system volumes created. Also create
        // a BlockTaskList containing the list of task resources to be
        // returned for the purpose of monitoring the volume creation
        // operation for each volume to be created.
        if (taskList == null) {
            taskList = new TaskList();
        }

        Iterator<Recommendation> recommendationsIter;

        final BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null
                : _dbClient.queryObject(BlockConsistencyGroup.class,
                capabilities.getBlockConsistencyGroup());

        // prepare the volumes
        List<URI> volumeURIs = prepareRecommendedVolumes(task, taskList, project, varray,
                vpool, capabilities.getResourceCount(), recommendations, consistencyGroup,
                volumeLabel, size.toString());

        // Execute the volume creations requests for each recommendation.
        recommendationsIter = recommendations.iterator();
        while (recommendationsIter.hasNext()) {
            Recommendation recommendation = recommendationsIter.next();
            volumeDescriptors.addAll(createVolumeDescriptors(
                    (SRDFRecommendation) recommendation, volumeURIs, capabilities));
            // Log volume descriptor information
            logVolumeDescriptorPrecreateInfo(volumeDescriptors, task);
        }
        return volumeDescriptors;
    }


}
