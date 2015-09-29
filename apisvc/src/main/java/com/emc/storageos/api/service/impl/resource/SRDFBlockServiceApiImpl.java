/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.SRDFScheduler;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume.VolumeAccessState;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.srdfcontroller.SRDFController;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;

/**
 * Block Service subtask (parts of larger operations) SRDF implementation.
 */
public class SRDFBlockServiceApiImpl extends AbstractBlockServiceApiImpl<SRDFScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(SRDFBlockServiceApiImpl.class);

    public SRDFBlockServiceApiImpl() {
        super(DiscoveredDataObject.Type.srdf.name());
    }

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";
    protected final static Long V3CYLINDERSIZE = 1966080L;
    private final static String LABEL_SUFFIX_FOR_46X = "T";

    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    @Autowired
    protected DependencyChecker _dependencyChecker;

    protected CoordinatorClient _coordinator;

    @Override
    public void setCoordinator(final CoordinatorClient locator) {
        _coordinator = locator;
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param <T>
     * @return
     */
    @Override
    protected <T extends Controller> T getController(final Class<T> clazz, final String hw) {
        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw,
                clazz.getSimpleName());
    }

    private List<Recommendation> getRecommendationsForVirtualPoolChangeRequest(final Volume volume,
            final VirtualPool cos, final VirtualPoolChangeParam cosChangeParam) {
        Project project = _dbClient.queryObject(Project.class, volume.getProject());

        // SRDF volume placement is requested.
        return getBlockScheduler().scheduleStorageForCosChangeUnprotected(
                volume,
                cos,
                SRDFScheduler.getTargetVirtualArraysForVirtualPool(project, cos, _dbClient,
                        _permissionsHelper),
                cosChangeParam);
    }

    /**
     * Prepare Recommended Volumes for SRDF scenarios only.
     * 
     * This method is responsible for acting the same as the unprotected "prepareRecommendedVolumes"
     * call, however it needs to create multiple volumes per single volume requests in order to
     * generate SRDF protection.
     * 
     * Those most typical scenario is, that for any one volume requested in an SRDF configuration,
     * we create: 1. One Source Volume 3. One Target Volume on target varrays
     * 
     * @param param
     *            volume create request
     * @param task
     *            task from request or generated
     * @param taskList
     *            task list
     * @param project
     *            project from request
     * @param varray
     *            varray from request
     * @param vpool
     *            vpool from request
     * @param volumeCount
     *            volume count from the request
     * @param recommendations
     *            list of resulting recommendations from placement
     * @param consistencyGroup
     *            consistency group ID
     * @return list of volume URIs created
     */
    private List<URI> prepareRecommendedVolumes(final VolumeCreate param, final String task,
            final TaskList taskList, final Project project, final VirtualArray varray,
            final VirtualPool vpool, final Integer volumeCount,
            final List<Recommendation> recommendations,
            final BlockConsistencyGroup consistencyGroup, final int volumeCounter,
            final String volumeLabel) {
        List<URI> volumeURIs = new ArrayList<URI>();
        try {
            // Create an entire Protection object for each recommendation result.
            Iterator<Recommendation> recommendationsIter = recommendations.iterator();
            while (recommendationsIter.hasNext()) {
                SRDFRecommendation recommendation = (SRDFRecommendation) recommendationsIter.next();
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, recommendation.getSourceStorageSystem());
                // Prepare the Bourne Volumes to be created and associated
                // with the actual storage system volumes created. Also create
                // a BlockTaskList containing the list of task resources to be
                // returned for the purpose of monitoring the volume creation
                // operation for each volume to be created.
                for (int i = 0; i < volumeCount; i++) {
                    // get generated volume name
                    String newVolumeLabel = generateDefaultVolumeLabel(param.getName(), i, volumeCount);

                    // Grab the existing volume and task object from the incoming task list
                    Volume srcVolume = StorageScheduler.getPrecreatedVolume(_dbClient, taskList, newVolumeLabel);
                    boolean volumePrecreated = false;
                    if (srcVolume != null) {
                        volumePrecreated = true;
                    }

                    // Assemble a Replication Set; A Collection of volumes. One production, and any
                    // number of targets.
                    if (recommendation.getVpoolChangeVolume() == null) {
                        srcVolume = prepareVolume(srcVolume, param, project, varray, vpool,
                                param.getSize(), recommendation, newVolumeLabel, consistencyGroup,
                                task, false, Volume.PersonalityTypes.SOURCE, null, null, null);
                        volumeURIs.add(srcVolume.getId());
                        if (!volumePrecreated) {
                            taskList.getTaskList().add(toTask(srcVolume, task));
                        }
                    } else {
                        srcVolume = _dbClient.queryObject(Volume.class,
                                recommendation.getVpoolChangeVolume());
                        Operation op = _dbClient.createTaskOpStatus(Volume.class, srcVolume.getId(), task,
                                ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
                        // Fill in additional information that prepare would've filled in that's specific to SRDF.
                        // Best to only fill in information here that isn't harmful if a rollback occurred,
                        // and the protection never got set up.
                        volumeURIs.add(srcVolume.getId());
                        taskList.getTaskList().add(toTask(srcVolume, task, op));
                    }

                    Map<URI, VpoolRemoteCopyProtectionSettings> settingMap = VirtualPool
                            .getRemoteProtectionSettings(vpool, _dbClient);
                    for (VirtualArray protectionVirtualArray : SRDFScheduler
                            .getTargetVirtualArraysForVirtualPool(project, vpool, _dbClient,
                                    _permissionsHelper)) {
                        VpoolRemoteCopyProtectionSettings settings = settingMap
                                .get(protectionVirtualArray.getId());

                        // COP-16363 Create target BCG in controllersvc

                        // Prepare and populate CG request for the SRDF targets
                        volumeURIs.addAll(prepareTargetVolumes(param, project, vpool, recommendation,
                                new StringBuilder(newVolumeLabel), protectionVirtualArray,
                                settings, srcVolume, task, taskList));
                    }
                }
            }
        } catch (InternalException e) {
            _log.error("Rolling back the created CGs if any.");
            throw e;
        } catch (Exception e) {
            _log.error("Rolling back the created CGs if any.");
            throw APIException.badRequests.srdfInternalError(e);
        }
        return volumeURIs;
    }

    /**
     * Prep work to call the orchestrator to create the volume descriptors
     * 
     * @param recommendation
     *            recommendation object from SRDFRecommendation
     * @param volumeURIs
     *            volumes already prepared
     * @param capabilities
     *            vpool capabilities
     * @return list of volume descriptors
     * @throws ControllerException
     */
    private List<VolumeDescriptor> createVolumeDescriptors(final SRDFRecommendation recommendation,
            final List<URI> volumeURIs, final VirtualPoolCapabilityValuesWrapper capabilities)
                    throws ControllerException {

        List<Volume> preparedVolumes = _dbClient.queryObject(Volume.class, volumeURIs);

        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
        // Package up the Volume descriptors
        for (Volume volume : preparedVolumes) {
            VolumeDescriptor.Type volumeType = VolumeDescriptor.Type.SRDF_SOURCE;

            // CoS Change flow, mark the production volume as already existing, so it doesn't get
            // created
            if (recommendation.getVpoolChangeVolume() != null
                    && recommendation.getVpoolChangeVolume().equals(volume.getId())) {
                volumeType = VolumeDescriptor.Type.SRDF_EXISTING_SOURCE;
                VolumeDescriptor desc = new VolumeDescriptor(volumeType,
                        volume.getStorageController(), volume.getId(), volume.getPool(), null,
                        capabilities, volume.getCapacity());
                Map<String, Object> volumeParams = new HashMap<String, Object>();
                volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID,
                        recommendation.getVpoolChangeVolume());
                volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID,
                        recommendation.getVpoolChangeVpool());
                volumeParams.put(VolumeDescriptor.PARAM_VPOOL_OLD_VPOOL_ID,
                        volume.getVirtualPool());

                desc.setParameters(volumeParams);
                descriptors.add(desc);

                _log.info("Adding Source Volume Descriptor for: " + desc.toString());
            } else {
                // Normal create-from-scratch flow
                if (volume.getPersonality() == null) {
                    throw APIException.badRequests.srdfVolumeMissingPersonalityAttribute(volume
                            .getId());
                }
                if (volume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) {
                    volumeType = VolumeDescriptor.Type.SRDF_TARGET;
                }

                VolumeDescriptor desc = new VolumeDescriptor(volumeType,
                        volume.getStorageController(), volume.getId(), volume.getPool(), null,
                        capabilities, volume.getCapacity());

                descriptors.add(desc);

                _log.info("Adding Non-Source Volume Descriptor for: " + desc.toString());
            }
        }

        return descriptors;
    }

    /**
     * Prepare Volume for an SRDF protected volume
     * 
     * @param volume
     *            pre-created volume from the api service
     * @param param
     *            volume request
     * @param project
     *            project requested
     * @param varray
     *            varray requested
     * @param vpool
     *            vpool requested
     * @param size
     *            size of the volume
     * @param placement
     *            recommendation for placement
     * @param label
     *            volume label
     * @param consistencyGroup
     *            consistency group
     * @param token
     *            task id
     * @param remote
     *            is this a target volume
     * @param personality
     *            normal volume or metadata
     * @param srcVolumeId
     *            source volume ID; only for target volumes
     * @param raGroupURI
     *            RDF Group of the source array to use
     * @param copyMode
     *            copy policy, like async or sync
     * 
     * @return a persisted volume
     */
    private Volume prepareVolume(Volume volume, final VolumeCreate param,
            final Project project, final VirtualArray varray, final VirtualPool vpool,
            final String size, final Recommendation placement,
            final String label, final BlockConsistencyGroup consistencyGroup, final String token,
            final boolean remote, final Volume.PersonalityTypes personality, final URI srcVolumeId,
            final URI raGroupURI, final String copyMode) {
        boolean newVolume = false;

        if (volume == null) {
            // check for duplicate label
            validateVolumeLabel(label, project);

            newVolume = true;
            volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setOpStatus(new OpStatusMap());
        } else {
            volume = _dbClient.queryObject(Volume.class, volume.getId());
        }

        volume.setLabel(label);
        volume.setCapacity(SizeUtil.translateSize(size));
        volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                vpool.getSupportedProvisioningType()));
        volume.setVirtualPool(vpool.getId());
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setVirtualArray(varray.getId());
        volume.setSrdfGroup(raGroupURI);
        volume.setSrdfCopyMode(copyMode);
        if (null != placement.getSourceStoragePool()) {
            StoragePool pool = _dbClient.queryObject(StoragePool.class, placement.getSourceStoragePool());
            if (null != pool) {
                volume.setProtocol(new StringSet());
                volume.getProtocol().addAll(
                        VirtualPoolUtil.getMatchingProtocols(vpool.getProtocols(),
                                pool.getProtocols()));
            }
        }
        volume.setPersonality(personality.toString());

        if (personality.equals(Volume.PersonalityTypes.SOURCE)) {
            volume.setAccessState(VolumeAccessState.READWRITE.name());
        } else if (personality.equals(Volume.PersonalityTypes.TARGET)) {
            volume.setAccessState(VolumeAccessState.NOT_READY.name());
        }

        if (!remote) {
            volume.setStorageController(placement.getSourceStorageSystem());
            volume.setPool(placement.getSourceStoragePool());
        } else {
            volume.setStorageController(((SRDFRecommendation) placement).getVirtualArrayTargetMap()
                    .get(varray.getId()).getTargetStorageDevice());
            volume.setPool(((SRDFRecommendation) placement).getVirtualArrayTargetMap()
                    .get(varray.getId()).getTargetStoragePool());
        }
        volume.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
        op.setStartTime(Calendar.getInstance());
        volume.getOpStatus().put(token, op);
        if (consistencyGroup != null) {
            volume.setConsistencyGroup(consistencyGroup.getId());
        }

        if (null != vpool.getAutoTierPolicyName()) {
            URI autoTierPolicyUri = StorageScheduler.getAutoTierPolicy(volume.getPool(),
                    vpool.getAutoTierPolicyName(), _dbClient);
            if (null != autoTierPolicyUri) {
                volume.setAutoTieringPolicyUri(autoTierPolicyUri);
            }
        }

        // Keep track of target volumes associated with the source volume
        if (srcVolumeId != null) {
            Volume srcVolume = _dbClient.queryObject(Volume.class, srcVolumeId);
            if (srcVolume.getSrdfTargets() == null) {
                srcVolume.setSrdfTargets(new StringSet());
            }
            // This is done in prepare, but the source volume may be a cos change volume that didn't
            // go through that process.
            srcVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
            srcVolume.getSrdfTargets().add(volume.getId().toString());
            _dbClient.persistObject(srcVolume);

            volume.setSrdfParent(new NamedURI(srcVolume.getId(), srcVolume.getLabel()));
            computeCapacityforSRDFV3ToV2(volume);
        }

        if (newVolume) {
            _dbClient.createObject(volume);
        } else {
            _dbClient.updateAndReindexObject(volume);
        }

        return volume;
    }

    /**
     * Populate and prepare all target volumes (targets) associated with SRDF protection of a
     * volume.
     * 
     * @param param
     *            Volume creation request
     * @param project
     *            project requested
     * @param vpool
     *            class of service requested
     * @param recommendation
     *            recommendation placement object
     * @param volumeLabelBuilder
     *            label building to create volume labels
     * @param targetVirtualArray
     *            protection varray we're playing with
     * @param settings
     *            settings
     * @param task
     *            task id
     * @return list of volume IDs
     */
    private List<URI> prepareTargetVolumes(final VolumeCreate param, final Project project,
            final VirtualPool vpool, final SRDFRecommendation recommendation,
            final StringBuilder volumeLabelBuilder, final VirtualArray targetVirtualArray,
            final VpoolRemoteCopyProtectionSettings settings, final Volume srcVolume,
            final String task, final TaskList taskList) {
        Volume volume;
        List<URI> volumeURIs = new ArrayList<URI>();

        // By default, the target VirtualPool is the source VirtualPool
        VirtualPool targetVpool = vpool;
        // If there's a VirtualPool in the protection settings that is different, use it instead.
        if (settings.getVirtualPool() != null) {
            targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
        }

        // Target volume in a varray
        volume = prepareVolume(
                null,
                param,
                project,
                targetVirtualArray,
                targetVpool,
                param.getSize(),
                recommendation,
                new StringBuilder(volumeLabelBuilder.toString()).append(
                        "-target-" + targetVirtualArray.getLabel()).toString(),
                null,
                task, true, Volume.PersonalityTypes.TARGET, srcVolume.getId(), recommendation
                        .getVirtualArrayTargetMap().get(targetVirtualArray.getId())
                        .getSourceRAGroup(),
                settings.getCopyMode());
        volumeURIs.add(volume.getId());
        // add target only during vpool change.
        if (recommendation.getVpoolChangeVolume() != null) {
            taskList.getTaskList().add(toTask(volume, task));
        }
        return volumeURIs;
    }

    /**
     * SRDF between VMAX3 to VMAX2 is failing due to configuration mismatch (OPT#475186).
     * As a workaround, calculate the VMAX2 volume size based on the VMAX3 cylinder size.
     * 
     * @param targetVolume
     */
    private void computeCapacityforSRDFV3ToV2(Volume targetVolume) {
        if (targetVolume == null) {
            return;
        }
        StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, targetVolume.getStorageController());

        Volume sourceVolume = _dbClient.queryObject(Volume.class, targetVolume.getSrdfParent());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());

        // Source : VMAX3 & Target : VMAX2 case
        if (sourceSystem != null && targetSystem != null && sourceSystem.checkIfVmax3() && !targetSystem.checkIfVmax3()) {
            Long cylinderCount = (long) Math.ceil((double) targetVolume.getCapacity() / V3CYLINDERSIZE);
            targetVolume.setCapacity(cylinderCount * V3CYLINDERSIZE);
            _log.info("Cylinder Count : {}, VMAX2 volume Capacity : {}", cylinderCount, targetVolume.getCapacity());
        }
    }

    @Override
    public TaskList createVolumes(final VolumeCreate param, final Project project,
            final VirtualArray varray, final VirtualPool cos,
            final List<Recommendation> volRecommendations, TaskList taskList,
            final String task, final VirtualPoolCapabilityValuesWrapper capabilities) throws InternalException {

        List<Recommendation> recommendations = volRecommendations;
        // Prepare the Bourne Volumes to be created and associated
        // with the actual storage system volumes created. Also create
        // a BlockTaskList containing the list of task resources to be
        // returned for the purpose of monitoring the volume creation
        // operation for each volume to be created.
        int volumeCounter = 1;
        String volumeLabel = param.getName();
        if (taskList == null) {
            taskList = new TaskList();
        }

        Iterator<Recommendation> recommendationsIter;

        final BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null
                : _dbClient.queryObject(BlockConsistencyGroup.class,
                        capabilities.getBlockConsistencyGroup());

        // prepare the volumes
        List<URI> volumeURIs = prepareRecommendedVolumes(param, task, taskList, project, varray,
                cos, capabilities.getResourceCount(), recommendations, consistencyGroup,
                volumeCounter, volumeLabel);

        // Execute the volume creations requests for each recommendation.
        recommendationsIter = recommendations.iterator();
        while (recommendationsIter.hasNext()) {
            Recommendation recommendation = recommendationsIter.next();
            try {
                List<VolumeDescriptor> volumeDescriptors = createVolumeDescriptors(
                        (SRDFRecommendation) recommendation, volumeURIs, capabilities);
                // Log volume descriptor information
                logVolumeDescriptorPrecreateInfo(volumeDescriptors, task);
                BlockOrchestrationController controller = getController(
                        BlockOrchestrationController.class,
                        BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
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
                            ServiceCoded coded = ServiceError.buildServiceError(
                                    ServiceCode.API_RP_VOLUME_CREATE_ERROR, errorMsg.toString());
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

                // If there was a controller error creating the volumes,
                // throw an internal server error and include the task
                // information in the response body, which will inform
                // the user what succeeded and what failed.
                throw APIException.badRequests.cannotCreateSRDFVolumes(e);
            }
        }

        return taskList;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(final URI systemURI, final List<URI> volumeURIs,
            final String deletionType, final String task) throws InternalException {
        _log.info("Request to delete {} volume(s) with SRDF Protection", volumeURIs.size());
        super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public <T extends DataObject> String checkForDelete(final T object) throws InternalException {
        // The standard dependency checker really doesn't fly with SRDF because we need to determine
        // if we can do
        // a tear-down of the volume, and that tear-down involved cleaning up dependent
        // relationships.
        // So this will be a bit more manual and calculated. In order to determine if we can delete
        // this object,
        // we need to make sure:
        // 1. This device is a SOURCE device if the protection set is happy and healthy (this is
        // done before we get here)
        // 2. This device and all of the other devices don't have any block snapshots
        // 3. If the device isn't part of a healthy SRDF protection, then do a dependency check

        // Generate a list of dependencies, if there are any.
        Map<URI, URI> dependencies = new HashMap<URI, URI>();

        // Get all of the volumes associated with the volume
        List<URI> volumeIDs;
        // JIRA CTRL-266; perhaps we can improve this using DB indexes
        volumeIDs = Volume.fetchSRDFVolumes(_dbClient, object.getId());
        for (URI volumeID : volumeIDs) {
            URIQueryResultList list = new URIQueryResultList();
            Constraint constraint = ContainmentConstraint.Factory
                    .getVolumeSnapshotConstraint(volumeID);
            _dbClient.queryByConstraint(constraint, list);
            Iterator<URI> it = list.iterator();
            while (it.hasNext()) {
                URI snapshotID = it.next();
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotID);
                if (snapshot != null && !snapshot.getInactive()) {
                    dependencies.put(volumeID, snapshotID);
                }
            }

            if (!dependencies.isEmpty()) {
                throw APIException.badRequests.cannotDeleteVolumeBlockSnapShotExists(String
                        .valueOf(dependencies));
            }

            // Do a relatively "normal" check, as long as it's a "broken" SRDF set.
            // It's considered a broken SRDF set if there's only one volume returned from the
            // fetchSRDFVolumes() call,
            // Because that can only have happened if the RDF relationship was already torn down.
            if (volumeIDs.size() == 1) {
                String depMsg = _dependencyChecker.checkDependencies(object.getId(),
                        object.getClass(), true);
                if (depMsg != null) {
                    return depMsg;
                }
                return object.canBeDeleted();
            }
        }

        return null;
    }

    @Override
    public TaskList deactivateMirror(final StorageSystem device, final URI mirrorURI,
            final String task) {
        // FIXME Should use relevant ServiceCodeException here
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageSystemConnectivityList getStorageSystemConnectivity(final StorageSystem system) {
        StorageSystemConnectivityList connectivityList = new StorageSystemConnectivityList();
        // Set used to ensure unique values are added to the connectivity list
        Set<String> existing = new HashSet<String>();

        if (system.getRemotelyConnectedTo() != null) {
            for (String remoteSystemUri : system.getRemotelyConnectedTo()) {
                if (remoteSystemUri == null
                        || NullColumnValueGetter.getNullStr().equals(remoteSystemUri)) {
                    continue;
                }

                StorageSystem remoteSystem = _dbClient.queryObject(StorageSystem.class,
                        URI.create(remoteSystemUri));
                if (remoteSystem != null) {
                    StorageSystemConnectivityRestRep connection = new StorageSystemConnectivityRestRep();
                    connection.getConnectionTypes().add(SupportedReplicationTypes.SRDF.toString());
                    connection.setProtectionSystem(toNamedRelatedResource(
                            ResourceTypeEnum.PROTECTION_SYSTEM, URI.create(remoteSystemUri),
                            remoteSystem.getSerialNumber()));
                    connection.setStorageSystem(toNamedRelatedResource(
                            ResourceTypeEnum.STORAGE_SYSTEM, system.getId(),
                            system.getSerialNumber()));

                    // The key is a transient unique ID, since none of the actual fields guarantee
                    // uniqueness.
                    // We use this to make sure we don't add the same storage system more than once
                    // for the same
                    // protection system and connection type.
                    String key = connection.getProtectionSystem().toString()
                            + connection.getConnectionTypes()
                            + connection.getStorageSystem().toString();
                    if (!existing.contains(key)) {
                        existing.add(key);
                        connectivityList.getConnections().add(connection);
                    }
                }
            }
        }

        return connectivityList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeVirtualArrayForVolumes(final List<Volume> volume,
            final BlockConsistencyGroup cg, final List<Volume> cgVolumes,
            final VirtualArray varray, final String taskId) throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * Upgrade a local block volume to a protected SRDF volume
     * 
     * @param volume
     *            -- VPlex volume (existing).
     * @param vpool
     *            -- Requested vpool.
     * @param taskId
     * @throws InternalException
     */
    private void upgradeToTargetVolume(final Volume volume, final VirtualPool vpool,
            final VirtualPoolChangeParam cosChangeParam, final String taskId)
                    throws InternalException {
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, volume.getConsistencyGroup());
        List<Recommendation> recommendations = getRecommendationsForVirtualPoolChangeRequest(
                volume, vpool, cosChangeParam);

        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageFoundForVolume();
        }

        // Call out to the respective block service implementation to prepare and create the
        // volumes based on the recommendations.
        Project project = _dbClient.queryObject(Project.class, volume.getProject());
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());

        // Generate a VolumeCreate object that contains the information that createVolumes likes to
        // consume.
        VolumeCreate param = new VolumeCreate(volume.getLabel(), String.valueOf(volume
                .getCapacity()), 1, vpool.getId(), volume.getVirtualArray(), volume.getProject()
                        .getURI());

        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));

        if (volume.getIsComposite()) {
            // add meta volume properties to the capabilities instance
            capabilities.put(VirtualPoolCapabilityValuesWrapper.IS_META_VOLUME, volume.getIsComposite());
            capabilities.put(VirtualPoolCapabilityValuesWrapper.META_VOLUME_TYPE, volume.getCompositionType());
            capabilities.put(VirtualPoolCapabilityValuesWrapper.META_VOLUME_MEMBER_COUNT, volume.getMetaMemberCount());
            capabilities.put(VirtualPoolCapabilityValuesWrapper.META_VOLUME_MEMBER_SIZE, volume.getMetaMemberSize());
            _log.debug(String.format("Capabilities : isMeta: %s, Meta Type: %s, Member size: %s, Count: %s",
                    capabilities.getIsMetaVolume(), capabilities.getMetaVolumeType(), capabilities.getMetaVolumeMemberSize(),
                    capabilities.getMetaVolumeMemberCount()));
        }

        createVolumes(param, project, varray, vpool, recommendations, null, taskId, capabilities);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void changeVolumeVirtualPool(final URI systemURI, final Volume volume,
            final VirtualPool vpool, final VirtualPoolChangeParam vpoolChangeParam, final String taskId)
                    throws InternalException {
        _log.debug("Volume {} VirtualPool change.", volume.getId());

        // Check for common Vpool updates handled by generic code. It returns true if handled.
        List<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }

        // Check if the volume is normal without CG but new vPool with CG enabled.
        if (NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())
                && (null != vpool.getMultivolumeConsistency() && vpool.getMultivolumeConsistency())) {
            _log.info("VPool change is not permitted as volume is not part of CG but new VPool is consistency enabled.");
            throw APIException.badRequests.changeToVirtualPoolNotSupportedForNonCGVolume(volume.getId(),
                    vpool.getLabel());
        }

        if (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())
                || (volume.getSrdfTargets() != null && !volume.getSrdfTargets().isEmpty())) {
            throw APIException.badRequests.srdfVolumeVPoolChangeNotSupported(volume.getId());
        }

        // Get the storage system.
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        String systemType = storageSystem.getSystemType();
        if (DiscoveredDataObject.Type.vmax.name().equals(systemType)) {
            _log.debug("SRDF Protection VirtualPool change for vmax volume.");
            upgradeToTargetVolume(volume, vpool, vpoolChangeParam, taskId);
        } else {
            // not vmax volume
            throw APIException.badRequests.srdfVolumeVPoolChangeNotSupported(volume.getId());
        }
    }

    @Override
    public void changeVolumeVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {

        // Check for common Vpool updates handled by generic code. It returns true if handled.
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }

        for (Volume volume : volumes) {
            changeVolumeVirtualPool(volume.getStorageController(), volume, vpool, vpoolChangeParam, taskId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVolumeExpansionRequest(final Volume volume, final long newSize) {
        _log.debug("Verify if SRDF volume {} can be expanded", volume.getId());

        // Look at all source and target volumes and make sure they can all be expanded
        super.verifyVolumeExpansionRequest(volume, newSize);

        for (String volumeID : volume.getSrdfTargets()) {
            try {
                super.verifyVolumeExpansionRequest(
                        _dbClient.queryObject(Volume.class, new URI(volumeID)), newSize);
            } catch (URISyntaxException e) {
                throw APIException.badRequests.invalidURI(volumeID, e);
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws ControllerException
     */
    @Override
    public void expandVolume(final Volume volume, final long newSize, final String taskId)
            throws ControllerException {

        if (PersonalityTypes.TARGET.toString().equalsIgnoreCase(volume.getPersonality())) {
            throw APIException.badRequests.expandSupportedOnlyOnSource(volume.getId());
        }
        SRDFController controller = getController(SRDFController.class, "vmax");
        // TODO : JIRA CTRL-5335 SRDF expand needs to go via BlockOrchestrationController.
        controller.expandVolume(volume.getStorageController(), volume.getPool(), volume.getId(), newSize, taskId);
    }

    @Override
    protected Set<URI> getConnectedVarrays(final URI varrayUID) {
        Set<URI> vArrays = new HashSet<URI>();

        // JIRA CTRL-267 opened; this should just be getting remotely connecteds from the DB, not
        // calculating on the fly.

        // TODO: convert to the latest version
        List<URI> poolUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePoolsConstraint(varrayUID.toString()));

        Set<URI> sourceSystemUris = new HashSet<URI>();

        Iterator<StoragePool> poolItr = _dbClient.queryIterativeObjectField(StoragePool.class,
                "storageDevice", poolUris);

        while (poolItr.hasNext()) {
            StoragePool pool = poolItr.next();
            if (null == pool || pool.getStorageDevice() == null) {
                _log.info("Pool 1 null");
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
            // TODO: convert to the latest version
            List<URI> remotePoolUris = _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(remoteUri));
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

    private boolean isSRDFParentInactiveForTarget(Volume volume) {
        NamedURI parent = volume.getSrdfParent();
        if (NullColumnValueGetter.isNullNamedURI(parent)) {
            String msg = String.format("Volume %s has no SRDF parent", volume.getId());
            throw new IllegalStateException(msg);
        }
        Volume parentVolume = _dbClient.queryObject(Volume.class, parent.getURI());
        return parentVolume == null || parentVolume.getInactive();
    }

    private Volume.PersonalityTypes getPersonality(Volume volume) {
        if (Strings.isNullOrEmpty(volume.getPersonality())) {
            String msg = String.format("Volume %s has no personality", volume.getId());
            throw new IllegalStateException(msg);
        }
        return Volume.PersonalityTypes.valueOf(volume.getPersonality());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
            List<URI> volumeURIs, String deletionType) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (URI volumeURI : volumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            VolumeDescriptor.Type descriptorType;
            if (volume.getPersonality() == null || volume.getPersonality().contains("null")) {
                descriptorType = VolumeDescriptor.Type.BLOCK_DATA;
            } else if (Volume.PersonalityTypes.TARGET == getPersonality(volume)) {
                if (isSRDFParentInactiveForTarget(volume)) {
                    descriptorType = VolumeDescriptor.Type.BLOCK_DATA;
                } else {
                    _log.warn("Attempted to delete an SRDF target that had an active SRDF source");
                    throw APIException.badRequests.cannotDeleteSRDFTargetWithActiveSource(volumeURI,
                            volume.getSrdfParent().getURI());
                }
            } else {
                descriptorType = VolumeDescriptor.Type.SRDF_SOURCE;
            }

            VolumeDescriptor descriptor = new VolumeDescriptor(descriptorType, systemURI, volumeURI, null, null);
            volumeDescriptors.add(descriptor);
            // Add a descriptor for each of the associated volumes.
            for (URI assocVolId : Volume.fetchSRDFVolumes(_dbClient, volumeURI)) {
                Volume assocVolume = _dbClient.queryObject(Volume.class, assocVolId);
                if (null == assocVolume) {
                    continue;
                }
                VolumeDescriptor assocDesc = new VolumeDescriptor(
                        VolumeDescriptor.Type.BLOCK_DATA, assocVolume.getStorageController(),
                        assocVolume.getId(), null, null);
                volumeDescriptors.add(assocDesc);
                // If there were any Block Mirrors, add a descriptors for them.
                addDescriptorsForMirrors(volumeDescriptors, assocVolume);
            }
        }
        return volumeDescriptors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool volumeVirtualPool,
            VirtualPool newVirtualPool, StringBuffer notSuppReasonBuff) {
        List<VirtualPoolChangeOperationEnum> allowedOperations = new ArrayList<VirtualPoolChangeOperationEnum>();

        if (VirtualPool.vPoolSpecifiesSRDF(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isSupportedSRDFVolumeVirtualPoolChange(volume, volumeVirtualPool,
                        newVirtualPool, _dbClient, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.SRDF_PROTECED);
        }

        return allowedOperations;
    }

}
