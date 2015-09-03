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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.api.service.impl.resource.VPlexBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
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
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;
import com.google.common.base.Joiner;

/**
 * Responsible for ingesting vplex local and distributed virtual volumes.
 */
public class BlockVplexVolumeIngestOrchestrator extends BlockVolumeIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(BlockVplexVolumeIngestOrchestrator.class);

    // maps storage system URIs to StorageSystem objects
    private Map<String, StorageSystem> _systemMap = new HashMap<String, StorageSystem>();

    // the ingest strategy factory, used for ingesting the backend volume
    private IngestStrategyFactory ingestStrategyFactory;

    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

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
    public <T extends BlockObject> T ingestBlockObjects(
            List<URI> systemCache, List<URI> poolCache, StorageSystem system,
            UnManagedVolume unManagedVolume, VirtualPool vPool, VirtualArray virtualArray, 
            Project project, TenantOrg tenant, List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, 
            boolean unManagedVolumeExported, Class<T> clazz, Map<String, StringBuffer> taskStatusMap, 
            String vplexIngestionMethod) throws IngestionException {

        refreshCaches(system);

        VolumeIngestionUtil.checkValidVarrayForUnmanagedVolume(unManagedVolume, virtualArray.getId(),
                getClusterIdToNameMap(system), getVarrayToClusterIdMap(system), _dbClient);

        _logger.info("VPLEX ingestion method is " + vplexIngestionMethod);
        boolean ingestBackend = (null == vplexIngestionMethod)
                || vplexIngestionMethod.isEmpty()
                || (!vplexIngestionMethod.equals(VplexBackendIngestionContext.INGESTION_METHOD_VVOL_ONLY));

        VplexBackendIngestionContext context = null;

        if (ingestBackend) {

            context = new VplexBackendIngestionContext(unManagedVolume, _dbClient);
            context.setIngestionInProgress(true);

            //
            // If the "Only During Discovery" system setting is set, no new data will 
            // be fetched during ingestion. This assumes that all data has been collected 
            // during discovery and ingestion will fail if it can't find all the required data.
            // 
            // If "Only During Ingestion" or "During Discovery and Ingestion" mode is set, 
            // then an attempt will be made to query the VPLEX api now to find any incomplete data, 
            // but the database will be checked first.
            // 
            // The default mode is  "Only During Discovery", so the user needs to remember
            // to run discovery first on all backend storage arrays before running on the VPLEX.
            //
            String discoveryMode = ControllerUtils.getPropertyValueFromCoordinator(
                    _coordinator, VplexBackendIngestionContext.DISCOVERY_MODE);
            if (VplexBackendIngestionContext.DISCOVERY_MODE_DISCOVERY_ONLY.equals(discoveryMode)) {
                context.setInDiscoveryOnlyMode(true);
            }

            // the backend volumes and export masks will be part of the VPLEX project
            // rather than the front-end virtual volume project, so we need to set that in the context
            Project vplexProject = VPlexBlockServiceApiImpl.getVplexProject(system, _dbClient, _tenantsService);
            context.setBackendProject(vplexProject);
            context.setFrontendProject(project);

            try {
                _logger.info("Ingesting backend structure of VPLEX virtual volume {}", unManagedVolume.getLabel());

                validateContext(vPool, tenant, context);

                ingestBackendVolumes(systemCache, poolCache, vPool,
                        virtualArray, tenant, unManagedVolumesToBeDeleted,
                        taskStatusMap, context, vplexIngestionMethod);

                ingestBackendExportMasks(system, vPool, virtualArray,
                        tenant, unManagedVolumesToBeDeleted,
                        taskStatusMap, context);

            } catch (Exception ex) {
                _logger.error("error during VPLEX backend ingestion: ", ex);

                // remove unmanaged backend vols from ones to be deleted in case
                // they were marked inactive during export mask ingestion
                if (null != context.getUnmanagedBackendVolumes()) {
                    boolean removed = unManagedVolumesToBeDeleted.removeAll(context.getUnmanagedBackendVolumes());
                    _logger.info("were backend volumes prevented for deletion? " + removed);
                }

                throw IngestionException.exceptions.failedToIngestVplexBackend(ex.getLocalizedMessage());
            }
        }

        try {
            _logger.info("Ingesting VPLEX virtual volume {}", unManagedVolume.getLabel());
            T virtualVolume = super.ingestBlockObjects(
                    systemCache, poolCache, system, unManagedVolume, 
                    vPool, virtualArray, project, tenant, unManagedVolumesToBeDeleted, 
                    createdObjectMap, updatedObjectMap, unManagedVolumeExported, 
                    clazz, taskStatusMap, vplexIngestionMethod);

            if (ingestBackend && (null != context) && (null != virtualVolume)) {
                setFlags(context);
                setAssociatedVolumes(context, (Volume) virtualVolume);
                createVplexMirrorObjects(context, (Volume) virtualVolume);
                sortOutCloneAssociations(context, (Volume) virtualVolume);
                handleBackendPersistence(context);
                _logger.info(context.toStringDebug());
                _logger.info(context.getPerformanceReport());
            }

            return virtualVolume;
        } catch (Exception ex) {
            _logger.error("error during VPLEX backend ingestion wrap up: ", ex);

            // remove unmanaged backend vols from ones to be deleted in case
            // they were marked for deletion during export mask ingestion
            if (null != context && null != context.getUnmanagedBackendVolumes()) {
                boolean removed = unManagedVolumesToBeDeleted.removeAll(context.getUnmanagedBackendVolumes());
                _logger.info("were backend unmanaged volumes prevented from deletion? " + removed);
            }

            throw ex;
        }
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
        boolean isLocal = context.isVplexLocal();
        if ((isLocal && (unManagedBackendVolumes.size() < 1))
                || !isLocal && (unManagedBackendVolumes.size() < 2)) {
            String supportingDevice = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(),
                    unManagedVirtualVolume.getVolumeInformation());
            if (unManagedBackendVolumes.isEmpty()) {
                String reason = "failed to find any VPLEX backend volume for UnManagedVolume " 
                        + unManagedVirtualVolume.getLabel() + " with supporting device "
                        + supportingDevice;
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            } else {
                String reason = "failed to find all VPLEX backend volume for UnManagedVolume " 
                        + unManagedVirtualVolume.getLabel() + " with supporting device "
                        + supportingDevice + ". Did find these backend volumes, though: "
                        + Joiner.on(", ").join(unManagedBackendVolumes);
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
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
                for (Entry<UnManagedVolume, String> entry : context.getUnmanagedMirrors().entrySet()) {
                    if (mirrorGuid.equals(entry.getKey().getNativeGuid())) {
                        _logger.info("\t\tbut it's native, so it's okay...");
                        mirrorator.remove();
                    }
                }
            }
            if (!mirrors.isEmpty()) {
                String reason = "backend volume has non-native vplex mirror(s): " + Joiner.on(", ").join(mirrors);
                _logger.error(reason);
                throw IngestionException.exceptions.validationException(reason);
            }
        }

        int mirrorCount = context.getUnmanagedMirrors().size();
        if (mirrorCount > 0) {
            _logger.info("{} native mirror(s) are present, validating vpool", mirrorCount);
            if (VirtualPool.vPoolSpecifiesMirrors(vpool, _dbClient)) {
                if (mirrorCount > vpool.getMaxNativeContinuousCopies()) {
                    String reason = "volume has more continuous copies (" + mirrorCount + ") than vpool allows";
                    _logger.error(reason);
                    throw IngestionException.exceptions.validationException(reason);
                }
            } else {
                String reason = "vpool does not allow continuous copies, but volume has" + mirrorCount + " mirror(s)";
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

        long unManagedVolumesCapacity = 
                VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(
                        _dbClient, context.getAllUnManagedVolumeUris());
        _logger.info("validating total backend volume capacity {} against the vpool", unManagedVolumesCapacity);
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool, 
                context.getBackendProject(), tenant, unManagedVolumesCapacity, "volume");
        
        _logger.info("validating backend volumes against the vpool");
        VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(
                context.getAllUnManagedVolumeUris(), vpool, _dbClient);
        
    }

    /**
     * Ingests the backend volumes and any related replicas.
     * 
     * Calls ingestBlockObjects by getting a nested IngestStrategy 
     * for each backend volume or replica from the IngestStrategyFactory.
     * 
     * @param systemCache the cache of storage system URIs
     * @param poolCache the cache of storage pool URIs
     * @param vPool the virtual pool for ingestion
     * @param virtualArray the virtual array for ingestion
     * @param tenant the tenant for ingestion
     * @param unManagedVolumesToBeDeleted unmanaged volumes that will be marked for deletion
     * @param taskStatusMap a map of task statuses
     * @param context the VplexBackendIngestionContext for the parent virtual volume
     * @param vplexIngestionMethod the ingestion method (full or virtual volume only)
     * 
     * @throws IngestionException
     */
    private void ingestBackendVolumes(List<URI> systemCache,
            List<URI> poolCache, VirtualPool vPool, 
            VirtualArray virtualArray, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, StringBuffer> taskStatusMap,
            VplexBackendIngestionContext context, String vplexIngestionMethod) throws IngestionException {

        for (UnManagedVolume associatedVolume : context.getAllUnmanagedVolumes()) {
            _logger.info("Ingestion started for vplex backend volume {}", associatedVolume.getNativeGuid());

            try {
                // get the associated storage system, possibly from cache
                URI storageSystemUri = associatedVolume.getStorageSystemUri();
                StorageSystem associatedSystem = _systemMap.get(storageSystemUri.toString());
                if (null == associatedSystem) {
                    associatedSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                    _systemMap.put(storageSystemUri.toString(), associatedSystem);
                }

                // determine the correct project to use with this volume:
                // the backend volumes have the vplex backend Project, but
                // the rest have the same Project as the virtual volume.
                Project project = context.getUnmanagedBackendVolumes().contains(associatedVolume) ?
                        context.getBackendProject() : context.getFrontendProject();

                IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(associatedVolume);

                @SuppressWarnings("unchecked")
                BlockObject blockObject = ingestStrategy.ingestBlockObjects(systemCache, poolCache,
                        associatedSystem, associatedVolume, vPool, virtualArray,
                        project, tenant, unManagedVolumesToBeDeleted, context.getCreatedObjectMap(),
                        context.getUpdatedObjectMap(), true,
                        VolumeIngestionUtil.getBlockObjectClass(associatedVolume), taskStatusMap, vplexIngestionMethod);

                if (null == blockObject) {
                    // an exception should have been thrown by a lower layer in
                    // ingestion did not succeed, but in case it wasn't, throw one
                    throw IngestionException.exceptions.generalVolumeException(
                            associatedVolume.getLabel(), "check the logs for more details");
                }

                context.getCreatedObjectMap().put(blockObject.getNativeGuid(), blockObject);
                context.getProcessedUnManagedVolumeMap().put(associatedVolume.getNativeGuid(), associatedVolume);
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
    private void ingestBackendExportMasks(StorageSystem system,
            VirtualPool vPool, VirtualArray virtualArray,
            TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, StringBuffer> taskStatusMap,
            VplexBackendIngestionContext context) throws IngestionException {

        // process masking for any successfully processed UnManagedVolumes
        for (Entry<String, UnManagedVolume> entry : context.getProcessedUnManagedVolumeMap().entrySet()) {
            
            String unManagedVolumeGUID = entry.getKey();
            UnManagedVolume processedUnManagedVolume = entry.getValue();
            
            // we only need to worry about creating export masks for the
            // actual backend volumes. if any replicas have unmanaged export
            // masks, they should be ingested separately for the hosts
            // they are exported to, not the backend of the VPLEX
            if (!context.isBackendVolume(processedUnManagedVolume)) {
                _logger.info("export mask processing is only required for backend volumes, skipping {}", 
                        processedUnManagedVolume.getLabel());
                continue;
            }

            if (processedUnManagedVolume.getUnmanagedExportMasks().isEmpty()) {
                _logger.info("ingested block object {} has no unmanaged export masks", 
                        processedUnManagedVolume.getLabel());
                continue;
            }

            _logger.info("ingesting export mask(s) for unmanaged volume " + processedUnManagedVolume);

            String createdObjectGuid = unManagedVolumeGUID.replace(
                    VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
            BlockObject processedBlockObject = context.getCreatedObjectMap().get(createdObjectGuid);

            if (processedBlockObject == null) {
                String reason = "The ingested block object is null. Skipping ingestion of export masks.";
                throw IngestionException.exceptions.generalVolumeException(
                        processedUnManagedVolume.getLabel(), reason);
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
                    StorageSystem associatedSystem = _systemMap.get(storageSystemUri.toString());

                    // collect the initiators in this backend mask
                    List<URI> initUris = new ArrayList<URI>();
                    for (String initUri : uem.getKnownInitiatorUris()) {
                        initUris.add(URI.create(initUri));
                    }
                    List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initUris);

                    // find or create the backend export group
                    boolean exportGroupCreated = false;
                    ExportGroup exportGroup = this.findOrCreateExportGroup(
                            system, associatedSystem, initiators,
                            virtualArray.getId(), context.getBackendProject().getId(),
                            tenant.getId(), 4, uem, exportGroupCreated);

                    // create an ingest param so that we can reuse the ingestExportMask method
                    VolumeExportIngestParam exportIngestParam = new VolumeExportIngestParam();
                    exportIngestParam.setProject(context.getBackendProject().getId());
                    exportIngestParam.setVarray(virtualArray.getId());
                    exportIngestParam.setVpool(vPool.getId());
                    List<URI> associatedVolumeUris = new ArrayList<URI>();
                    for (UnManagedVolume umv : context.getUnmanagedBackendVolumes()) {
                        associatedVolumeUris.add(umv.getId());
                    }
                    exportIngestParam.setUnManagedVolumes(associatedVolumeUris);

                    // find the ingest export strategy and call into for this unmanaged export mask
                    IngestExportStrategy ingestStrategy = 
                            ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);
                    BlockObject blockObject = ingestStrategy.ingestExportMasks(
                            processedUnManagedVolume, exportIngestParam, exportGroup,
                            processedBlockObject, unManagedVolumesToBeDeleted, 
                            associatedSystem, exportGroupCreated, initiators);

                    if (null == blockObject) {
                        // an exception should have been thrown by a lower layer in
                        // ingestion did not succeed, but in case it wasn't, throw one
                        throw IngestionException.exceptions.generalVolumeException(
                                processedUnManagedVolume.getLabel(), "check the logs for more details");
                    } else {
                        context.getIngestedObjects().add(blockObject);
                    }

                    // update the related objects if any after successful export mask ingestion
                    List<DataObject> updatedObjects = context.getUpdatedObjectMap().get(unManagedVolumeGUID);
                    if (updatedObjects != null && !updatedObjects.isEmpty()) {
                        _dbClient.updateAndReindexObject(updatedObjects);
                    }
                }
            } catch (Exception ex) {
                _logger.error(ex.getLocalizedMessage());
                throw ex;
            }
        }
    }

    /**
     * Updates any internal flags on the ingested backend resources.
     * 
     * @param context the VplexBackendIngestionContext
     */
    private void setFlags(VplexBackendIngestionContext context) {
        // set internal object flag on any backend volumes
        for (BlockObject o : context.getCreatedObjectMap().values()) {
            if (context.getBackendVolumeGuids().contains(o.getNativeGuid())) {
                _logger.info("setting INTERNAL_OBJECT flag on " + o.getLabel());
                o.addInternalFlags(Flag.INTERNAL_OBJECT);
            }
        }
    }
    
    /**
     * Associates the virtual volume with the newly-ingested backend volumes.
     * This should be called after the parent virtual volume has already been ingested.
     * 
     * @param context the VplexBackendIngestionContext
     * @param virtualVolume the ingested virtual volume's Volume object.
     */
    private void setAssociatedVolumes(VplexBackendIngestionContext context, Volume virtualVolume) {
        Collection<BlockObject> createdObjects = context.getCreatedObjectMap().values();
        StringSet vols = new StringSet();
        for (BlockObject vol : createdObjects) {
            if (context.getBackendVolumeGuids().contains(vol.getNativeGuid())) {
                vols.add(vol.getId().toString());
            }
        }
        _logger.info("setting associated volumes {} on virtual volume {}", vols, virtualVolume.getLabel());
        virtualVolume.setAssociatedVolumes(vols);
    }

    /**
     * Create a VplexMirror database object if a VPLEX native mirror is present.
     * This should be called after the parent virtual volume has already been ingested.
     * 
     * @param context the VplexBackendIngestionContext
     * @param virtualVolume the ingested virtual volume's Volume object.
     */
    private void createVplexMirrorObjects(VplexBackendIngestionContext context, Volume virtualVolume) {
        if (!context.getUnmanagedMirrors().isEmpty()) {
            _logger.info("creating VplexMirror object for virtual volume " + virtualVolume.getLabel());
            for (Entry<UnManagedVolume, String> entry : context.getUnmanagedMirrors().entrySet()) {
                // find mirror and create a VplexMirror object
                BlockObject mirror = context.getCreatedObjectMap().get(entry.getKey().getNativeGuid()
                        .replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                                VolumeIngestionUtil.VOLUME));
                if (null != mirror) {
                    _logger.info("processing mirror " + mirror.getLabel());
                    if (mirror instanceof Volume) {
                        Volume mirrorVolume = (Volume) mirror;
                        
                        // create VplexMirror set all the basic properties
                        VplexMirror vplexMirror = new VplexMirror();
                        vplexMirror.setId(URIUtil.createId(VplexMirror.class));
                        vplexMirror.setCapacity(mirrorVolume.getCapacity());
                        vplexMirror.setLabel(mirrorVolume.getLabel());
                        vplexMirror.setNativeId(entry.getValue());
                        vplexMirror.setProvisionedCapacity(mirrorVolume.getProvisionedCapacity());
                        vplexMirror.setSource(new NamedURI(virtualVolume.getId(), virtualVolume.getLabel()));
                        vplexMirror.setStorageController(mirrorVolume.getStorageController());
                        vplexMirror.setTenant(mirrorVolume.getTenant());
                        vplexMirror.setThinPreAllocationSize(mirrorVolume.getThinVolumePreAllocationSize());
                        vplexMirror.setThinlyProvisioned(mirrorVolume.getThinlyProvisioned());
                        vplexMirror.setVirtualArray(mirrorVolume.getVirtualArray());
                        vplexMirror.setVirtualPool(mirrorVolume.getVirtualPool());

                        // set the associated volume for this VplexMirror
                        StringSet associatedVolumes = new StringSet();
                        associatedVolumes.add(mirrorVolume.getId().toString());
                        vplexMirror.setAssociatedVolumes(associatedVolumes);

                        // project will be the same as the virtual volume (i.e., the front-end project)
                        vplexMirror.setProject(new NamedURI(
                                context.getFrontendProject().getId(), mirrorVolume.getLabel()));
                        
                        // deviceLabel will be the very last part of the native guid
                        String[] devicePathParts = entry.getValue().split("/");
                        String deviceName = devicePathParts[devicePathParts.length - 1];
                        vplexMirror.setDeviceLabel(deviceName);

                        // save the new VplexMirror
                        _dbClient.createObject(vplexMirror);
                        
                        // set mirrors property on the parent virtual volume
                        StringSet mirrors = virtualVolume.getMirrors();
                        if (mirrors == null) {
                            mirrors = new StringSet();
                        }
                        mirrors.add(vplexMirror.getId().toString());
                        virtualVolume.setMirrors(mirrors);
                    }
                }
            }
        }
    }

    /**
     * Sort out the clone-parent associations if any full front-end clones are present.
     * This should be called after the parent virtual volume has already been ingested.
     * 
     * @param context the VplexBackendIngestionContext
     * @param virtualVolume the ingested virtual volume's Volume object.
     */
    private void sortOutCloneAssociations(VplexBackendIngestionContext context, Volume virtualVolumeSource) {

        for (Entry<UnManagedVolume, UnManagedVolume> entry : context.getUnmanagedFullClones().entrySet()) {

            // 
            // TODO: this will currently only work with a clone detected on a vplex local volume. 
            //       out of ~4000 existing volumes on our dev vplex i found only one full 
            //       front end clone (which i created myself), so i'm delaying this somewhat 
            //       complicated work to supported a clone on both legs; 
            //       will file a separate JIRA to add support; sorry :(
            //

            // locate the source backend Volume object
            UnManagedVolume backendUnmanagedVolumeSource = context.getUnmanagedBackendVolumes().get(0);
            Volume backendVolumeSource = (Volume)
                    context.getCreatedObjectMap().get(backendUnmanagedVolumeSource.getNativeGuid().replace(
                            VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME));
            if (null == backendVolumeSource) {
                throw IngestionException.exceptions.generalException(
                        "could not determine source backend volume for clone (full copy)");
            }
            if (null == backendVolumeSource.getId()) {
                // the backend source volume may not have been persisted yet
                backendVolumeSource.setId(URIUtil.createId(Volume.class));
            }

            // locate the target virtual Volume object
            Volume virtualVolumeTarget = (Volume)
                    context.getCreatedObjectMap().get(entry.getValue().getNativeGuid().replace(
                            VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME));

            // locate the target backend Volume object
            Volume backendVolumeTarget = null;
            if (null != virtualVolumeTarget.getAssociatedVolumes() &&
                    !virtualVolumeTarget.getAssociatedVolumes().isEmpty()) {
                String targetVvolUri = virtualVolumeTarget.getAssociatedVolumes().iterator().next();
                backendVolumeTarget = _dbClient.queryObject(Volume.class, URI.create(targetVvolUri));
            }
            if (null == backendVolumeTarget) {
                throw IngestionException.exceptions.generalException(
                        "could not determine target backend volume for clone (full copy)");
            }
            
            // set associations on the backend target Volume
            backendVolumeTarget.setAssociatedSourceVolume(backendVolumeSource.getId());
            backendVolumeTarget.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            backendVolumeTarget.setSyncActive(true);
            // need to persist this one, but the others will be handled by callers
            _dbClient.updateAndReindexObject(backendVolumeTarget);

            // set associations on the front-end target Volume
            NamedURI namedUri = new NamedURI(
                    context.getFrontendProject().getId(), virtualVolumeTarget.getLabel());
            virtualVolumeTarget.setProject(namedUri);
            _logger.info("set project named uri on {} to " + namedUri.toString(), virtualVolumeTarget.getLabel());
            virtualVolumeTarget.clearInternalFlags(Flag.INTERNAL_OBJECT);
            virtualVolumeTarget.setAssociatedSourceVolume(virtualVolumeSource.getId());
            virtualVolumeTarget.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            virtualVolumeTarget.setSyncActive(true);

            // set full copies and sync state on the backend source Volume
            StringSet backendFullCopies = new StringSet();
            backendFullCopies.add(backendVolumeTarget.getId().toString());
            backendVolumeSource.setFullCopies(backendFullCopies);
            backendVolumeSource.setSyncActive(true);

            // set full copies and sync state on the front-end source Volume
            StringSet frontendFullCopies = new StringSet();
            frontendFullCopies.add(virtualVolumeTarget.getId().toString());
            virtualVolumeSource.setFullCopies(frontendFullCopies);
            virtualVolumeSource.setSyncActive(true);
        }
    }

    /**
     * Persist all the backend resources.  Should only be called
     * after everything has been associated and the parent virtual volume
     * itself has been ingested.
     * 
     * @param context the VplexBackendIngestionContext
     */
    private void handleBackendPersistence(VplexBackendIngestionContext context) {
        _dbClient.createObject(context.getIngestedObjects());
        _dbClient.createObject(context.getCreatedObjectMap().values());
        for (List<DataObject> dos : context.getUpdatedObjectMap().values()) {
            _dbClient.persistObject(dos);
        }
        _dbClient.persistObject(context.getProcessedUnManagedVolumeMap().values());
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
     * @param exportGroupCreated whether or not a new group was crated
     * @return existing or newly created ExportGroup (not yet persisted)
     */
    ExportGroup findOrCreateExportGroup(StorageSystem vplex,
            StorageSystem array, Collection<Initiator> initiators,
            URI virtualArrayURI,
            URI projectURI, URI tenantURI, int numPaths,
            UnManagedExportMask unmanagedExportMask, boolean exportGroupCreated) {

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
                    exportGroupCreated = false;
                    _logger.info(String.format("Returning existing ExportGroup %s", group.getLabel()));
                    exportGroup = group;
                }
            }
        } else {

            // No existing group has the mask, let's create one.
            exportGroup = new ExportGroup();
            exportGroup.setId(URIUtil.createId(ExportGroup.class));
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

            exportGroupCreated = true;
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
    protected void updateCGPropertiesInVolume(URI consistencyGroupUri, Volume volume, StorageSystem system,
            UnManagedVolume unManagedVolume) {
        if (consistencyGroupUri != null) {

            String cgName = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(), unManagedVolume.getVolumeInformation());

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupUri);

            StringSet unmanagedVolumeClusters = unManagedVolume.getVolumeInformation().get(
                    SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString());
            // Add a ViPR CG mapping for each of the VPlex clusters the VPlex CG
            // belongs to.
            if (unmanagedVolumeClusters != null && !unmanagedVolumeClusters.isEmpty()) {
                Iterator<String> unmanagedVolumeClustersItr = unmanagedVolumeClusters.iterator();
                while (unmanagedVolumeClustersItr.hasNext()) {
                    cg.addSystemConsistencyGroup(system.getId().toString(),
                            BlockConsistencyGroupUtils.buildClusterCgName(unmanagedVolumeClustersItr.next(), cgName));
                }

                _dbClient.updateAndReindexObject(cg);
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
        super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
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
        //       method in the ingest strategy framework that would
        //       enable clearing these caches at the end of ingestion.
        //       not currently possible, though.
    }
}
