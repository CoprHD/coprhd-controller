/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toCompletedTask;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyUtils;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ApplicationAddVolumeList;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Block Service subtask (parts of larger operations) default implementation.
 */
public class DefaultBlockServiceApiImpl extends AbstractBlockServiceApiImpl<StorageScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(DefaultBlockServiceApiImpl.class);
    
    private static final String MIGRATION_LABEL_SUFFIX = "m";
    
    @Autowired
    private final PermissionsHelper _permissionsHelper = null;

    // The max number of volumes allowed in a CG for varray and vpool
    // changes resulting in backend data migrations. Set in the API
    // service configuration file.
    private int _maxCgVolumesForMigration;

    /**
     * Public setter for Spring configuration.
     *
     * @param maxCgVols Max number of volumes allowed in a CG for varray and
     *            vpool changes resulting in backend data migrations
     */
    public void setMaxCgVolumesForMigration(int maxCgVols) {
        _maxCgVolumesForMigration = maxCgVols;
    }

    public DefaultBlockServiceApiImpl() {
        super(null);
    }

    private List<VolumeDescriptor> prepareVolumeDescriptors(List<Volume> volumes, VirtualPoolCapabilityValuesWrapper cosCapabilities) {

        // Build up a list of VolumeDescriptors based on the volumes
        final List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (Volume volume : volumes) {
            VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                    volume.getStorageController(), volume.getId(),
                    volume.getPool(), volume.getConsistencyGroup(), cosCapabilities);
            volumeDescriptors.add(desc);
        }

        return volumeDescriptors;
    }

    @Override
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray neighborhood,
            VirtualPool cos, Map<VpoolUse, List<Recommendation>> recommendationMap, TaskList taskList,
            String task, VirtualPoolCapabilityValuesWrapper cosCapabilities) throws InternalException {
        
        Long size = SizeUtil.translateSize(param.getSize());
        List<VolumeDescriptor> existingDescriptors = new ArrayList<VolumeDescriptor>();
        List<VolumeDescriptor> volumeDescriptors = createVolumesAndDescriptors(
                existingDescriptors, param.getName(), size, 
                project, neighborhood, cos, recommendationMap.get(VpoolUse.ROOT), taskList, task, cosCapabilities);
        List<Volume> preparedVolumes = getPreparedVolumes(volumeDescriptors);

        final BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);

        try {
            // Execute the volume creations requests
            controller.createVolumes(volumeDescriptors, task);
        } catch (InternalException e) {
            _log.error("Controller error when creating volumes", e);
            failVolumeCreateRequest(task, taskList, preparedVolumes, e.getMessage());
            throw e;
        } catch (Exception e) {
            _log.error("Controller error when creating volumes", e);
            failVolumeCreateRequest(task, taskList, preparedVolumes, e.getMessage());
            throw e;
        }

        return taskList;
    }

    @Override
    public List<VolumeDescriptor> createVolumesAndDescriptors(List<VolumeDescriptor> descriptors, String volumeLabel, Long size, Project project,
            VirtualArray varray, VirtualPool vpool, List<Recommendation> recommendations, TaskList taskList, String task,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities) {
        // Prepare the Bourne Volumes to be created and associated
        // with the actual storage system volumes created. Also create
        // a BlockTaskList containing the list of task resources to be
        // returned for the purpose of monitoring the volume creation
        // operation for each volume to be created.
        int volumeCounter = 0;
        List<Volume> preparedVolumes = new ArrayList<Volume>();
        final BlockConsistencyGroup consistencyGroup = vpoolCapabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                .queryObject(BlockConsistencyGroup.class, vpoolCapabilities.getBlockConsistencyGroup());

        // Prepare the volumes
        _scheduler.prepareRecommendedVolumes(size, task, taskList, project,
                varray, vpool, vpoolCapabilities.getResourceCount(), recommendations,
                consistencyGroup, volumeCounter, volumeLabel, preparedVolumes, vpoolCapabilities, false);

        // Prepare the volume descriptors based on the recommendations
        final List<VolumeDescriptor> volumeDescriptors = prepareVolumeDescriptors(preparedVolumes, vpoolCapabilities);

        // Log volume descriptor information
        logVolumeDescriptorPrecreateInfo(volumeDescriptors, task);
        
        return volumeDescriptors;
    }
    
    /**
     * Retrieves the preparedVolumes from the volume descriptors.
     * @param descriptors
     * @return List<Volume>
     */
    private List<Volume> getPreparedVolumes(List<VolumeDescriptor> descriptors) {
        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(descriptors);
        @SuppressWarnings("deprecation")
        List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
        return volumes;
    }

    private void failVolumeCreateRequest(String task, TaskList taskList, List<Volume> preparedVolumes, String errorMsg) {
        String errorMessage = String.format("Controller error: %s", errorMsg);
        for (TaskResourceRep volumeTask : taskList.getTaskList()) {
            volumeTask.setState(Operation.Status.error.name());
            volumeTask.setMessage(errorMessage);
            Operation statusUpdate = new Operation(Operation.Status.error.name(), errorMessage);
            _dbClient.updateTaskOpStatus(Volume.class, volumeTask.getResource()
                    .getId(), task, statusUpdate);
        }
        for (Volume volume : preparedVolumes) {
            volume.setInactive(true);
            _dbClient.persistObject(volume);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(final URI systemURI, final List<URI> volumeURIs,
            final String deletionType, final String task) throws InternalException {
        _log.info("Request to delete {} volume(s)", volumeURIs.size());
        super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
        // Call super first.
        super.cleanupForViPROnlyDelete(volumeDescriptors);

        // Clean up the relationship between volumes that are full
        // copies and and their source volumes.
        BlockFullCopyManager.cleanUpFullCopyAssociations(volumeDescriptors, _dbClient);
    }

    // Connectivity for a VNX/VMAX array is determined by the various protection systems.
    // This method calls all the known protection systems to find out which are covering
    // this StorageArray.
    @Override
    public StorageSystemConnectivityList getStorageSystemConnectivity(StorageSystem storageSystem) {
        Map<String, AbstractBlockServiceApiImpl> apiMap = AbstractBlockServiceApiImpl.getProtectionImplementations();
        StorageSystemConnectivityList result = new StorageSystemConnectivityList();
        for (AbstractBlockServiceApiImpl impl : apiMap.values()) {
            if (impl == this)
            {
                continue;     // no infinite recursion
            }
            StorageSystemConnectivityList list = impl.getStorageSystemConnectivity(storageSystem);
            result.getConnections().addAll(list.getConnections());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualPoolChangeList getVirtualPoolForVirtualPoolChange(Volume volume) {
        return getVirtualPoolChangeListForVolume(volume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool volumeVirtualPool,
            VirtualPool newVirtualPool, StringBuffer notSuppReasonBuff) {
        List<VirtualPoolChangeOperationEnum> allowedOperations = new ArrayList<VirtualPoolChangeOperationEnum>();

        if (VirtualPool.vPoolSpecifiesHighAvailability(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isVPlexImport(volume, volumeVirtualPool, newVirtualPool, notSuppReasonBuff) &&
                VirtualPoolChangeAnalyzer.doesVplexVpoolContainVolumeStoragePool(volume, newVirtualPool, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.NON_VPLEX_TO_VPLEX);
        }

        if (VirtualPool.vPoolSpecifiesProtection(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isSupportedAddRPProtectionVirtualPoolChange(volume,
                        volumeVirtualPool, newVirtualPool, _dbClient, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.RP_PROTECTED);
        }

        if (VirtualPool.vPoolSpecifiesSRDF(newVirtualPool) &&
                VirtualPoolChangeAnalyzer.isSupportedSRDFVolumeVirtualPoolChange(volume,
                        volumeVirtualPool, newVirtualPool, _dbClient, notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.SRDF_PROTECED);
        }

        if (VirtualPool.vPoolSpecifiesMirrors(newVirtualPool, _dbClient)
                &&
                VirtualPoolChangeAnalyzer.isSupportedAddMirrorsVirtualPoolChange(volume, volumeVirtualPool, newVirtualPool, _dbClient,
                        notSuppReasonBuff)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.ADD_MIRRORS);
        }

        if (VirtualPoolChangeAnalyzer.vpoolChangeRequiresMigration(volumeVirtualPool, newVirtualPool)) {
            allowedOperations.add(VirtualPoolChangeOperationEnum.DATA_MIGRATION);
        }

        return allowedOperations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException {

        // The volume cannot be exported
        if (volume.isVolumeExported(_dbClient)) {
            _log.info("The volume is exported.");
            throw APIException.badRequests.changesNotSupportedFor("VirtualArray", "exported volumes");
        }

        // If the vpool has assigned varrays, the vpool must be assigned
        // to the new varray.
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        StringSet vpoolVarrayIds = vpool.getVirtualArrays();
        if ((vpoolVarrayIds == null) || (!vpoolVarrayIds.contains(newVarray.getId().toString()))) {
            throw APIException.badRequests.vpoolNotAssignedToVarrayForVarrayChange(
                    vpool.getLabel(), volume.getLabel());
        }

        // The volume must be detached from all full copies.
        if (BlockFullCopyUtils.volumeHasFullCopySession(volume, _dbClient)) {
            throw APIException.badRequests.volumeForVarrayChangeHasFullCopies(volume.getLabel());
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeVirtualArrayForVolumes(List<Volume> volumes,
            BlockConsistencyGroup cg, List<Volume> cgVolumes, VirtualArray tgtVarray,
            boolean isHostMigration, URI migrationHostURI, String taskId) throws InternalException {

        // Since the backend volume would change and snapshots are just
        // snapshots of the backend volume, the user would lose all snapshots
        // if we allowed the varray change. Therefore, we don't allow the varray
        // change if the volume has snapshots. The user would have to explicitly
        // go and delete their snapshots first.
        //
        // Note: We make this validation here instead of in the verification
        // method "verifyVarrayChangeSupportedForVolumeAndVarray" because the
        // verification method is called from not only the API to change the
        // volume virtual array, but also the API that determines the volumes
        // that would be eligible for a proposed varray change. The latter API
        // is used by the UI to populate the list of volumes. We want volumes
        // with snaps to appear in the list, so that the user will know that
        // if they remove the snapshots, they can perform the varray change.
        for (Volume volume : volumes) {
            List<BlockSnapshot> snapshots = getSnapshots(volume);
            if (!snapshots.isEmpty()) {
                for (BlockSnapshot snapshot : snapshots) {
                    if (!snapshot.getInactive()) {
                        throw APIException.badRequests.volumeForVarrayChangeHasSnaps(volume.getId().toString());
                    }
                }
            }
            // If the volume has mirrors then varray change will not
            // be allowed. User needs to explicitly delete mirrors first.
            StringSet mirrorURIs = volume.getMirrors();
            if (mirrorURIs != null && !mirrorURIs.isEmpty()) {
                List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, StringSetUtil.stringSetToUriList(mirrorURIs));
                if (mirrors != null && !mirrors.isEmpty()) {
                    throw APIException.badRequests
                        .volumeForVarrayChangeHasMirrors(volume.getId().toString(), volume.getLabel());
                }
            }
        }

        // All volumes will be migrated in the same workflow.
        // If there are many volumes in the CG, the workflow
        // will have many steps. Worse, if an error occurs
        // additional workflow steps get added for rollback.
        // An issue can then arise trying to persist the
        // workflow to Zoo Keeper because the workflow
        // persistence is restricted to 250000 bytes. So
        // we add a restriction on the number of volumes in
        // the CG that will be allowed for a data migration
        if ((cg != null) && (volumes.size() > _maxCgVolumesForMigration)) {
            throw APIException.badRequests
                    .cgContainsTooManyVolumesForVArrayChange(cg.getLabel(),
                            volumes.size(), _maxCgVolumesForMigration);
        }

        // Varray changes for volumes in CGs will always migrate all
        // volumes in the CG. If there are multiple volumes in the CG
        // and the CG has associated backend CGs then we need to ensure
        // that when the migration targets are created for these volumes
        // they are placed on the same backend storage system in the new
        // varray. Therefore, we ensure this is the case by verifying that
        // the valid storage pools for the volume's vpool that are tagged
        // to the new varray resolve to a single storage system. If not
        // we don't allow the varray change.
        if ((cg != null) && (cg.checkForType(Types.LOCAL)) && (cgVolumes.size() > 1)) {
            verifyTargetSystemsForCGDataMigration(volumes, null, tgtVarray.getId());
        }

        // Create the volume descriptors for the virtual array change.
        List<VolumeDescriptor> descriptors = createVolumeDescriptorsForVarrayChange(
                volumes, tgtVarray, isHostMigration, migrationHostURI, taskId);

        try {
            // Orchestrate the virtual array change.
            //MigrationOrchestrationController controller = getController(
            //        MigrationOrchestrationController.class,
            //        MigrationOrchestrationController.MIGRATION_ORCHESTRATION_DEVICE);
            //controller.changeVirtualArray(descriptors, taskId);
            _log.info("Successfully invoked migration orchestrator.");
        } catch (InternalException e) {
            _log.error("Controller error", e);
            for (VolumeDescriptor descriptor : descriptors) {
                // Make sure to clean up the tasks associated with the
                // migration targets and migrations.
                if (VolumeDescriptor.Type.HOST_MIGRATE_VOLUME.equals(descriptor.getType())) {
                    _dbClient.error(Volume.class, descriptor.getVolumeURI(), taskId, e);
                    _dbClient.error(Migration.class, descriptor.getMigrationId(), taskId, e);
                }
                if (VolumeDescriptor.Type.DRIVER_MIGRATE_VOLUME.equals(descriptor.getType())) {
                    _dbClient.error(Volume.class, descriptor.getVolumeURI(), taskId, e);
                    _dbClient.error(Migration.class, descriptor.getMigrationId(), taskId, e);
                }
            }
            throw e;
        }

    }

    /**
     * {@inheritDoc}
     *
     * @throws InternalException
     */
    @Override
    public TaskList changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        VirtualPool volumeVirtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        _log.info("Volume {} VirtualPool change.", volume.getId());

        String transferSpeed = null;
        ArrayList<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);

        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return null;
        }

        boolean isHostMigration = vpoolChangeParam.getIsHostMigration();
        URI migrationHostURI = vpoolChangeParam.getMigrationHost();

        // Prepare for volume VirtualPool change.
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        _log.info("VirtualPool change for volume.");
        List<VolumeDescriptor> descriptors = createChangeVirtualPoolDescriptors(storageSystem, volume, vpool,
                isHostMigration, migrationHostURI, taskId, null, null);

        // Now we get the Orchestration controller and use it to change the virtual pool of the volumes.
        orchestrateVPoolChanges(Arrays.asList(volume), descriptors, taskId);

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList changeVolumeVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {

        // Check for common Vpool updates handled by generic code. It returns true if handled.
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return null;
        }

        boolean isHostMigration = vpoolChangeParam.getIsHostMigration();
        URI migrationHostURI = vpoolChangeParam.getMigrationHost();
        Volume changeVPoolVolume = volumes.get(0);
        VirtualPool currentVPool = _dbClient.queryObject(VirtualPool.class, changeVPoolVolume.getVirtualPool());
        List<Volume> cgVolumes = new ArrayList<Volume>();
        BlockConsistencyGroup cg = null;
        boolean foundVolumeNotInCG = false;
        for (Volume volume : volumes) {
            URI cgURI = volume.getConsistencyGroup();
            if ((cg == null) && (!foundVolumeNotInCG)) {
                if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    cg = _permissionsHelper.getObjectById(cgURI, BlockConsistencyGroup.class);
                        _log.info("All volumes should be in CG {}:{}", cgURI, cg.getLabel());
                    cgVolumes.addAll(getActiveCGVolumes(cg));
                } else {
                    _log.info("No volumes should be in CGs");
                    foundVolumeNotInCG = true;
                }
            } else if (((cg != null) && (NullColumnValueGetter.isNullURI(cgURI))) ||
                       ((foundVolumeNotInCG) && (!NullColumnValueGetter.isNullURI(cgURI)))) {
                // A volume was in a CG, so all volumes must be in a CG.
                if (cg != null) {
                    // Volumes should all be in the CG and this one is not.
                    _log.error("Volume {}:{} is not in the CG", volume.getId(), volume.getLabel());
                } else {
                    _log.error("Volume {}:{} is in CG {}", new Object[] { volume.getId(),
                            volume.getLabel(), cgURI });
                }
                throw APIException.badRequests.cantChangeVpoolNotAllCGVolumes();
            }
        }

        if (cg != null) {
            VirtualPoolChangeOperationEnum vpoolChange = VirtualPoolChangeAnalyzer
                    .getSupportedVolumeVirtualPoolChangeOperation(changeVPoolVolume, currentVPool, vpool,
                            _dbClient, new StringBuffer());
            // If any of the volumes is in such a CG and if this is a data
            // migration of the volumes, then the volumes passed must be all
            // the volumes in the CG and only the volumes in the CG.
            if ((vpoolChange != null) && (vpoolChange == VirtualPoolChangeOperationEnum.DATA_MIGRATION)) {
                _log.info("Vpool change is a data migration");

                // If the volumes are in a CG veryfy that they are
                // all in the same CG and all volumes are passed.
                if (cg != null) {
                    _log.info("Verify all volumes in CG {}:{}", cg.getId(), cg.getLabel());
                    verifyVolumesInCG(volumes, cgVolumes);
                }

                // All volumes will be migrated in the same workflow.
                // If there are many volumes in the CG, the workflow
                // will have many steps. Worse, if an error occurs
                // additional workflow steps get added for rollback.
                // An issue can then arise trying to persist the
                // workflow to Zoo Keeper because the workflow
                // persistence is restricted to 250000 bytes. So
                // we add a restriction on the number of volumes in
                // the CG that will be allowed for a data migration
                // vpool change.
                if (cgVolumes.size() > _maxCgVolumesForMigration) {
                    throw APIException.badRequests
                            .cgContainsTooManyVolumesForVPoolChange(cg.getLabel(),
                                    volumes.size(), _maxCgVolumesForMigration);
                }

                // When migrating multiple volumes in the CG we
                // want to be sure the target vpool ensures the
                // migration targets will be placed on the same
                // storage system as the migration targets will
                // be placed in a CG on the target storage system.
                if (cgVolumes.size() > 1) {
                    _log.info("Multiple volume request, verifying target storage systems");
                    verifyTargetSystemsForCGDataMigration(cgVolumes, vpool, cg.getVirtualArray());
                }

                // Get all volume descriptors for all volumes to be migrated.
                URI systemURI = changeVPoolVolume.getStorageController();
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
                List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
                for (Volume volume : cgVolumes) {
                    descriptors.addAll(createChangeVirtualPoolDescriptors(storageSystem,
                            volume, vpool, isHostMigration, migrationHostURI, taskId, null, null));
                }

                // Orchestrate the vpool changes of all volumes as a single request.
                orchestrateVPoolChanges(cgVolumes, descriptors, taskId);
            }
        }

        // Otherwise proceed as we normally would performing
        // individual vpool changes for each volume.
        for (Volume volume : volumes) {
            changeVolumeVirtualPool(volume.getStorageController(), volume, vpool, vpoolChangeParam, taskId);
        }

        return null;
    }
    
    /**
     * Verifies that the passed volumes correspond to the passed volumes from
     * a consistency group.
     *
     * @param volumes The volumes to verify
     * @param cgVolumes The list of active volumes in a CG.
     */
    private void verifyVolumesInCG(List<Volume> volumes, List<Volume> cgVolumes) {
        // The volumes counts must match. If the number of volumes
        // is less, then not all volumes in the CG were passed.
        if (volumes.size() < cgVolumes.size()) {
            throw APIException.badRequests.cantChangeVarrayNotAllCGVolumes();
        }
    }

    /**
     * Invokes the block orchestrator for a vpool change operation.
     *
     * @param volumes The volumes undergoing the vpool change.
     * @param descriptors The prepared volume descriptors.
     * @param taskId The task identifier.
     */
    private void orchestrateVPoolChanges(List<Volume> volumes, List<VolumeDescriptor> descriptors, String taskId) {
        try {
            //MigrationOrchestrationController controller = getController(
            //        MigrationOrchestrationController.class,
            //        MigrationOrchestrationController.MIGRATION_ORCHESTRATION_DEVICE);
            //controller.changeVirtualPool(descriptors, taskId);
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("Controller error", e);
            }
            String errMsg = String.format("Controller error on changeVolumeVirtualPool: %s", e.getMessage());
            Operation statusUpdate = new Operation(Operation.Status.error.name(), errMsg);
            for (Volume volume : volumes) {
                _dbClient.updateTaskOpStatus(Volume.class, volume.getId(), taskId, statusUpdate);
            }
            throw e;
        }
    }

    /**
     * Verifies if the valid storage pools for the target vpool specify a single
     * target storage system.
     *
     * @param currentVPool The source vpool for a vpool change
     * @param newVPool The target vpool for a vpool change.
     * @param srcVarrayURI The virtual array for the volumes being migrated.
     */
    private void verifyTargetSystemsForCGDataMigration(List<Volume> volumes,
            VirtualPool newVPool, URI srcVarrayURI) {
        // Determine if the vpool change requires a migration. If the
        // new vpool is null, then this is a varray migration and the
        // varray is changing. In either case, the valid storage pools
        // specified in the target vpool that are tagged to the passed
        // varray must specify the same system so that all volumes are
        // placed on the same array.
        URI tgtSystemURI = null;
        for (Volume volume : volumes) {
            VirtualPool currentVPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            if ((newVPool == null) || (VirtualPoolChangeAnalyzer.vpoolChangeRequiresMigration(currentVPool, newVPool))) {
                VirtualPool vPoolToCheck = (newVPool == null ? currentVPool : newVPool);
                List<StoragePool> pools = VirtualPool.getValidStoragePools(vPoolToCheck, _dbClient, true);
                for (StoragePool pool : pools) {
                    // We only need to check the storage pools in the target vpool that
                    // are tagged to the virtual array for the volumes being migrated.
                    if (!pool.getTaggedVirtualArrays().contains(srcVarrayURI.toString())) {
                        continue;
                    }

                    if (tgtSystemURI == null) {
                        tgtSystemURI = pool.getStorageDevice();
                    } else if (!tgtSystemURI.equals(pool.getStorageDevice())) {
                        throw APIException.badRequests.targetVPoolDoesNotSpecifyUniqueSystem();
                    }
                }
            }
        }
    }

    /**
     * Creates the volumes descriptors for a varray change for the passed
     * list of volumes.
     *
     * @param volumes The volumes being moved.
     * @param tgtVarray The target virtual array.
     * @param isHostMigration Boolean describing if the migration is host-based or not.
     * @param migrationHostURI The URI for the migration host in host-based migrations.
     * @param taskId The task identifier.
     *
     * @return A list of volume descriptors
     */
    private List<VolumeDescriptor> createVolumeDescriptorsForVarrayChange(List<Volume> volumes,
            VirtualArray tgtVarray, boolean isHostMigration, URI migrationHostURI, String taskId) {

        // The list of descriptors for the virtual array change.
        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();

        // The storage system.
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                volumes.get(0).getStorageController());

        // Create a descriptor for each volume.
        for (Volume volume : volumes) {
            VolumeDescriptor descriptor = new VolumeDescriptor(
                    VolumeDescriptor.Type.BLOCK_DATA, volume.getStorageController(),
                    volume.getId(), null, null);
            Map<String, Object> descrParams = new HashMap<String, Object>();
            descrParams.put(VolumeDescriptor.PARAM_VARRAY_CHANGE_NEW_VAARAY_ID, tgtVarray.getId());
            descriptor.setParameters(descrParams);
            descriptors.add(descriptor);

            VirtualPool volumeVPool = _dbClient.queryObject(VirtualPool.class,
                    volume.getVirtualPool());
            descriptors.addAll(createBackendVolumeMigrationDescriptors(storageSystem,
                    volume, tgtVarray, volumeVPool, getVolumeCapacity(volume),
                    isHostMigration, migrationHostURI, taskId, null, null));
        }

        return descriptors;
    }

    /**
     * Change the VirtualPool for the passed virtual volume on the passed VPlex
     * storage system.
     *
     * @param storageSystem A reference to the storage system.
     * @param volume A reference to the virtual volume.
     * @param newVpool The desired VirtualPool.
     * @param isHostMigration Boolean describing if the migration is host-based or not.
     * @param migrationHostURI The URI for the migration host in host-based migrations.
     * @param taskId The task identifier.
     * @param recommendations Scheduling recommendations for new volume backend.
     * @param capabilities The virtual pool capabilities.
     *
     * @throws InternalException
     */
    protected List<VolumeDescriptor> createChangeVirtualPoolDescriptors(StorageSystem storageSystem, Volume volume,
            VirtualPool newVpool, boolean isHostMigration, URI migrationHostURI, String taskId,
            List<Recommendation> recommendations, VirtualPoolCapabilityValuesWrapper capabilities)
    throws InternalException {
        // Get the varray and current vpool for the virtual volume.
        URI volumeVarrayURI = volume.getVirtualArray();
        VirtualArray volumeVarray = _dbClient.queryObject(VirtualArray.class, volumeVarrayURI);
        _log.info("Virtual volume varray is {}", volumeVarrayURI);
        URI volumeVpoolURI = volume.getVirtualPool();
        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, volumeVpoolURI);

        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();

        // Add the Volume Descriptor for change vpool
        VolumeDescriptor volumeDesc = new VolumeDescriptor(VolumeDescriptor.Type.GENERAL_VOLUME,
                volume.getStorageController(),
                volume.getId(),
                volume.getPool(), null);

        Map<String, Object> volumeParams = new HashMap<String, Object>();
        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_EXISTING_VOLUME_ID, volume.getId());
        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID, newVpool.getId());
        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_OLD_VPOOL_ID, volume.getVirtualPool());
        volumeDesc.setParameters(volumeParams);
        descriptors.add(volumeDesc);

        if (VirtualPoolChangeAnalyzer.vpoolChangeRequiresMigration(currentVpool, newVpool)) {
            descriptors.addAll(createBackendVolumeMigrationDescriptors(storageSystem, volume,
                    volumeVarray, newVpool, getVolumeCapacity(volume), isHostMigration,
                    migrationHostURI, taskId, recommendations, capabilities));
        }

        return descriptors;
    }

    /**
     * Returns the backend volume of the passed VPLEX volume in the passed
     * virtual array.
     *
     * @param volume A reference to the volume.
     * @param varrayURI The URI of the virtual array.
     *
     * @return A reference to the backend volume for the passed volume in
     *         the passed virtual array, or null if the backend volumes are not
     *         known.
     */
    private Volume getAssociatedVolumeInVArray(Volume volume, URI varrayURI) {
        StringSet associatedVolumeIds = volume.getAssociatedVolumes();
        if (associatedVolumeIds != null) {
            for (String associatedVolumeId : associatedVolumeIds) {
                Volume associatedVolume = _dbClient.queryObject(Volume.class,
                        URI.create(associatedVolumeId));
                if (associatedVolume.getVirtualArray().equals(varrayURI)) {
                    return associatedVolume;
                }
            }
        }
        return null;
    }

    /**
     * Does the work necessary to prepare the passed backend volume for the
     * passed virtual volume to be migrated to a new volume with a new VirtualPool.
     *
     * @param storageSystem A reference to the storage system.
     * @param sourceVolume A reference to the backend volume to be migrated.
     * @param varray A reference to the varray for the backend volume.
     * @param vpool A reference to the VirtualPool for the new volume.
     * @param capacity The capacity for the migration target.
     * @param isHostMigration Boolean describing if the migration will be host or driver based.
     * @param migrationHostURI URI of the migration host.
     * @param taskId The task identifier.
     * @param recommendations Scheduling recommendations for new volume backend.
     * @param capabilities The virtual pool capabilities.
     * @param newVolumes An OUT parameter to which the new volume is added.
     * @param migrationMap A OUT parameter to which the new migration is added.
     * @param poolVolumeMap An OUT parameter associating the new Volume to the
     *            storage pool in which it will be created.
     */
    private List<VolumeDescriptor> createBackendVolumeMigrationDescriptors(StorageSystem storageSystem,
            Volume sourceVolume, VirtualArray varray, VirtualPool vpool, Long capacity, boolean isHostMigration,
            URI migrationHostURI, String taskId, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        URI sourceVolumeURI = null;
        Project targetProject = null;
        String targetLabel = null;
        // Ideally we would just give the new backend volume the same
        // name as the current i.e., source. However, this is a problem
        // if the migration is on the same array. We can't have two volumes
        // with the same name. Eventually the source will go away, but not
        // until after the migration is completed. The backend volume names
        // are basically irrelevant, but we still want them tied to
        // the volume name.
        //
        // The volume have an additional suffix of "-<1...N>" if the
        // volume was created as part of a multi-volume creation
        // request, where N was the number of volumes requested. When
        // a volume is first migrated, we will append a "m" to the current
        // source volume name to ensure name uniqueness. If the volume
        // happens to be migrated again, we'll remove the extra character.
        // We'll go back forth in this manner for each migration of that
        // backend volume.
        sourceVolumeURI = sourceVolume.getId();
        targetProject = _dbClient.queryObject(Project.class, sourceVolume.getProject().getURI());
        targetLabel = sourceVolume.getLabel();
        if (!targetLabel.endsWith(MIGRATION_LABEL_SUFFIX)) {
            targetLabel += MIGRATION_LABEL_SUFFIX;
        } else {
            targetLabel = targetLabel.substring(0, targetLabel.length() - 1);
        }

        // Get the recommendation for this volume placement.
        Set<URI> requestedStorageSystems = new HashSet<URI>();
        requestedStorageSystems.add(storageSystem.getId());

        URI cgURI = null;
        // Check to see if the VirtualPoolCapabilityValuesWrapper have been passed in, if not, create a new one.
        if (capabilities != null) {
            // The consistency group or null when not specified.
            final BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                    .queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());

            // If the consistency group is created but does not specify the LOCAL
            // type, the CG must be a CG created prior to 2.2 or an ingested CG. In
            // this case, we don't want a volume creation to result in backend CGs
            if ((consistencyGroup != null) && ((!consistencyGroup.created()) ||
                    (consistencyGroup.getTypes().contains(Types.LOCAL.toString())))) {
                cgURI = consistencyGroup.getId();
            }
        } else {
            capabilities = new VirtualPoolCapabilityValuesWrapper();
            capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, capacity);
            capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        }

        boolean premadeRecs = false;

        if (recommendations == null || recommendations.isEmpty()) {
            recommendations = getBlockScheduler().getRecommendationsForResources(
                    varray, targetProject, vpool, capabilities);
            if (recommendations.isEmpty()) {
                throw APIException.badRequests.noStorageFoundForVolumeMigration(vpool.getLabel(), varray.getLabel(), sourceVolumeURI);
            }
            _log.info("Got recommendation");
        } else {
            premadeRecs = true;
        }

        // Get target storage system and pool
        URI targetStorageSystem = recommendations.get(0).getSourceStorageSystem();
        URI targetStoragePool = recommendations.get(0).getSourceStoragePool();

        // If a driver assisted migration was requested, verify that the driver can handle
        // the migration.
        if (!isHostMigration) {
            verifyDriverCapabilities(sourceVolume.getStorageController(), targetStorageSystem);
        }
        // Create a volume for the new backend volume to which
        // data will be migrated.
        Volume targetVolume = prepareVolumeForRequest(capacity,
                targetProject, varray, vpool, targetStorageSystem, targetStoragePool,
                targetLabel, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME,
                taskId, _dbClient);

        // If the cgURI is null, try and get it from the source volume.
        if (cgURI == null) {
            if ((sourceVolume != null) && (!NullColumnValueGetter.isNullURI(sourceVolume.getConsistencyGroup()))) {
                cgURI = sourceVolume.getConsistencyGroup();
                targetVolume.setConsistencyGroup(cgURI);
            }
        }

        if ((sourceVolume != null) && NullColumnValueGetter.isNotNullValue(sourceVolume.getReplicationGroupInstance())) {
            targetVolume.setReplicationGroupInstance(sourceVolume.getReplicationGroupInstance());
        }

        targetVolume.addInternalFlags(Flag.INTERNAL_OBJECT);
        _dbClient.updateObject(targetVolume);

        _log.info("Prepared volume {}", targetVolume.getId());

        // Add the volume to the passed new volumes list and pool
        // volume map.
        URI targetVolumeURI = targetVolume.getId();

        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();

        descriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                targetStorageSystem,
                targetVolumeURI,
                targetStoragePool,
                cgURI,
                capabilities,
                capacity));

        // Create a migration to represent the migration of data
        // from the backend volume to the new backend volume for the
        // passed volume and add the migration to the passed
        // migrations list.
        Migration migration = prepareMigration(sourceVolumeURI,
                targetVolumeURI, isHostMigration, migrationHostURI, taskId);

        if (isHostMigration) {
            descriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.HOST_MIGRATE_VOLUME,
                targetStorageSystem,
                targetVolumeURI,
                targetStoragePool,
                cgURI,
                migration.getId(),
                capabilities));
        } else {
            descriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.DRIVER_MIGRATE_VOLUME,
                targetStorageSystem,
                targetVolumeURI,
                targetStoragePool,
                cgURI,
                migration.getId(),
                capabilities));
        }

        _log.info("Prepared migration {}.", migration.getId());

        return descriptors;
    }

    /**
     * Verify that the storage systems provided support assisted volume migration
     *
     * @param sourceStorageSystemURI The URI of the source storage system
     * @param targetStorageSystemURI The URI of the target storage system
     */
    private void verifyDriverCapabilities(URI sourceStorageSystemURI, URI targetStorageSystemURI)
            throws InternalException {
        // Not yet implemented
        return;
    }

    /**
     * Gets the target volume's capacity.
     *
     * @param volume The volume to get the capacity of.
     */
    public Long getVolumeCapacity(Volume volume) {
        Long userRequestedCapacity = volume.getCapacity();
        Long provisionedCapacity = volume.getProvisionedCapacity();
        if (provisionedCapacity > userRequestedCapacity) {
            return provisionedCapacity;
        }

        return userRequestedCapacity;
    }

    /**
     * Prepare a new Bourne volume.
     *
     * @param size The volume size.
     * @param project A reference to the volume's Project.
     * @param varray A reference to the volume's varray.
     * @param vpool A reference to the volume's VirtualPool.
     * @param storageSystemURI The URI of the volume's storage system.
     * @param storagePoolURI The URI of the volume's storage pool.
     * @param label The volume label.
     * @param token The task id for volume creation.
     * @param dbClient A reference to a database client.
     *
     * @return A reference to the new volume.
     */
    public static Volume prepareVolumeForRequest(Long size, Project project,
            VirtualArray varray, VirtualPool vpool, URI storageSystemURI,
            URI storagePoolURI, String label, ResourceOperationTypeEnum opType,
            String token, DbClient dbClient) {
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(label);
        volume.setCapacity(size);
        volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString()
                .equalsIgnoreCase(vpool.getSupportedProvisioningType()));
        volume.setVirtualPool(vpool.getId());
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setVirtualArray(varray.getId());
        StoragePool storagePool = null;
        storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
        volume.setProtocol(new StringSet());
        volume.getProtocol().addAll(
                VirtualPoolUtil.getMatchingProtocols(vpool.getProtocols(), storagePool.getProtocols()));
        volume.setStorageController(storageSystemURI);
        volume.setPool(storagePoolURI);
        volume.setOpStatus(new OpStatusMap());

        // Set the auto tiering policy.
        if (null != vpool.getAutoTierPolicyName()) {
            URI autoTierPolicyUri = StorageScheduler.getAutoTierPolicy(storagePoolURI,
                    vpool.getAutoTierPolicyName(), dbClient);
            if (null != autoTierPolicyUri) {
                volume.setAutoTieringPolicyUri(autoTierPolicyUri);
            }
        }

        if (opType != null) {
            Operation op = new Operation();
            op.setResourceType(opType);
            volume.getOpStatus().createTaskStatus(token, op);
        }

        dbClient.createObject(volume);

        return volume;
    }

    /**
     * Prepares a migration for the passed volume specifying the source
     * and target volumes for the migration, as well as if the migration
     * will be driver assisted or not.
     *
     * @param sourceURI The URI of the source volume for the migration.
     * @param targetURI The URI of the target volume for the migration.
     * @param isHostMigration Boolean describing if the migration will be host or driver based.
     * @param migrationHostURI The URI of the migration host.
     * @param token The task identifier.
     *
     * @return A reference to a newly created Migration.
     */
    public Migration prepareMigration(URI sourceURI, URI targetURI,
            boolean isHostMigration, URI migrationHostURI, String token) {
        Migration migration = new Migration();
        migration.setId(URIUtil.createId(Migration.class));
        migration.setVolume(sourceURI);
        migration.setSource(sourceURI);
        migration.setTarget(targetURI);
        if (isHostMigration) {
            migration.setMigrationHost(migrationHostURI);
        }
        _dbClient.createObject(migration);
        migration.setOpStatus(new OpStatusMap());
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(),
                token, ResourceOperationTypeEnum.MIGRATE_BLOCK_VOLUME);
        migration.getOpStatus().put(token, op);
        _dbClient.updateObject(migration);

        return migration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep deleteConsistencyGroup(StorageSystem device,
            BlockConsistencyGroup consistencyGroup, String task) throws ControllerException {

        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, consistencyGroup.getId(),
                task, ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP);

        BlockController controller = getController(BlockController.class,
                device.getSystemType());
        controller.deleteConsistencyGroup(device.getId(), consistencyGroup.getId(), Boolean.TRUE, task);

        return toTask(consistencyGroup, task, op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep updateConsistencyGroup(StorageSystem device,
            List<Volume> cgVolumes, BlockConsistencyGroup consistencyGroup,
            List<URI> addVolumesList, List<URI> removeVolumesList, String task)
            throws ControllerException {

        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class,
                consistencyGroup.getId(), task,
                ResourceOperationTypeEnum.UPDATE_CONSISTENCY_GROUP);

        if (!device.getSystemType().equals(DiscoveredDataObject.Type.scaleio.name())) {
            BlockController controller = getController(BlockController.class,
                    device.getSystemType());
            controller.updateConsistencyGroup(device.getId(), consistencyGroup.getId(),
                    addVolumesList, removeVolumesList, task);
            return toTask(consistencyGroup, task, op);
        } else {
            // ScaleIO does not have explicit CGs, so we can just update the database and complete
            Iterator<Volume> addVolumeItr = _dbClient.queryIterativeObjects(Volume.class, addVolumesList);
            List<Volume> addVolumes = new ArrayList<Volume>();
            while (addVolumeItr.hasNext()) {
                Volume volume = addVolumeItr.next();
                volume.setConsistencyGroup(consistencyGroup.getId());
                addVolumes.add(volume);
            }

            Iterator<Volume> removeVolumeItr = _dbClient.queryIterativeObjects(Volume.class, removeVolumesList);
            List<Volume> removeVolumes = new ArrayList<Volume>();
            while (removeVolumeItr.hasNext()) {
                Volume volume = removeVolumeItr.next();
                volume.setConsistencyGroup(consistencyGroup.getId());
                removeVolumes.add(volume);
            }

            _dbClient.updateObject(addVolumes);
            _dbClient.updateObject(removeVolumes);
            return toCompletedTask(consistencyGroup, task, op);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
            List<URI> volumeURIs, String deletionType) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (URI volumeURI : volumeURIs) {
            VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                    systemURI, volumeURI, null, null);
            volumeDescriptors.add(desc);
        }
        return volumeDescriptors;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws ControllerException
     */
    @Override
    public TaskResourceRep establishVolumeAndSnapshotGroupRelation(
            StorageSystem storageSystem, Volume sourceVolume,
            BlockSnapshot snapshot, String taskId) throws ControllerException {

        _log.info("START establish Volume and Snapshot group relation");
        // Create the task on the block snapshot
        Operation op = _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_VOLUME_SNAPSHOT);
        snapshot.getOpStatus().put(taskId, op);

        try {
            BlockController controller = getController(BlockController.class,
                    storageSystem.getSystemType());
            controller.establishVolumeAndSnapshotGroupRelation(storageSystem.getId(),
                    sourceVolume.getId(), snapshot.getId(), taskId);
        } catch (ControllerException e) {
            String errorMsg = String.format(
                    "Failed to establish group relation between volume group and snapshot group."
                            + "Source volume: %s, Snapshot: %s",
                    sourceVolume.getId(), snapshot.getId());
            _log.error(errorMsg, e);
            _dbClient.error(BlockSnapshot.class, snapshot.getId(), taskId, e);
        }

        return toTask(snapshot, taskId, op);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void updateVolumesInVolumeGroup(VolumeGroupVolumeList addVolumes, 
                                           List<Volume> removeVolumes,
                                           URI volumeGroupId,
                                           String taskId) {
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, volumeGroupId);
        ApplicationAddVolumeList addVolumeList = null;
        if (addVolumes != null && addVolumes.getVolumes() != null && !addVolumes.getVolumes().isEmpty()) {
            addVolumeList = addVolumesToApplication(addVolumes, volumeGroup, taskId);
        }

        if (removeVolumes != null && !removeVolumes.isEmpty()) {
            removeVolumesFromApplication(removeVolumes, volumeGroup, taskId);
        }

        // call controller to handle non application ready CG volumes
        if ((addVolumeList != null && !addVolumeList.getVolumes().isEmpty())) {
            List<URI> vols = addVolumeList.getVolumes();
            Volume firstVolume = _dbClient.queryObject(Volume.class, vols.get(0));
            URI systemURI = firstVolume.getStorageController();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
            BlockController controller = getController(BlockController.class, system.getSystemType());
            controller.updateApplication(systemURI, addVolumeList, volumeGroup.getId(), taskId);
        } else {
            // No need to call to controller. update the application task
            Operation op = volumeGroup.getOpStatus().get(taskId);
            op.ready();
            volumeGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(volumeGroup);
        }
    }

    /**
     * Update volumes with volumeGroup Id, if the volumes are application ready
     * (non VNX, or VNX volumes not in a real replication group)
     *
     * @param volumesList The add volume list
     * @param application The application that the volumes are added to
     * @param taskId
     * @return ApplicationVolumeList The volumes that are not application ready (in real VNX CG with array replication group)
     */
    private ApplicationAddVolumeList addVolumesToApplication(VolumeGroupVolumeList volumeList, VolumeGroup application, String taskId) {
        ApplicationAddVolumeList addVolumeList = new ApplicationAddVolumeList() ;

        Map<URI, List<URI>> addCGVolsMap = new HashMap<URI, List<URI>>();
        String newRGName = volumeList.getReplicationGroupName();
        for (URI voluri : volumeList.getVolumes()) {
            Volume volume = _dbClient.queryObject(Volume.class, voluri);
            if (volume == null || volume.getInactive()) {
                _log.info(String.format("The volume %s does not exist or has been deleted", voluri));
                continue;
            }

            URI cgUri = volume.getConsistencyGroup();
            if (!NullColumnValueGetter.isNullURI(cgUri)) {
                List<URI> vols = addCGVolsMap.get(cgUri);
                if (vols == null) {
                    vols = new ArrayList<URI>();
                }
                vols.add(voluri);
                addCGVolsMap.put(cgUri, vols);
            } else {
                // The volume is not in CG
                throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(),
                        String.format("The volume %s is not in a consistency group", volume.getLabel()));
            }

            String rgName = volume.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(rgName) && !rgName.equals(newRGName)) {
                throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(),
                        String.format("The volume %s is already in an array replication group, only the existing group name is allowed.", volume.getLabel()));
            }
        }

        Set<URI> appReadyCGUris = new HashSet<URI>();
        Set<Volume> appReadyCGVols = new HashSet<Volume>();
        Set<URI> nonAppReadyCGVolUris = new HashSet<URI>();
        // validate input volumes first, then batch processing, to avoid partial success
        for (Map.Entry<URI, List<URI>> entry : addCGVolsMap.entrySet()) {
            URI cgUri = entry.getKey();
            List<URI> cgVolsToAdd = entry.getValue();

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
            List<Volume> cgVolumes = getActiveCGVolumes(cg);
            Set<URI> cgVolumeURIs = new HashSet<URI>();
            for (Volume cgVol : cgVolumes) {
                cgVolumeURIs.add(cgVol.getId());
            }

            Volume firstVolume = _dbClient.queryObject(Volume.class, cgVolsToAdd.get(0));

            // Check if all CG volumes are adding into the application
            if (!cgVolumeURIs.containsAll(cgVolsToAdd) || cgVolsToAdd.size() != cgVolumeURIs.size()) {
                throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(firstVolume.getLabel(),
                        "not all volumes in consistency group are in the add volume list");
            }

            if (ControllerUtils.isVnxVolume(firstVolume, _dbClient) && !ControllerUtils.isNotInRealVNXRG(firstVolume, _dbClient)) {
                // VNX CG cannot have snapshots, user has to remove the snapshots first in order to add the CG to an application
                URIQueryResultList cgSnapshotsResults = new URIQueryResultList();
                _dbClient.queryByConstraint(getBlockSnapshotByConsistencyGroup(cgUri), cgSnapshotsResults);
                Iterator<URI> cgSnapshotsIter = cgSnapshotsResults.iterator();
                while (cgSnapshotsIter.hasNext()) {
                    BlockSnapshot cgSnapshot = _dbClient.queryObject(BlockSnapshot.class, cgSnapshotsIter.next());
                    if ((cgSnapshot != null) && (!cgSnapshot.getInactive())) {
                        throw APIException.badRequests.notAllowedWhenVNXCGHasSnapshot();
                    }
                }

                nonAppReadyCGVolUris.addAll(cgVolumeURIs);
            } else { // non VNX CG volume, or volume in VNX CG with no array replication group
                appReadyCGUris.add(cgUri);
                appReadyCGVols.addAll(cgVolumes);
            }
        }

        if (!appReadyCGVols.isEmpty()) {
            for (Volume cgVol : appReadyCGVols) {
                StringSet applications = cgVol.getVolumeGroupIds();
                applications.add(application.getId().toString());
                cgVol.setVolumeGroupIds(applications);

                // handle clones
                StringSet fullCopies = cgVol.getFullCopies();
                List<Volume> fullCopiesToUpdate = new ArrayList<Volume>();
                if (fullCopies != null && !fullCopies.isEmpty()) {
                    for (String fullCopyId : fullCopies) {
                        Volume fullCopy = _dbClient.queryObject(Volume.class, URI.create(fullCopyId));
                        if (fullCopy != null && !fullCopy.getInactive()) {
                            fullCopy.setFullCopySetName(fullCopy.getReplicationGroupInstance());
                            fullCopiesToUpdate.add(fullCopy);
                        }
                    }
                }

                if (!fullCopiesToUpdate.isEmpty()) {
                    _dbClient.updateObject(fullCopiesToUpdate);
                }

                Operation op = cgVol.getOpStatus().get(taskId);
                op.ready();
                cgVol.getOpStatus().updateTaskStatus(taskId, op);
            }

            _dbClient.updateObject(appReadyCGVols);
        }

        if (!appReadyCGUris.isEmpty()) {
            for (URI cgUri : appReadyCGUris) {
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                if (cg != null && !cg.getInactive()) {
                    cg.setArrayConsistency(false);
                    Operation op = cg.getOpStatus().get(taskId);
                    op.ready();
                    cg.getOpStatus().updateTaskStatus(taskId, op);
                    _dbClient.updateObject(cg);
                }
            }
        }

        addVolumeList.getVolumes().addAll(nonAppReadyCGVolUris);

        _log.info("Added volumes in CG to the application" );
        return addVolumeList;
    }

    /**
     * Remove volumes from application
     * @param removeVolumes Volumes to be removed
     * @param taskId
     * @param application The application that the volumes are removed from
     */
    private void removeVolumesFromApplication(List<Volume> removeVolumes, VolumeGroup application, String taskId) {
        Map<URI, List<URI>> cgVolsMap = new HashMap<URI, List<URI>>();
        for (Volume volume : removeVolumes) {
            URI cgUri = volume.getConsistencyGroup();
            if (!NullColumnValueGetter.isNullURI(cgUri)) {
                List<URI> vols = cgVolsMap.get(cgUri);
                if (vols == null) {
                    vols = new ArrayList<URI>();
                }
                vols.add(volume.getId());
                cgVolsMap.put(cgUri, vols);
            } else {
                // Shouldn't happen. The volume is not in CG
                throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(),
                        String.format("The volume %s is not in a consistency group", volume.getLabel()));
            }
        }

        for (Map.Entry<URI, List<URI>> entry : cgVolsMap.entrySet()) {
            URI cgUri = entry.getKey();
            List<URI> cgVolsToRemove = entry.getValue();

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
            List<Volume> cgVolumes = getActiveCGVolumes(cg);

            Set<URI> cgVolumeURIs = new HashSet<URI>();
            for (Volume cgVol : cgVolumes) {
                cgVolumeURIs.add(cgVol.getId());
            }

            // Check if all CG volumes are removing from the application
            Volume firstVolume = _dbClient.queryObject(Volume.class, cgVolsToRemove.get(0));
            if (!cgVolumeURIs.containsAll(cgVolsToRemove) || cgVolsToRemove.size() != cgVolumeURIs.size()) {
                throw APIException.badRequests.volumeCantBeRemovedFromVolumeGroup(firstVolume.getLabel(),
                        "not all volumes in consistency group are in the remove volume list");
            }

            for (Volume cgVol : cgVolumes) {
                StringSet applications = cgVol.getVolumeGroupIds();
                if (applications != null && !applications.isEmpty()) {
                    applications.remove(application.getId().toString());
                    cgVol.setVolumeGroupIds(applications);
                }

                // handle clones
                StringSet fullCopies = cgVol.getFullCopies();
                List<Volume> fullCopiesToUpdate = new ArrayList<Volume>();
                if (fullCopies != null && !fullCopies.isEmpty()) {
                    for (String fullCopyId : fullCopies) {
                        Volume fullCopy = _dbClient.queryObject(Volume.class, URI.create(fullCopyId));
                        if (fullCopy != null && !fullCopy.getInactive()) {
                            fullCopy.setFullCopySetName(NullColumnValueGetter.getNullStr());
                            fullCopiesToUpdate.add(fullCopy);
                        }
                    }
                }

                if (!fullCopiesToUpdate.isEmpty()) {
                    _dbClient.updateObject(fullCopiesToUpdate);
                }

                Operation op = cgVol.getOpStatus().get(taskId);
                op.ready();
                cgVol.getOpStatus().updateTaskStatus(taskId, op);
            }
            _dbClient.updateObject(cgVolumes);

            // update task status for CGs
            Operation op = cg.getOpStatus().get(taskId);
            op.ready();
            cg.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(cg);
        }

        _log.info("Removed volumes in CG from the application" );
    }

    /**
     * Creates tasks against consistency group associated with a request and adds them to the given task list.
     *
     * @param group
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    protected void addConsistencyGroupTask(URI groupUri, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        BlockConsistencyGroup group = _dbClient.queryObject(BlockConsistencyGroup.class, groupUri);
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
    }
    
    /**
     * Creates tasks against consistency groups associated with a request and adds them to the given task list.
     *
     * @param group
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    protected void addVolumeTask(Volume volume, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(volume, taskId, op));
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.BlockServiceApi#getReplicationGroupNames(com.emc.storageos.db.client.model.VolumeGroup)
     */
    @Override
    public Collection<? extends String> getReplicationGroupNames(VolumeGroup group) {
        List<String> groupNames = new ArrayList<String>();
        final List<Volume> volumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumesByVolumeGroupId(group.getId().toString()));
        for (Volume volume : volumes) {
            if (NullColumnValueGetter.isNotNullValue(volume.getReplicationGroupInstance())) {
                groupNames.add(volume.getReplicationGroupInstance());
            }
        }
        return groupNames;
    }
    
}
