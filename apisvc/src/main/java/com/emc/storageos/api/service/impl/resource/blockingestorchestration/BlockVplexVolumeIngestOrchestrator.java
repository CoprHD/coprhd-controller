/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.api.service.impl.resource.VPlexBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.VplexVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;
import com.google.common.base.Joiner;

/**
 * Responsible for ingesting vplex local and distributed virtual volumes.
 */
public class BlockVplexVolumeIngestOrchestrator extends BlockVolumeIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(BlockVplexVolumeIngestOrchestrator.class);

    // best practice two paths from each VPLEX director for paths in a backend export group
    private static final int DEFAULT_BACKEND_NUMPATHS = 4;

    // maps storage system URIs to StorageSystem objects
    private final Map<String, StorageSystem> _systemMap = new HashMap<String, StorageSystem>();

    // the tenants service, used to generate the Project for the backend volumes
    private TenantsService _tenantsService;

    public void setTenantsService(TenantsService tenantsService) {
        _tenantsService = tenantsService;
    }

    // the coordinator client, used to get system settings values
    private CoordinatorClient _coordinator;

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    @Override
    public <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException {

        refreshCaches(requestContext.getStorageSystem());

        UnManagedVolume unManagedVolume = requestContext.getCurrentUnmanagedVolume();

        VolumeIngestionUtil.checkValidVarrayForUnmanagedVolume(unManagedVolume,
                requestContext.getVarray().getId(),
                getClusterIdToNameMap(requestContext.getStorageSystem()),
                getVarrayToClusterIdMap(requestContext.getStorageSystem()), _dbClient);

        String vplexIngestionMethod = requestContext.getVplexIngestionMethod();
        _logger.info("VPLEX ingestion method is " + vplexIngestionMethod);
        boolean ingestBackend = (null == vplexIngestionMethod)
                || vplexIngestionMethod.isEmpty()
                || (!vplexIngestionMethod.equals(VplexBackendIngestionContext.INGESTION_METHOD_VVOL_ONLY));

        VplexVolumeIngestionContext volumeContext = null;

        if (ingestBackend) {

            volumeContext = (VplexVolumeIngestionContext) requestContext.getVolumeContext();
            volumeContext.setIngestionInProgress(true);

            //
            // If the "Only During Discovery" system setting is set, no new data will
            // be fetched during ingestion. This assumes that all data has been collected
            // during discovery and ingestion will fail if it can't find all the required data.
            //
            // If "Only During Ingestion" or "During Discovery and Ingestion" mode is set,
            // then an attempt will be made to query the VPLEX api now to find any incomplete data,
            // but the database will be checked first.
            //
            // The default mode is "Only During Discovery", so the user needs to remember
            // to run discovery first on all backend storage arrays before running on the VPLEX.
            //
            String discoveryMode = ControllerUtils.getPropertyValueFromCoordinator(
                    _coordinator, VplexBackendIngestionContext.DISCOVERY_MODE);
            if (VplexBackendIngestionContext.DISCOVERY_MODE_DISCOVERY_ONLY.equals(discoveryMode)) {
                volumeContext.setInDiscoveryOnlyMode(true);
            }

            // the backend volumes and export masks will be part of the VPLEX project
            // rather than the front-end virtual volume project, so we need to set that in the context
            Project vplexProject = VPlexBlockServiceApiImpl.getVplexProject(
                    requestContext.getStorageSystem(), _dbClient, _tenantsService);
            volumeContext.setBackendProject(vplexProject);
            volumeContext.setFrontendProject(requestContext.getProject());

            try {
                _logger.info("Ingesting backend structure of VPLEX virtual volume {}", unManagedVolume.getLabel());

                validateContext(requestContext.getVpool(), requestContext.getTenant(), volumeContext);

                ingestBackendVolumes(requestContext, volumeContext);

                ingestBackendExportMasks(requestContext, volumeContext);

            } catch (Exception ex) {
                _logger.error("error during VPLEX backend ingestion: ", ex);
                throw IngestionException.exceptions.failedToIngestVplexBackend(ex.getLocalizedMessage());
            }
        }

        _logger.info("Ingesting VPLEX virtual volume {}", unManagedVolume.getLabel());
        T virtualVolume = super.ingestBlockObjects(requestContext, clazz);

        return virtualVolume;
    }

    /**
     * Validate the VplexBackendIngestionContext against the
     * VirtualPool and Tenant.
     * 
     * @param vpool the target VirtualPool for ingestion
     * @param tenant the Tenant in use
     * @param context the VplexBackendIngestionContext
     * @throws IngestionException
     */
    private void validateContext(VirtualPool vpool,
            TenantOrg tenant, VplexBackendIngestionContext context) throws IngestionException {
        UnManagedVolume unManagedVirtualVolume = context.getUnmanagedVirtualVolume();
        List<UnManagedVolume> unManagedBackendVolumes = context.getUnmanagedBackendVolumes();

        _logger.info("validating the ingestion context for these backend volumes: " + unManagedBackendVolumes);

        _logger.info("checking if we have found enough backend volumes for ingestion");
        if ((context.isLocal() && (unManagedBackendVolumes.isEmpty()))
                || context.isDistributed() && (unManagedBackendVolumes.size() < 2)) {
            String supportingDevice = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(),
                    unManagedVirtualVolume.getVolumeInformation());
            if (unManagedBackendVolumes.isEmpty()) {
                String reason = "failed to find any VPLEX backend volume for UnManagedVolume "
                        + unManagedVirtualVolume.getLabel() + " with supporting device "
                        + supportingDevice + ".  Has the backend array been discovered for unmanaged volumes?";
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            } else {
                String reason = "failed to find all VPLEX backend volume for UnManagedVolume "
                        + unManagedVirtualVolume.getLabel() + " with supporting device "
                        + supportingDevice + ". Did find these backend volumes, though: "
                        + Joiner.on(", ").join(unManagedBackendVolumes)
                        + ".  Have all backend arrays been discovered for unmanaged volumes?";
                ;
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            }
        }

        // validate the supporting device structure is compatible with vipr
        context.validateSupportingDeviceStructure();

        // validate that there are not too many replicas on a distributed volume
        // as we can only support ingesting snaps or clones on one leg
        if (context.isDistributed()) {
            _logger.info("checking for presence of replicas on both legs of this distributed volume");

            // each entry in these collections is a concatenated list of replica names
            // on each backend volume, so if their size is more than one, that means there
            // are replicas present on both legs.
            // for example: all the backend volume name strings on leg 1 are concatenated,
            // added to snapshotsList at position 0. All the backend volumes on leg2 (if
            // present) are concatenated and added to snapshotsList at position 1. So,
            // if the size snapshotsList is greater than 1, we've got snaps on both legs.
            List<String> snapshotsList = new ArrayList<String>();
            List<String> clonesList = new ArrayList<String>();
            for (UnManagedVolume vol : unManagedBackendVolumes) {
                StringSet snapshots = VplexBackendIngestionContext.extractValuesFromStringSet(
                        SupportedVolumeInformation.SNAPSHOTS.name(), vol.getVolumeInformation());
                if (snapshots != null && !snapshots.isEmpty()) {
                    snapshotsList.add(Joiner.on(", ").join(snapshots));
                }
                StringSet clones = VplexBackendIngestionContext.extractValuesFromStringSet(
                        SupportedVolumeInformation.FULL_COPIES.name(), vol.getVolumeInformation());
                if (clones != null && !clones.isEmpty()) {
                    clonesList.add(Joiner.on(", ").join(clones));
                }
            }

            // build up an error message
            int counter = 0;
            StringBuilder message = new StringBuilder("");
            if (snapshotsList.size() > 1) {
                for (String snapshots : snapshotsList) {
                    if (counter > 0) {
                        message.append(" and");
                    }
                    message.append(" one distributed volume component has snapshots ").append(snapshots);
                    counter++;
                }
                counter = 0;
            }
            if (clonesList.size() > 1) {
                for (String clones : clonesList) {
                    if (counter > 0) {
                        message.append(" and");
                    }
                    message.append(" one distributed volume component has full copies ").append(clones);
                    counter++;
                }
            }
            if (message.length() > 0) {
                String reason = message.toString();
                _logger.error(reason);
                throw IngestionException.exceptions.vplexVolumeCannotHaveReplicasOnBothLegs(reason);
            }
        }

        for (UnManagedVolume vol : unManagedBackendVolumes) {
            _logger.info("checking for non native mirrors on backend volume " + vol.getNativeGuid());
            StringSet mirrors = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.MIRRORS.toString(), vol.getVolumeInformation());
            Iterator<String> mirrorator = mirrors.iterator();
            while (mirrorator.hasNext()) {
                String mirrorGuid = mirrorator.next();
                _logger.info("\tvolume has mirror " + mirrorGuid);
                for (Entry<UnManagedVolume, String> entry : context.getUnmanagedVplexMirrors().entrySet()) {
                    if (mirrorGuid.equals(entry.getKey().getNativeGuid())) {
                        _logger.info("\t\tbut it's native, so it's okay...");
                        mirrorator.remove();
                    }
                }
            }
            if (!mirrors.isEmpty()) {
                String reason = "cannot ingest a mirror on the backend array, "
                        + "only VPLEX device mirrors are supported. Mirrors found: "
                        + Joiner.on(", ").join(mirrors);
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            }
        }

        // validate that there are no array-based backend-only volume clones.
        // that is, if there's a clone, it should have a virtual volume in
        // front of it, otherwise, we can't ingest it.
        if (context.getUnmanagedBackendOnlyClones().size() > 0) {
            List<String> cloneInfo = new ArrayList<String>();
            for (Entry<UnManagedVolume, Set<UnManagedVolume>> cloneEntry : context.getUnmanagedBackendOnlyClones().entrySet()) {
                String message = cloneEntry.getKey().getLabel() + " has ";
                List<String> clones = new ArrayList<String>();
                for (UnManagedVolume clone : cloneEntry.getValue()) {
                    clones.add(clone.getLabel());
                }
                message += Joiner.on(", ").join(clones) + ". ";
                cloneInfo.add(message);
            }
            String reason = "cannot currently ingest a clone on the backend array "
                    + "that doesn't have a virtual volume in front of it. "
                    + "Backend-only clones found: "
                    + Joiner.on(", ").join(cloneInfo);
            _logger.error(reason);
            throw IngestionException.exceptions.validationException(reason);
        }

        int mirrorCount = context.getUnmanagedVplexMirrors().size();
        if (mirrorCount > 0) {
            _logger.info("{} native mirror(s) are present, validating vpool", mirrorCount);
            if (VirtualPool.vPoolSpecifiesMirrors(vpool, _dbClient)) {
                if (mirrorCount > vpool.getMaxNativeContinuousCopies()) {
                    if (context.isDistributed() && mirrorCount == 2) {
                        // there are two mirrors
                        // we need to check that they are on different clusters
                        List<UnManagedVolume> mirrors = new ArrayList<UnManagedVolume>();
                        for (UnManagedVolume mirror : context.getUnmanagedVplexMirrors().keySet()) {
                            mirrors.add(mirror);
                        }
                        if (mirrors.size() == 2) {
                            String backendClusterId0 = VplexBackendIngestionContext.extractValueFromStringSet(
                                    SupportedVolumeInformation.VPLEX_BACKEND_CLUSTER_ID.toString(),
                                    mirrors.get(0).getVolumeInformation());
                            String backendClusterId1 = VplexBackendIngestionContext.extractValueFromStringSet(
                                    SupportedVolumeInformation.VPLEX_BACKEND_CLUSTER_ID.toString(),
                                    mirrors.get(1).getVolumeInformation());
                            if (backendClusterId0.equals(backendClusterId1)) {
                                // the different clusters check failed
                                StringBuilder reason = new StringBuilder("the volume's mirrors must be on separate ");
                                reason.append(" vplex clusters. mirrors found: ");
                                reason.append(backendClusterId0).append(": ")
                                        .append(mirrors.get(0).getLabel()).append("; ")
                                        .append(backendClusterId1).append(": ")
                                        .append(mirrors.get(1).getLabel()).append(".");
                                String message = reason.toString();
                                _logger.error(message);
                                throw IngestionException.exceptions.validationException(message);
                            } else {
                                // a high availability vpool is required
                                VirtualPool haVpool = VirtualPool.getHAVPool(vpool, _dbClient);
                                if (haVpool == null) {
                                    String reason = "no high availability virtual pool is "
                                            + "set on source virtual pool " + vpool.getLabel();
                                    _logger.error(reason);
                                    throw IngestionException.exceptions.validationException(reason);
                                }
                                // max continuous copies needs to be set to one on both source and ha vpools
                                if (vpool.getMaxNativeContinuousCopies() == 1
                                        && haVpool.getMaxNativeContinuousCopies() == 1) {
                                    _logger.info("volume is distributed, has a mirror on each leg, both source and "
                                            + "high availaiblity vpools have continuous copies value of 1, "
                                            + "volume is ok for ingestion");
                                } else {
                                    StringBuilder reason = new StringBuilder("the virtual pools' continuous copy ");
                                    reason.append("settings are incorrect for ingesting a dual distributed mirror. ");
                                    reason.append("Source virtual pool is set to ")
                                            .append(vpool.getMaxNativeContinuousCopies())
                                            .append(" and target virtual pool is set to ")
                                            .append(haVpool.getMaxNativeContinuousCopies()).append(". ");
                                    reason.append("Mirrors found - ").append(backendClusterId0).append(": ")
                                            .append(mirrors.get(0).getLabel()).append("; ")
                                            .append(backendClusterId1).append(": ")
                                            .append(mirrors.get(1).getLabel()).append(".");
                                    String message = reason.toString();
                                    _logger.error(message);
                                    throw IngestionException.exceptions.validationException(message);
                                }
                            }
                        }
                    } else {
                        StringBuilder reason = new StringBuilder("volume has more continuous copies (");
                        reason.append(mirrorCount).append(" than vpool allows. Mirrors found: ");
                        reason.append(Joiner.on(", ").join(context.getUnmanagedVplexMirrors().keySet()));
                        String message = reason.toString();
                        _logger.error(message);
                        throw IngestionException.exceptions.validationException(message);
                    }
                }
            } else {
                String reason = "virtual pool does not allow continuous copies, but volume has " + mirrorCount + " mirror(s)";
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            }
        }

        int snapshotCount = context.getUnmanagedSnapshots().size();
        if (snapshotCount > 0) {
            _logger.info("{} snapshot(s) are present, validating vpool", snapshotCount);
            if (VirtualPool.vPoolSpecifiesSnapshots(vpool)) {
                if (snapshotCount > vpool.getMaxNativeSnapshots()) {
                    String reason = "volume has more snapshots (" + snapshotCount + ") than vpool allows";
                    _logger.error(reason);
                    throw IngestionException.exceptions.validationException(reason);
                }
            } else {
                String reason = "vpool does not allow snapshots, but volume has " + snapshotCount + " snapshot(s)";
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            }
        }

        long unManagedVolumesCapacity = VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(
                _dbClient, context.getUnmanagedBackendVolumeUris());
        _logger.info("validating total backend volume capacity {} against the vpool", unManagedVolumesCapacity);
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool,
                context.getBackendProject(), tenant, unManagedVolumesCapacity, "volume");

        _logger.info("validating backend volumes against the vpool");
        VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(
                context.getUnmanagedBackendVolumeUris(), vpool, _dbClient);

    }

    /**
     * Ingests the backend volumes and any related replicas.
     * 
     * Calls ingestBlockObjects by getting a nested IngestStrategy
     * for each backend volume or replica from the IngestStrategyFactory.
     * 
     * @param backendRequestContext the VplexBackendIngestionContext for the parent virtual volume
     * 
     * @throws IngestionException
     */
    private void ingestBackendVolumes(IngestionRequestContext requestContext,
            VplexVolumeIngestionContext backendRequestContext) throws IngestionException {

        while (backendRequestContext.hasNext()) {

            UnManagedVolume associatedVolume = backendRequestContext.next();

            String sourceClusterId = getClusterNameForVarray(backendRequestContext.getVarray(), requestContext.getStorageSystem());
            String haClusterId = getClusterNameForVarray(backendRequestContext.getHaVarray(), requestContext.getStorageSystem());
            _logger.info("the source cluster id is {} and the high availability cluster id is {}",
                    sourceClusterId, haClusterId);
            backendRequestContext.setHaClusterId(haClusterId);

            _logger.info("Ingestion started for vplex backend volume {}", associatedVolume.getNativeGuid());

            try {

                validateBackendVolumeVpool(associatedVolume, backendRequestContext.getVpool());

                IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(associatedVolume, false);

                @SuppressWarnings("unchecked")
                BlockObject blockObject = ingestStrategy.ingestBlockObjects(backendRequestContext,
                        VolumeIngestionUtil.getBlockObjectClass(associatedVolume));

                if (null == blockObject) {
                    // an exception should have been thrown by a lower layer in
                    // ingestion did not succeed, but in case it wasn't, throw one
                    throw IngestionException.exceptions.generalVolumeException(
                            associatedVolume.getLabel(), "check the logs for more details");
                }

                // Note that a VPLEX backend volume could also be a snapshot target volume.
                // When this is the case, the local volume ingest strategy is what will be
                // retrieved and executed. As a result, the object returned will be a
                // Volume instance not a BlockSnapshot instance. However, the local volume
                // ingest strategy realizes that the volume may also be a snapshot target
                // volume and creates the BlockSnapshot instance to represent the snapshot
                // target volume and adds this BlockSnapshot instance to the created objects
                // list. Because the BlockSnapshot and Volume instances will have the same
                // native GUID, as they represent the same physical volume, we can't
                // add the Volume to the created objects list as it would just replace
                // the BlockSnapshot instance and only the Volume would get created. So,
                // we first move the snapshot to the created snapshots list before adding
                // the volume to the created objects list.
                Map<String, BlockObject> createdObjectMap = backendRequestContext.getObjectsToBeCreatedMap();
                String blockObjectNativeGuid = blockObject.getNativeGuid();
                if (createdObjectMap.containsKey(blockObjectNativeGuid)) {
                    BlockObject createdBlockObject = createdObjectMap.get(blockObjectNativeGuid);
                    if (createdBlockObject instanceof BlockSnapshot) {
                        _logger.info("Backend ingestion created block snapshot {}", blockObjectNativeGuid);

                        // The snapshot will be created with the backend volume project, so we
                        // need to update that to the frontend project.
                        ((BlockSnapshot) createdBlockObject).setProject(new NamedURI(backendRequestContext.getFrontendProject().getId(),
                                createdBlockObject.getLabel()));

                        backendRequestContext.getCreatedSnapshotMap().put(blockObjectNativeGuid, (BlockSnapshot) createdBlockObject);
                        createdObjectMap.put(blockObjectNativeGuid, blockObject);
                    } else {
                        // This should not happen. If there is an instance in the created
                        // objects list with the same guid as the ingested block object
                        // it must be that the backend volume is also a snapshot target
                        // volume and the strategy created the BlockSnapshot instance and
                        // added it to the created objects list.
                        _logger.warn("Unexpected object in created objects list during backend ingestion {}:{}",
                                blockObjectNativeGuid, createdBlockObject.getLabel());
                    }
                } else {
                    createdObjectMap.put(blockObjectNativeGuid, blockObject);
                }

                backendRequestContext.getProcessedUnManagedVolumeMap().put(associatedVolume.getNativeGuid(),
                        backendRequestContext.getVolumeContext());
                _logger.info("Ingestion ended for backend volume {}", associatedVolume.getNativeGuid());
            } catch (Exception ex) {
                _logger.error(ex.getLocalizedMessage());
                throw ex;
            }
        }
    }

    /**
     * Ingests the export masks between the backend storage arrays and the
     * VPLEX for the backend storage volumes.
     * 
     * Calls ingestExportMasks by getting a nested IngestStrategy
     * for each backend volume or replica from the IngestStrategyFactory.
     * 
     * @param system the VPLEX storage system
     * @param vPool the virtual pool for ingestion
     * @param virtualArray the virtual array for ingestion
     * @param tenant the tenant for ingestion
     * @param unManagedVolumesToBeDeleted unmanaged volumes that will be marked for deletion
     * @param taskStatusMap a map of task statuses
     * @param context the VplexBackendIngestionContext for the parent virtual volume
     * 
     * @throws IngestionException
     */
    private void ingestBackendExportMasks(IngestionRequestContext requestContext,
            VplexVolumeIngestionContext backendRequestContext)
            throws IngestionException {

        VirtualArray virtualArray = backendRequestContext.getVarray();
        VirtualPool vPool = backendRequestContext.getVpool();

        // process masking for any successfully processed UnManagedVolumes
        for (Entry<String, VolumeIngestionContext> entry : backendRequestContext.getProcessedUnManagedVolumeMap().entrySet()) {

            String unManagedVolumeGUID = entry.getKey();
            VolumeIngestionContext volumeContext = entry.getValue();
            UnManagedVolume processedUnManagedVolume = volumeContext.getUnmanagedVolume();

            if (processedUnManagedVolume.getUnmanagedExportMasks().isEmpty()) {
                String reason = "the backend volume has no unmanaged export masks "
                        + processedUnManagedVolume.getLabel();
                _logger.warn(reason);
                continue;
            }

            _logger.info("ingesting export mask(s) for unmanaged volume " + processedUnManagedVolume);

            String createdObjectGuid = unManagedVolumeGUID.replace(
                    VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
            BlockObject processedBlockObject = backendRequestContext.getObjectsToBeCreatedMap().get(createdObjectGuid);

            if (processedBlockObject == null) {
                String reason = "The ingested block object is null. Skipping ingestion of export masks.";
                throw IngestionException.exceptions.generalVolumeException(
                        processedUnManagedVolume.getLabel(), reason);
            }

            // we need to make sure we're using the correct varray and vpool for this backend volume.
            // in the case of distributed, it could be the HA array (which would have been determined
            // by the ingestBackendVolumes method before processing got to this point).
            // the processedBlockObject here is the ingested backend volume for the leg we're looking at
            // and it will have the correct virtual array and virtual pool already set on it
            if (backendRequestContext.isDistributed()) {
                // a backend volume can only be a Volume BlockObject type, so this is safe
                Volume backendVolume = ((Volume) processedBlockObject);

                virtualArray = _dbClient.queryObject(VirtualArray.class, backendVolume.getVirtualArray());
                vPool = _dbClient.queryObject(VirtualPool.class, backendVolume.getVirtualPool());

                if (virtualArray == null) {
                    throw IngestionException.exceptions.failedToIngestVplexBackend(
                            "Could not find virtual array for backend volume " + backendVolume.getLabel());
                }
                if (vPool == null) {
                    throw IngestionException.exceptions.failedToIngestVplexBackend(
                            "Could not find virtual pool for backend volume " + backendVolume.getLabel());
                }
            }

            try {
                // assuming only one export mask for this volume between the backend array and VPLEX
                int uemCount = 0;
                for (String uri : processedUnManagedVolume.getUnmanagedExportMasks()) {

                    if (uemCount++ > 1) {
                        _logger.warn("more than one unmanaged export mask found on this backend volume");
                    }

                    UnManagedExportMask uem = _dbClient.queryObject(UnManagedExportMask.class, URI.create(uri));

                    _logger.info("preparing to ingest backend unmanaged export mask {}",
                            uem.getMaskName());

                    // find the associated storage system
                    URI storageSystemUri = processedUnManagedVolume.getStorageSystemUri();
                    StorageSystem associatedSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);

                    // collect the initiators in this backend mask
                    List<URI> initUris = new ArrayList<URI>();
                    for (String initUri : uem.getKnownInitiatorUris()) {
                        initUris.add(URI.create(initUri));
                    }
                    List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initUris);
                    backendRequestContext.setDeviceInitiators(initiators);

                    // find or create the backend export group
                    ExportGroup exportGroup = this.findOrCreateExportGroup(
                            requestContext.getStorageSystem(), associatedSystem, initiators,
                            virtualArray.getId(), backendRequestContext.getBackendProject().getId(),
                            backendRequestContext.getTenant().getId(), DEFAULT_BACKEND_NUMPATHS, uem);
                    if (null == exportGroup.getId()) {
                        backendRequestContext.setExportGroupCreated(true);
                        exportGroup.setId(URIUtil.createId(ExportGroup.class));
                    }
                    backendRequestContext.setExportGroup(exportGroup);

                    // find the ingest export strategy and call into for this unmanaged export mask
                    IngestExportStrategy ingestStrategy =
                            ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);
                    BlockObject blockObject = ingestStrategy.ingestExportMasks(
                            processedUnManagedVolume, processedBlockObject, backendRequestContext);

                    if (null == blockObject) {
                        // an exception should have been thrown by a lower layer in
                        // ingestion did not succeed, but in case it wasn't, throw one
                        throw IngestionException.exceptions.generalVolumeException(
                                processedUnManagedVolume.getLabel(), "check the logs for more details");
                    } else {
                        backendRequestContext.getObjectsIngestedByExportProcessing().add(blockObject);
                    }

                    /**
                     * TODO verify persistence - this should be handled by context.commitBackend
                     * // update the related objects if any after successful export mask ingestion
                     * List<DataObject> updatedO bjects = backendRequestContext.getUpdatedObjectMap().get(unManagedVolumeGUID);
                     * if (updatedObjects != null && !updatedObjects.isEmpty()) {
                     * _dbClient.updateObject(updatedObjects);
                     * }
                     */
                }
            } catch (Exception ex) {
                _logger.error(ex.getLocalizedMessage());
                throw ex;
            }
        }
    }

    /**
     * Find or Create an ExportGroup.
     * 
     * @param vplex -- VPLEX StorageSystem
     * @param array -- Array StorageSystem
     * @param initiators -- Collection<Initiator> representing VPLEX back-end ports.
     * @param virtualArrayURI
     * @param projectURI
     * @param tenantURI
     * @param numPaths Value of maxPaths to be put in ExportGroup
     * @param unmanagedExportMask the unmanaged export mask
     * @return existing or newly created ExportGroup (not yet persisted)
     */
    ExportGroup findOrCreateExportGroup(StorageSystem vplex,
            StorageSystem array, Collection<Initiator> initiators,
            URI virtualArrayURI,
            URI projectURI, URI tenantURI, int numPaths,
            UnManagedExportMask unmanagedExportMask) {

        String arrayName = array.getSystemType().replace("block", "")
                + array.getSerialNumber().substring(array.getSerialNumber().length() - 4);
        String groupName = unmanagedExportMask.getMaskName() + "_" + arrayName;

        List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, ExportGroup.class, PrefixConstraint.Factory
                        .getFullMatchConstraint(ExportGroup.class, "label",
                                groupName));

        ExportGroup exportGroup = null;
        if (null != exportGroups && !exportGroups.isEmpty()) {
            for (ExportGroup group : exportGroups) {
                if (null != group) {
                    _logger.info(String.format("Returning existing ExportGroup %s", group.getLabel()));
                    exportGroup = group;
                }
            }
        } else {

            // No existing group has the mask, let's create one.
            exportGroup = new ExportGroup();
            exportGroup.setLabel(groupName);
            exportGroup.setProject(new NamedURI(projectURI, exportGroup.getLabel()));
            exportGroup.setVirtualArray(vplex.getVirtualArray());
            exportGroup.setTenant(new NamedURI(tenantURI, exportGroup.getLabel()));
            exportGroup.setGeneratedName(groupName);
            exportGroup.setVolumes(new StringMap());
            exportGroup.setOpStatus(new OpStatusMap());
            exportGroup.setVirtualArray(virtualArrayURI);
            exportGroup.setNumPaths(numPaths);

            // Add the initiators into the ExportGroup.
            for (Initiator initiator : initiators) {
                exportGroup.addInitiator(initiator);
            }

            _logger.info(String.format("Returning new ExportGroup %s", exportGroup.getLabel()));
        }

        return exportGroup;
    }

    @Override
    protected void updateBlockObjectNativeIds(BlockObject blockObject, UnManagedVolume unManagedVolume) {
        String label = unManagedVolume.getLabel();
        blockObject.setDeviceLabel(label);
        blockObject.setLabel(label);
        blockObject.setNativeId(blockObject.getNativeGuid());
    }

    @Override
    protected URI getConsistencyGroupUri(UnManagedVolume unManagedVolume, VirtualPool vPool, URI project, URI tenant,
            URI virtualArray, DbClient dbClient) {
        return VolumeIngestionUtil.getVplexConsistencyGroup(unManagedVolume, vPool, project, tenant, virtualArray, dbClient);
    }

    @Override
    protected void updateCGPropertiesInVolume(URI consistencyGroupUri, BlockObject volume, StorageSystem system,
            UnManagedVolume unManagedVolume) {
        if (consistencyGroupUri != null) {

            String cgName = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(), unManagedVolume.getVolumeInformation());

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupUri);

            // Add a system consistency group mapping for the varray the cluster is connected to
            try {
                String vplexClusterName = VPlexControllerUtils.getVPlexClusterName(
                        _dbClient, cg.getVirtualArray(), system.getId());
                if (vplexClusterName != null) {

                    StringSet unmanagedVolumeClusters = unManagedVolume.getVolumeInformation().get(
                            SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString());
                    // Add a ViPR CG mapping for each of the VPlex clusters the VPlex CG
                    // belongs to.
                    if (unmanagedVolumeClusters != null && !unmanagedVolumeClusters.isEmpty()) {
                        Iterator<String> unmanagedVolumeClustersItr = unmanagedVolumeClusters.iterator();
                        String cgCluster = null;
                        while (unmanagedVolumeClustersItr.hasNext()) {
                            if (vplexClusterName.equals(unmanagedVolumeClustersItr.next())) {
                                cgCluster = vplexClusterName;
                                break;
                            }
                        }
                        if (cgCluster != null) {
                            cg.addSystemConsistencyGroup(system.getId().toString(),
                                    BlockConsistencyGroupUtils.buildClusterCgName(cgCluster, cgName));
                            _dbClient.updateAndReindexObject(cg);
                        } else {
                            throw new Exception(
                                    "could not determine VPLEX cluster name for consistency group virtual array "
                                            + cg.getVirtualArray());
                        }
                    } else {
                        throw new Exception(
                                "no VPLEX cluster(s) set on unmanaged volume "
                                        + unManagedVolume.getLabel());
                    }
                } else {
                    throw new Exception(
                            "could not determine VPLEX cluster name for virtual array "
                                    + cg.getVirtualArray());
                }
            } catch (Exception ex) {
                String message = "could not determine VPLEX cluster placement for consistency group "
                        + cg.getLabel() + " configured on UnManagedVolume " + unManagedVolume.getLabel();
                _logger.error(message, ex);
                throw IngestionException.exceptions.generalVolumeException(unManagedVolume.getLabel(), message);
            }

            volume.setConsistencyGroup(consistencyGroupUri);
        }
    }

    @Override
    protected void setProtocol(StoragePool pool, Volume volume, VirtualPool vPool) {
        if (null == volume.getProtocol()) {
            volume.setProtocol(new StringSet());
        }
        volume.getProtocol().addAll(vPool.getProtocols());
    }

    @Override
    protected StoragePool validateAndReturnStoragePoolInVAarray(UnManagedVolume unManagedVolume, VirtualArray virtualArray) {
        return null;
    }

    @Override
    protected void checkSystemResourceLimitsExceeded(StorageSystem system, UnManagedVolume unManagedVolume, List<URI> systemCache) {
        // always return true,as for vplex volumes this limit doesn't have any effect
        return;
    }

    @Override
    protected void checkPoolResourceLimitsExceeded(StorageSystem system, StoragePool pool, UnManagedVolume unManagedVolume,
            List<URI> poolCache) {
        // always return true, as pool will be null
        return;
    }

    @Override
    protected void checkUnManagedVolumeAddedToCG(UnManagedVolume unManagedVolume, VirtualArray virtualArray, TenantOrg tenant,
            Project project, VirtualPool vPool) {
        if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
            URI consistencyGroupUri = VolumeIngestionUtil.getVplexConsistencyGroup(unManagedVolume, vPool, project.getId(),
                    tenant.getId(), virtualArray.getId(), _dbClient);
            if (null == consistencyGroupUri) {
                _logger.warn("A Consistency Group for the VPLEX volume could not be determined. Skipping Ingestion.");
                throw IngestionException.exceptions.unmanagedVolumeVplexConsistencyGroupCouldNotBeIdentified(unManagedVolume.getLabel());
            }

        }
    }

    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        // skip auto tiering validation for virtual volume (only needed on backend volumes)
        if (!VolumeIngestionUtil.isVplexVolume(unManagedVolume)) {
            super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
        }
    }

    // VPLEX API cache related properties
    private long cacheLastRefreshed = 0;
    private static final long CACHE_TIMEOUT = 1800000; // 30 minutes
    private Map<String, Map<String, String>> vplexToClusterIdToNameMap;
    private Map<String, Map<String, String>> vplexToVarrayToClusterIdMap;

    /**
     * Gets a cached map of each virtual array's URI to the cluster id (1 or 2)
     * it connects to, a separate map for each VPLEX system.
     * 
     * @param vplex the VPLEX system whose map should be fetched
     * @return the varray to cluster id map for the VPLEX system
     */
    private Map<String, String> getVarrayToClusterIdMap(StorageSystem vplex) {
        if (null == vplexToVarrayToClusterIdMap) {
            vplexToVarrayToClusterIdMap = new HashMap<String, Map<String, String>>();
        }
        Map<String, String> varrayToClusterIdMap = vplexToVarrayToClusterIdMap.get(vplex.getId().toString());
        if (null == varrayToClusterIdMap) {
            varrayToClusterIdMap = new HashMap<String, String>();
            vplexToVarrayToClusterIdMap.put(vplex.getId().toString(), varrayToClusterIdMap);
        }
        return varrayToClusterIdMap;
    }

    /**
     * Gets a cached map of the cluster id (1 or 2) to its name
     * (e.g., cluster-1 or cluster-2), a separate map for each VPLEX system.
     * 
     * @param vplex the VPLEX system whose map should be fetched
     * @return the cluster id to cluster name map for the VPLEX system
     */
    private Map<String, String> getClusterIdToNameMap(StorageSystem vplex) {
        if (null == vplexToClusterIdToNameMap) {
            vplexToClusterIdToNameMap = new HashMap<String, Map<String, String>>();
        }
        Map<String, String> clusterIdToNameMap = vplexToClusterIdToNameMap.get(vplex.getId().toString());
        if (null == clusterIdToNameMap) {
            clusterIdToNameMap = new HashMap<String, String>();
            vplexToClusterIdToNameMap.put(vplex.getId().toString(), clusterIdToNameMap);
        }
        return clusterIdToNameMap;
    }

    /**
     * Find the VPLEX cluster name for the cluster connected
     * to a given Virtual Array.
     * 
     * @param varray the Virtual Array to check
     * @param vplex the VPLEX to look at
     * 
     * @return the cluster name (e.g. cluster-1 or cluster-2)
     */
    private String getClusterNameForVarray(VirtualArray varray, StorageSystem vplex) {
        if (null == varray || null == vplex) {
            return null;
        }

        String varrayClusterId = getVarrayToClusterIdMap(vplex).get(varray.getId().toString());
        if (null == varrayClusterId) {
            varrayClusterId = ConnectivityUtil.getVplexClusterForVarray(varray.getId(), vplex.getId(), _dbClient);
            if (!varrayClusterId.equals(ConnectivityUtil.CLUSTER_UNKNOWN)) {
                getVarrayToClusterIdMap(vplex).put(varray.getId().toString(), varrayClusterId);
            }
        }

        if (varrayClusterId.equals(ConnectivityUtil.CLUSTER_UNKNOWN)) {
            String reason = "Virtual Array is not associated with either cluster of the VPLEX";
            _logger.error(reason);
            throw IngestionException.exceptions.validationException(reason);
        }

        String varrayClusterName = getClusterIdToNameMap(vplex).get(varrayClusterId);
        if (null == varrayClusterName) {
            varrayClusterName = VPlexControllerUtils.getClusterNameForId(
                    varrayClusterId, vplex.getId(), _dbClient);
            getClusterIdToNameMap(vplex).put(varrayClusterId, varrayClusterName);
        }

        if (null == varrayClusterName) {
            String reason = "Couldn't find VPLEX cluster name for cluster id " + varrayClusterId;
            _logger.error(reason);
            throw IngestionException.exceptions.validationException(reason);
        }

        return varrayClusterName;
    }

    /**
     * Validates a backend UnMangedVolume against the Virtual Pool into which
     * it will be ingested.
     * 
     * @param backendVolume the backend UnManagedVolume to validate
     * @param vpool the Virtual Pool to check
     */
    private void validateBackendVolumeVpool(UnManagedVolume backendVolume, VirtualPool vpool) {
        URI storagePoolUri = backendVolume.getStoragePoolUri();
        if (!vpool.getMatchedStoragePools().contains(storagePoolUri.toString())) {
            String reason = "vpool " + vpool.getLabel()
                    + " does not match the backend volume's storage pool URI "
                    + storagePoolUri;
            _logger.error(reason);
            throw IngestionException.exceptions.validationException(reason);
        }
    }

    /**
     * Refreshes the VPLEX data caches if the cache timeout is up.
     * 
     * @param vplex the VPLEX system for which caches should be refreshed
     */
    private void refreshCaches(StorageSystem vplex) {
        long timeRightNow = System.currentTimeMillis();
        if (timeRightNow > (cacheLastRefreshed + CACHE_TIMEOUT)) {
            _logger.info("clearing vplex api info caches for {}", vplex.getLabel());
            getClusterIdToNameMap(vplex).clear();
            getVarrayToClusterIdMap(vplex).clear();
            cacheLastRefreshed = timeRightNow;
        }

        // TODO: it would be nice for there to be an onIngestionComplete
        // method in the ingest strategy framework that would
        // enable clearing these caches at the end of ingestion.
        // not currently possible, though.
    }
}
