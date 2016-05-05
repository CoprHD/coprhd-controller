/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyUtils;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionUtils;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.DataObject.Flag;
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
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.migrationorchestrationcontroller.MigrationOrchestrationController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.collect.Table;

/*
 * Default implementation of the Migration Service Api.
 */
public class DefaultMigrationServiceApiImpl extends AbstractMigrationServiceApiImpl<StorageScheduler> {
    private static final Logger s_logger = LoggerFactory
            .getLogger(DefaultMigrationServiceApiImpl.class);

    @Autowired
    private final PermissionsHelper _permissionsHelper = null;

    // The max number of volumes allowed in a CG for varray and vpool
    // changes resulting in backend data migrations. Set in the API
    // service configuration file.
    private int _maxCgVolumesForMigration;

    private static final String MIGRATION_LABEL_SUFFIX = "m";

    /**
     * Public setter for Spring configuration.
     *
     * @param maxCgVols Max number of volumes allowed in a CG for varray and
     *            vpool changes resulting in backend data migrations
     */
    public void setMaxCgVolumesForMigration(int maxCgVols) {
        _maxCgVolumesForMigration = maxCgVols;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException {

        // The volume cannot be exported
        if (volume.isVolumeExported(_dbClient)) {
            s_logger.info("The volume is exported.");
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
     * {@inheritDoc}
     */
    @Override
    public void changeVolumesVirtualArray(List<Volume> volumes,
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
            MigrationOrchestrationController controller = getController(
                    MigrationOrchestrationController.class,
                    MigrationOrchestrationController.MIGRATION_ORCHESTRATION_DEVICE);
            controller.changeVirtualArray(descriptors, taskId);
            s_logger.info("Successfully invoked migration orchestrator.");
        } catch (InternalException e) {
            s_logger.error("Controller error", e);
            for (VolumeDescriptor descriptor : descriptors) {
                // Make sure to clean up the tasks associated with the
                // migration targets and migrations.
                if (VolumeDescriptor.Type.MIGRATE_VOLUME.equals(descriptor.getType())) {
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
    private void changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool vpool,
            VolumeVirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        VirtualPool volumeVirtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        s_logger.info("Volume {} VirtualPool change.", volume.getId());

        String transferSpeed = null;
        ArrayList<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);

        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }

        // Prepare for volume VirtualPool change.
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        boolean isHostMigration = vpoolChangeParam.getIsHostMigration();
        URI migrationHostURI = vpoolChangeParam.getMigrationHost();
        s_logger.info("VirtualPool change for volume.");
        List<VolumeDescriptor> descriptors = createChangeVirtualPoolDescriptors(storageSystem, volume, vpool,
                isHostMigration, migrationHostURI, taskId, null, null);

        // Now we get the Orchestration controller and use it to change the virtual pool of the volumes.
        orchestrateVPoolChanges(Arrays.asList(volume), descriptors, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeVolumesVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VolumeVirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {

        // Check for common Vpool updates handled by generic code. It returns true if handled.
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }

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
                        s_logger.info("All volumes should be in CG {}:{}", cgURI, cg.getLabel());
                    cgVolumes.addAll(getActiveCGVolumes(cg));
                } else {
                    s_logger.info("No volumes should be in CGs");
                    foundVolumeNotInCG = true;
                }
            } else if (((cg != null) && (NullColumnValueGetter.isNullURI(cgURI))) ||
                       ((foundVolumeNotInCG) && (!NullColumnValueGetter.isNullURI(cgURI)))) {
                // A volume was in a CG, so all volumes must be in a CG.
                if (cg != null) {
                    // Volumes should all be in the CG and this one is not.
                    s_logger.error("Volume {}:{} is not in the CG", volume.getId(), volume.getLabel());
                } else {
                    s_logger.error("Volume {}:{} is in CG {}", new Object[] { volume.getId(),
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
                s_logger.info("Vpool change is a data migration");

                // If the volumes are in a CG veryfy that they are
                // all in the same CG and all volumes are passed.
                if (cg != null) {
                    s_logger.info("Verify all volumes in CG {}:{}", cg.getId(), cg.getLabel());
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
                    s_logger.info("Multiple volume request, verifying target storage systems");
                    verifyTargetSystemsForCGDataMigration(cgVolumes, vpool, cg.getVirtualArray());
                }

                // Get all volume descriptors for all volumes to be migrated.
                URI systemURI = changeVPoolVolume.getStorageController();
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
                List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
                URI migrationHostURI = vpoolChangeParam.getMigrationHost();
                boolean isHostMigration = vpoolChangeParam.getIsHostMigration();
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
            changeVolumeVirtualPool(volume.getStorageController(), volume, vpool,
                    vpoolChangeParam, taskId);
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
            MigrationOrchestrationController controller = getController(
                    MigrationOrchestrationController.class,
                    MigrationOrchestrationController.MIGRATION_ORCHESTRATION_DEVICE);
            controller.changeVirtualPool(descriptors, taskId);
        } catch (InternalException e) {
            if (s_logger.isErrorEnabled()) {
                s_logger.error("Controller error", e);
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
     * Creates the volumes descriptors for a varray change for the passed
     * list of volumes.
     *
     * @param volumes The volumes being moved.
     * @param tgtVarray The target virtual array.
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

            // We'll need to prepare a target volume and create a
            // descriptor for each backend volume being migrated.
            StringSet assocVolumes = volume.getAssociatedVolumes();
            String assocVolumeId = assocVolumes.iterator().next();
            URI assocVolumeURI = URI.create(assocVolumeId);
            Volume assocVolume = _dbClient.queryObject(Volume.class, assocVolumeURI);
            VirtualPool assocVolumeVPool = _dbClient.queryObject(VirtualPool.class,
                    assocVolume.getVirtualPool());
            descriptors.addAll(createBackendVolumeMigrationDescriptors(storageSystem,
                    volume, assocVolume, tgtVarray, assocVolumeVPool,
                    getVolumeCapacity(assocVolume), isHostMigration, migrationHostURI,
                    taskId, null, null));
        }

        return descriptors;
    }

    /**
     * Change the VirtualPool for the passed virtual volume on the passed VPlex
     * storage system.
     *
     * @param vplexSystem A reference to the VPlex storage system.
     * @param volume A reference to the virtual volume.
     * @param newVpool The desired VirtualPool.
     * @param isHostMigration Boolean describing if the migration is host-based or not.
     * @param migrationHostURI The URI for the migration host in host-based migrations.
     * @param taskId The task identifier.
     *
     * @throws InternalException
     */
    protected List<VolumeDescriptor> createChangeVirtualPoolDescriptors(StorageSystem vplexSystem, Volume volume,
            VirtualPool newVpool, boolean isHostMigration, URI migrationHostURI, String taskId,
            List<Recommendation> recommendations, VirtualPoolCapabilityValuesWrapper capabilities)
    throws InternalException {
        // Get the varray and current vpool for the virtual volume.
        URI volumeVarrayURI = volume.getVirtualArray();
        VirtualArray volumeVarray = _dbClient.queryObject(VirtualArray.class, volumeVarrayURI);
        s_logger.info("Virtual volume varray is {}", volumeVarrayURI);
        URI volumeVpoolURI = volume.getVirtualPool();
        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, volumeVpoolURI);

        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();

        // Add the Volume Descriptor for change vpool
        VolumeDescriptor volumeDesc = new VolumeDescriptor(VolumeDescriptor.Type.GENERAL_VOLUME,
                volume.getStorageController(),
                volume.getId(),
                volume.getPool(), null);

        Map<String, Object> volumeParams = new HashMap<String, Object>();
        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID, volume.getId());
        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID, newVpool.getId());
        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_OLD_VPOOL_ID, volume.getVirtualPool());
        volumeDesc.setParameters(volumeParams);
        descriptors.add(volumeDesc);

        // A VirtualPool change on a VPlex virtual volume requires
        // a migration of the data on the backend volume(s) used by
        // the virtual volume to new volumes that satisfy the new
        // new VirtualPool. So we need to get the placement
        // recommendations for the new volumes to which the data
        // will be migrated and prepare the volume(s). First
        // determine if the backend volume on the source side,
        // i.e., the backend volume in the same varray as the
        // vplex volume. Recall for ingested volumes, we know
        // nothing about the backend volumes.
        if (VirtualPoolChangeAnalyzer.vpoolChangeRequiresMigration(currentVpool, newVpool)) {
            Volume migSrcVolume = getAssociatedVolumeInVArray(volume, volumeVarrayURI);
            descriptors.addAll(createBackendVolumeMigrationDescriptors(vplexSystem, volume,
                    migSrcVolume, volumeVarray, newVpool, getVolumeCapacity(migSrcVolume != null ? migSrcVolume : volume),
                    isHostMigration, migrationHostURI, taskId, recommendations, false, capabilities));
        }

        return descriptors;
    }

    /**
     * Does the work necessary to prepare the passed backend volume for the
     * passed virtual volume to be migrated to a new volume with a new VirtualPool.
     *
     * @param storageSystem A reference to the storage system.
     * @param virtualVolume A reference to the virtual volume.
     * @param sourceVolume A reference to the backend volume to be migrated.
     * @param varray A reference to the varray for the backend volume.
     * @param vpool A reference to the VirtualPool for the new volume.
     * @param capacity The capacity for the migration target.
     * @param taskId The task identifier.
     * @param newVolumes An OUT parameter to which the new volume is added.
     * @param migrationMap A OUT parameter to which the new migration is added.
     * @param poolVolumeMap An OUT parameter associating the new Volume to the
     *            storage pool in which it will be created.
     */
    private List<VolumeDescriptor> createBackendVolumeMigrationDescriptors(StorageSystem storageSystem,
            Volume virtualVolume, Volume sourceVolume, VirtualArray varray, VirtualPool vpool,
            Long capacity, boolean isHostMigration, URI migrationHostURI, String taskId,
            List<VolumeRecommendation> recommendations, VirtualPoolCapabilityValuesWrapper capabilities) {

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
            s_logger.info("Got recommendation");
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

        s_logger.info("Prepared volume {}", targetVolume.getId());

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
        // passed virtual volume and add the migration to the passed
        // migrations list.
        Migration migration = prepareMigration(virtualVolume.getId(),
                sourceVolumeURI, targetVolumeURI, isHostMigration,
                migrationHostURI, taskId);

        descriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.MIGRATE_VOLUME,
                targetStorageSystem,
                targetVolumeURI,
                targetStoragePool,
                cgURI,
                migration.getId(),
                capabilities));

        s_logger.info("Prepared migration {}.", migration.getId());

        return descriptors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyDriverCapabilities(URI sourceStorageSystemURI, URI targetStorageSystemURI)
            throws InternalException {
        // Not yet implemented
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
     * Prepares a migration for the passed volume specifying the source
     * and target volumes for the migration, as well as if the migration
     * will be driver assisted or not.
     *
     * @param virtualVolumeURI The URI of the virtual volume.
     * @param sourceURI The URI of the source volume for the migration.
     * @param targetURI The URI of the target volume for the migration.
     * @param driverMigration Boolean describing whether or not this will be a driver assisted migration.
     * @param token The task identifier.
     *
     * @return A reference to a newly created Migration.
     */
    public Migration prepareMigration(URI virtualVolumeURI, URI sourceURI, URI targetURI,
            boolean isHostMigration, URI migrationHostURI, String token) {
        Migration migration = new Migration();
        migration.setId(URIUtil.createId(Migration.class));
        migration.setVolume(virtualVolumeURI);
        migration.setSource(sourceURI);
        migration.setTarget(targetURI);
        migration.setIsHostMigration(isHostMigration);
        migration.setMigrationHost(migrationHostURI);
        _dbClient.createObject(migration);
        migration.setOpStatus(new OpStatusMap());
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(),
                token, ResourceOperationTypeEnum.MIGRATE_BLOCK_VOLUME);
        migration.getOpStatus().put(token, op);
        _dbClient.updateObject(migration);

        return migration;
    }
}
