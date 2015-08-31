/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.api.service.impl.resource.VPlexBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
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
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;
import com.google.common.base.Joiner;

/**
 * Responsible for ingesting vplex local and distributed virtual volumes.
 */
public class BlockVplexVolumeIngestOrchestrator extends BlockVolumeIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(BlockVplexVolumeIngestOrchestrator.class);

    private static final long CACHE_TIMEOUT = 600000; // ten minutes
    private long cacheLastRefreshed = 0;
    
    // maps the cluster id (1 or 2) to its name (e.g., cluster-1 or cluster-2)
    private Map<String, String> clusterIdToNameMap = new HashMap<String, String>();

    // maps each virtual array's URI to the cluster id (1 or 2) it connects to
    private Map<String, String> varrayToClusterIdMap = new HashMap<String, String>();

    // maps storage system URIs to StorageSystem objects
    private Map<String, StorageSystem> systemMap = new HashMap<String, StorageSystem>();

    private IngestStrategyFactory ingestStrategyFactory;

    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    private TenantsService _tenantsService;

    public void setTenantsService(TenantsService tenantsService) {
        _tenantsService = tenantsService;
    }

    @Override
    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system,
            UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported,
            Class<T> clazz,
            Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod) throws IngestionException {
        // For VPLEX volumes, verify that it is OK to ingest the unmanaged
        // volume into the requested virtual array.

        long timeRightNow = new Date().getTime();
        if (timeRightNow > (cacheLastRefreshed + CACHE_TIMEOUT)) {
            _logger.debug("clearing vplex ingestion api info cache");
            clusterIdToNameMap.clear();
            varrayToClusterIdMap.clear();
            cacheLastRefreshed = timeRightNow;
        }

        if (!VolumeIngestionUtil.isValidVarrayForUnmanagedVolume(unManagedVolume, virtualArray.getId(),
                clusterIdToNameMap, varrayToClusterIdMap, _dbClient)) {
            throw IngestionException.exceptions.varrayIsInvalidForVplexVolume(virtualArray.getLabel(), unManagedVolume.getLabel());
        }

        // TODO probably convert to enum
        _logger.info("VPLEX ingestion method is " + vplexIngestionMethod);
        boolean ingestBackend = (null == vplexIngestionMethod) 
                || vplexIngestionMethod.isEmpty() 
                || (!vplexIngestionMethod.equals("VirtualVolumeOnly"));
        
        VplexBackendIngestionContext context = null;
        
        if (ingestBackend) {
            context = new VplexBackendIngestionContext(unManagedVolume, _dbClient);
            Project vplexProject = VPlexBlockServiceApiImpl.getVplexProject(system, _dbClient, _tenantsService);
            context.setBackendProject(vplexProject);
            context.setFrontendProject(project);
    
            try {
                
                // TODO calling this will preload everything and log it for debug purposes.
                // this is really just useful for testing, no need to call this first
                // as everything will lazy load from the context as needed from this point
                context.load();
                
                List<UnManagedVolume> unmanagedBackendVolumes = context.getUnmanagedBackendVolumes();
                if (null != unmanagedBackendVolumes && !unmanagedBackendVolumes.isEmpty()) {
    
                    // TODO make this context.validate(vpool, tenant);
                    validateContext(vPool, tenant, context);
                    
                    ingestBackendVolumes(systemCache, poolCache, vPool,
                            virtualArray, tenant, unManagedVolumesToBeDeleted, 
                            taskStatusMap, context, vplexIngestionMethod);
    
                    ingestBackendExportMasks(system, vPool, virtualArray, 
                            tenant, unManagedVolumesToBeDeleted, 
                            taskStatusMap, context);
                    
                    setFlags(context);
                }
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
            _logger.info("About to ingest the actual VPLEX virtual volume...");
            T virtualVolume = super.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool, virtualArray, project, tenant,
                    unManagedVolumesToBeDeleted, createdObjectMap, updatedObjectMap, unManagedVolumeExported, clazz, taskStatusMap, vplexIngestionMethod);
            
            if (ingestBackend) {
                setAssociatedVolumes(context, (Volume) virtualVolume);
                createVplexMirrorObjects(context, (Volume) virtualVolume);
                sortOutCloneAssociations(context, (Volume) virtualVolume);
                handleBackendPersistence(context);
            }

            return virtualVolume;
        } catch (Exception ex) {
            _logger.error("error during VPLEX backend ingestion wrap up: ", ex);
            
            // remove unmanaged backend vols from ones to be deleted in case
            // they were marked inactive during export mask ingestion
            if (null != context.getUnmanagedBackendVolumes()) {
                boolean removed = unManagedVolumesToBeDeleted.removeAll(context.getUnmanagedBackendVolumes());
                _logger.info("were backend volumes prevented for deletion? " + removed);
            }
            
            throw ex;
        }
    }

    private void validateContext(VirtualPool vpool, 
            TenantOrg tenant, VplexBackendIngestionContext context) throws Exception {
        UnManagedVolume unManagedVirtualVolume = context.getUnmanagedVirtualVolume();
        List<UnManagedVolume> unManagedBackendVolumes = context.getUnmanagedBackendVolumes();
        _logger.info("validating the backend volumes: " + unManagedBackendVolumes);
        
        _logger.info("validating if we have found enough backend volumes for ingestion");
        boolean isLocal = context.isVplexLocal();
        if ((isLocal && (unManagedBackendVolumes.size() < 1)) 
         || !isLocal && (unManagedBackendVolumes.size() < 2)) {
            String supportingDevice = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(), 
                    unManagedVirtualVolume.getVolumeInformation());
            if (unManagedBackendVolumes.isEmpty()) {
                throw IngestionException.exceptions.failedToFindAnyVplexBackendVolumes(
                        unManagedVirtualVolume.getNativeGuid(), supportingDevice);
            } else {
                String foundVolumes = Joiner.on(", ").join(unManagedBackendVolumes);
                throw IngestionException.exceptions.failedToFindAllVplexBackendVolumes(
                        unManagedVirtualVolume.getNativeGuid(), supportingDevice, foundVolumes);
            }
        }
        
        List<URI> unManagedBackendVolumeUris = new ArrayList<URI>();
        for (UnManagedVolume vol : unManagedBackendVolumes) {
            unManagedBackendVolumeUris.add(vol.getId());
            
            _logger.info("checking for non native mirrors on backend volume " + vol.getNativeGuid());
            StringSet mirrors = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.MIRRORS.toString(), vol.getVolumeInformation());
            Iterator<String> mirrorator = mirrors.iterator();
            while (mirrorator.hasNext()) {
                String mirrorGuid = mirrorator.next();
                _logger.info("\tvolume has mirror " + mirrorGuid);
                for (Entry<UnManagedVolume, String> entry : context.getUnmanagedMirrors().entrySet()) {
                    if (mirrorGuid.equals(entry.getKey().getNativeGuid())) {
                        _logger.info("\tbut it's native, so it's okay...");
                        mirrorator.remove();
                    }
                }
            }
            if (!mirrors.isEmpty()) {
                throw IngestionException.exceptions.generalException("backend volume has non-native vplex mirrors!");
            }
        }
        
        // TODO more validation - needs further analysis
        // check that vpool supports mirrors
        int mirrorCount = context.getUnmanagedMirrors().size();
        if (mirrorCount > 0) {
            if (VirtualPool.vPoolSpecifiesMirrors(vpool, _dbClient)) {
                if (vpool.getMaxNativeContinuousCopies() > mirrorCount) {
                    // TODO better exception
                    throw IngestionException.exceptions.generalException("more continuous copies (mirrors) for volume that vpool allows");
                }
            } else {
                // TODO better exception
                throw IngestionException.exceptions.generalException("vpool does not allow continuous copies, but a vplex mirror is present");
            }
        }
        
        int snapshotCount = context.getUnmanagedSnapshots().size();
        if (snapshotCount > 0) {
            if (VirtualPool.vPoolSpecifiesSnapshots(vpool)) {
                if (vpool.getMaxNativeSnapshots() > snapshotCount) {
                    // TODO better exception
                    throw IngestionException.exceptions.generalException("more snapshots for volume that vpool allows");
                }
            } else {
                // TODO better exception
                throw IngestionException.exceptions.generalException("vpool does not allow snapshots, but volume has snapshots");
            }
        }
        
        // check for Quotas
        long unManagedVolumesCapacity = VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(_dbClient, unManagedBackendVolumeUris);
        _logger.info("UnManagedVolume provisioning quota validation successful");
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool, context.getBackendProject(), tenant, unManagedVolumesCapacity, "volume");
        VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(unManagedBackendVolumeUris, vpool, _dbClient);
    }

    private void ingestBackendVolumes(List<URI> systemCache,
            List<URI> poolCache, VirtualPool vPool, VirtualArray virtualArray, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, StringBuffer> taskStatusMap,
            VplexBackendIngestionContext context, String vplexIngestionMethod) throws Exception {

        for (UnManagedVolume associatedVolume : context.getAllUnmanagedVolumes()) {
            _logger.info("Ingestion started for exported vplex backend unmanagedvolume {}", associatedVolume.getNativeGuid());

            try {
                URI storageSystemUri = associatedVolume.getStorageSystemUri();
                StorageSystem associatedSystem =  systemMap.get(storageSystemUri.toString());
                if (null == associatedSystem) {
                    associatedSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                    systemMap.put(storageSystemUri.toString(), associatedSystem);
                }
                
                IngestStrategy ingestStrategy =  ingestStrategyFactory.buildIngestStrategy(associatedVolume);
                
                @SuppressWarnings("unchecked")
                BlockObject blockObject = ingestStrategy.ingestBlockObjects(systemCache, poolCache, 
                        associatedSystem, associatedVolume, vPool, virtualArray, 
                        context.getBackendProject(), tenant, unManagedVolumesToBeDeleted, context.getCreatedObjectMap(), 
                        context.getUpdatedObjectMap(), true, 
                        VolumeIngestionUtil.getBlockObjectClass(associatedVolume), taskStatusMap, vplexIngestionMethod);
                
                _logger.info("Ingestion ended for exported unmanagedvolume {}", associatedVolume.getNativeGuid());
                if (null == blockObject)  {
                    
                    // TODO: handle this by not ingesting any backend vols; leave a nice message in success response
                    throw IngestionException.exceptions.generalVolumeException(
                            associatedVolume.getLabel(), "check the logs for more details");
                }
                
                context.getCreatedObjectMap().put(blockObject.getNativeGuid(), blockObject);
                context.getProcessedUnManagedVolumeMap().put(associatedVolume.getNativeGuid(), associatedVolume);
            } catch ( APIException ex ) {
                _logger.error(ex.getLocalizedMessage(), ex);
                throw ex;
            } catch ( Exception ex ) {
                _logger.error(ex.getLocalizedMessage(), ex);
                throw ex;
            }
        }
    }

    private void ingestBackendExportMasks(StorageSystem system,
            VirtualPool vPool, VirtualArray virtualArray, 
            TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, StringBuffer> taskStatusMap,
            VplexBackendIngestionContext context) {
        
        for (String unManagedVolumeGUID: context.getProcessedUnManagedVolumeMap().keySet()) {
            String objectGUID = unManagedVolumeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
            BlockObject processedBlockObject = context.getCreatedObjectMap().get(objectGUID);
            UnManagedVolume processedUnManagedVolume = context.getProcessedUnManagedVolumeMap().get(unManagedVolumeGUID);
            _logger.info("ingesting export mask(s) for unmanaged volume " + processedUnManagedVolume);

            try {
                if(processedBlockObject == null) {
                    _logger.warn("The ingested block object is null. Skipping ingestion of export masks for unmanaged volume {}", unManagedVolumeGUID);
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }
                
                if (processedUnManagedVolume.getUnmanagedExportMasks().isEmpty()) {
                    _logger.info("ingested block object {} has no unmanaged export masks", processedUnManagedVolume.getLabel());
                    continue;
                }
                
                // TODO happy path would just be one, but may need to account for more than one UEM
                String uemUri = processedUnManagedVolume.getUnmanagedExportMasks().iterator().next();
                UnManagedExportMask uem = _dbClient.queryObject(UnManagedExportMask.class, URI.create(uemUri));
                
                URI storageSystemUri = processedUnManagedVolume.getStorageSystemUri();
                StorageSystem associatedSystem =  systemMap.get(storageSystemUri.toString());

                IngestExportStrategy ingestStrategy =  ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);
                
                List<URI> initUris = new ArrayList<URI>();
                for (String uri : uem.getKnownInitiatorUris()) {
                    initUris.add(URI.create(uri));
                }
                List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initUris);
                
                boolean exportGroupCreated = false;
                ExportGroup exportGroup = this.findOrCreateExportGroup(system, associatedSystem, initiators, 
                        virtualArray.getId(), context.getBackendProject().getId(), tenant.getId(), 4, uem, exportGroupCreated); 
                
                VolumeExportIngestParam exportIngestParam = new VolumeExportIngestParam();
                exportIngestParam.setProject(context.getBackendProject().getId());
                exportIngestParam.setVarray(virtualArray.getId());
                exportIngestParam.setVpool(vPool.getId());
                
                List<URI> associatedVolumeUris = new ArrayList<URI>();
                for (UnManagedVolume umv : context.getUnmanagedBackendVolumes()) {
                    associatedVolumeUris.add(umv.getId());
                }
                exportIngestParam.setUnManagedVolumes(associatedVolumeUris);

                BlockObject blockObject = ingestStrategy.ingestExportMasks(processedUnManagedVolume, exportIngestParam, exportGroup, 
                        processedBlockObject, unManagedVolumesToBeDeleted, associatedSystem, exportGroupCreated, initiators);
                if (null == blockObject)  {
                    _logger.error("blockObject was null");
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }
                context.getIngestedObjects().add(blockObject);
                
                if (blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                    StringBuffer taskStatus = taskStatusMap
                            .get(processedUnManagedVolume.getNativeGuid());
                    String taskMessage = "";
                    if (taskStatus == null) {
                        // No task status found. Put in a default message.
                        taskMessage = String
                                .format("Not all the parent/replicas of unManagedVolume %s have been ingested",
                                        processedUnManagedVolume.getLabel());
                    } else {
                        taskMessage = taskStatus.toString();
                    }
                    _logger.error(taskMessage);
                }

                // Update the related objects if any after successful export
                // mask ingestion
                List<DataObject> updatedObjects = context.getUpdatedObjectMap().get(unManagedVolumeGUID);
                if (updatedObjects != null && !updatedObjects.isEmpty()) {
                    _dbClient.updateAndReindexObject(updatedObjects);
                }
            } catch ( APIException ex ) {
                _logger.error(ex.getLocalizedMessage(), ex);
                throw ex;
            } catch ( Exception ex ) {
                _logger.error(ex.getLocalizedMessage(), ex);
                throw ex;
            }
        }
    }

    private void createVplexMirrorObjects(VplexBackendIngestionContext context, Volume virtualVolume) {
        if (!context.getUnmanagedMirrors().isEmpty()) {
            _logger.info("creating VplexMirror object");
            for (Entry<UnManagedVolume, String> entry : context.getUnmanagedMirrors().entrySet()) {
                // find mirror and create a VplexMirror object
                BlockObject mirror = context.getCreatedObjectMap().get(entry.getKey().getNativeGuid()
                    .replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                        VolumeIngestionUtil.VOLUME));
                if (null != mirror) {
                    if (mirror instanceof Volume) {
                        Volume mirrorVolume = (Volume) mirror;
                        VplexMirror vplexMirror = new VplexMirror();
                        vplexMirror.setId(URIUtil.createId(VplexMirror.class));
                        StringSet associatedVolumes = new StringSet();
                        associatedVolumes.add(mirrorVolume.getId().toString());
                        vplexMirror.setAssociatedVolumes(associatedVolumes);
                        String[] devicePathParts = entry.getValue().split("/");
                        String deviceName = devicePathParts[devicePathParts.length - 1];
                        vplexMirror.setDeviceLabel(deviceName);
                        vplexMirror.setCapacity(mirrorVolume.getCapacity());
                        vplexMirror.setLabel(mirrorVolume.getLabel());
                        vplexMirror.setNativeId(entry.getValue());
                        vplexMirror.setProject(new NamedURI(
                                context.getFrontendProject().getId(), mirrorVolume.getLabel()));
                        vplexMirror.setProvisionedCapacity(mirrorVolume.getProvisionedCapacity());
                        vplexMirror.setSource(new NamedURI(virtualVolume.getId(), virtualVolume.getLabel()));
                        vplexMirror.setStorageController(mirrorVolume.getStorageController());
                        vplexMirror.setTenant(mirrorVolume.getTenant());
                        vplexMirror.setThinPreAllocationSize(mirrorVolume.getThinVolumePreAllocationSize());
                        vplexMirror.setThinlyProvisioned(mirrorVolume.getThinlyProvisioned());
                        vplexMirror.setVirtualArray(mirrorVolume.getVirtualArray());
                        vplexMirror.setVirtualPool(mirrorVolume.getVirtualPool());
                        
                        _dbClient.createObject(vplexMirror);
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
    
    private void setAssociatedVolumes(VplexBackendIngestionContext context, Volume virtualVolume) {
        Collection<BlockObject> createdObjects = context.getCreatedObjectMap().values();
        if (null != virtualVolume && virtualVolume instanceof Volume) {
            StringSet vols = new StringSet();
            for (BlockObject vol : createdObjects) {
                if (context.getBackendVolumeGuids().contains(vol.getNativeGuid())) {
                    vols.add(vol.getId().toString());
                }
            }
            virtualVolume.setAssociatedVolumes(vols);
        }
    }

    private void sortOutCloneAssociations(VplexBackendIngestionContext context, Volume vvolSource) {
        
        for (Entry<UnManagedVolume,UnManagedVolume> entry : context.getUnmanagedFullClones().entrySet()) {
            
            // TODO: account for distributed clone
            UnManagedVolume unBvolSource = context.getUnmanagedBackendVolumes().get(0);
            Volume bvolSource = (Volume) 
                    context.getCreatedObjectMap().get(unBvolSource.getNativeGuid().replace(
                            VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME));
            if (null == bvolSource) {
                throw IngestionException.exceptions.generalException("could not determine source backend volume for clone (full copy)");
            }
            if (null == bvolSource.getId()) {
                bvolSource.setId(URIUtil.createId(Volume.class));
            }

            // a full clone vvol is not an internal object, so make external
            Volume vvolTarget = (Volume) 
                context.getCreatedObjectMap().get(entry.getValue().getNativeGuid().replace(
                        VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME));
            
            // TODO: account for distributed clone
            Volume bvolTarget = null;
            if (null != vvolTarget.getAssociatedVolumes() && 
                    !vvolTarget.getAssociatedVolumes().isEmpty()) {
                String targetVvolUri = vvolTarget.getAssociatedVolumes().iterator().next();
                bvolTarget = _dbClient.queryObject(Volume.class, URI.create(targetVvolUri));
            }
            if (null == bvolTarget) {
                throw IngestionException.exceptions.generalException("could not determine target backend volume for clone (full copy)");
            }
            bvolTarget.setAssociatedSourceVolume(bvolSource.getId());
            bvolTarget.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            bvolTarget.setSyncActive(true);
            _dbClient.updateAndReindexObject(bvolTarget);
            
            NamedURI namedUri = new NamedURI(
                    context.getFrontendProject().getId(), vvolTarget.getLabel());
            vvolTarget.setProject(namedUri);
            _logger.info("set project named uri on {} to " 
                        + namedUri.toString(), vvolTarget.getLabel());
            vvolTarget.clearInternalFlags(Flag.INTERNAL_OBJECT);
            vvolTarget.setAssociatedSourceVolume(vvolSource.getId());
            vvolTarget.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            vvolTarget.setSyncActive(true);
            
            StringSet bFullCopies = new StringSet();
            bFullCopies.add(bvolTarget.getId().toString());
            bvolSource.setFullCopies(bFullCopies);
            bvolSource.setSyncActive(true);
            
            StringSet fFullCopies = new StringSet();
            fFullCopies.add(vvolTarget.getId().toString());
            vvolSource.setFullCopies(fFullCopies);
            vvolSource.setSyncActive(true);
        }
        

    }
    
    
    private void setFlags(VplexBackendIngestionContext context) {
        for (BlockObject o : context.getCreatedObjectMap().values()) {
            if (o instanceof Volume) {
                ((Volume) o).addInternalFlags(Flag.INTERNAL_OBJECT);
            }
        }
    }

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
                + array.getSerialNumber().substring(array.getSerialNumber().length()-4);
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
                    // TODO is this all we need to do to reuse an export group??
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
    
}
