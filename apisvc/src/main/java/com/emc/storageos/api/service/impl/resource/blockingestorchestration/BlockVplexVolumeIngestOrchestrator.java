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
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
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
            Map<String, StringBuffer> taskStatusMap) throws IngestionException {
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

        VplexBackendIngestionContext context = new VplexBackendIngestionContext(unManagedVolume, _dbClient);
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
                        taskStatusMap, context);

                ingestBackendExportMasks(system, vPool, virtualArray, 
                        tenant, unManagedVolumesToBeDeleted, 
                        taskStatusMap, context);

                handleFinalDetails(context);
                handlePersistence(context);
            }
        } catch (Exception ex) {
            _logger.error("error during VPLEX backend ingestion: ", ex);
            
            // remove unmanaged backend vols from ones to be deleted in case
            // they were marked inactive during export mask ingestion
            if (null != context.getUnmanagedBackendVolumes()) {
                boolean removed = unManagedVolumesToBeDeleted.removeAll(context.getUnmanagedBackendVolumes());
                _logger.info("were backend volumes prevented for deletion? " + removed);
            }
            // TODO: also need to rollback any new export group or export mask creation
            
            throw IngestionException.exceptions.failedToIngestVplexBackend(ex.getLocalizedMessage());
        }

        _logger.info("About to ingest the actual VPLEX virtual volume...");
        T virtualVolume = super.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool, virtualArray, project, tenant,
                unManagedVolumesToBeDeleted, createdObjectMap, updatedObjectMap, unManagedVolumeExported, clazz, taskStatusMap);
        
        try {
            setAssociatedVolumes(context, (Volume) virtualVolume);
            createVplexMirrorObjects(context, (Volume) virtualVolume);
        } catch (Exception ex) {
            _logger.error("error during VPLEX backend ingestion wrap up: ", ex);
            
            // remove unmanaged backend vols from ones to be deleted in case
            // they were marked inactive during export mask ingestion
            if (null != context.getUnmanagedBackendVolumes()) {
                boolean removed = unManagedVolumesToBeDeleted.removeAll(context.getUnmanagedBackendVolumes());
                _logger.info("were backend volumes prevented for deletion? " + removed);
            }
        }

        return virtualVolume;
    }

    private void validateContext(VirtualPool vPool, 
            TenantOrg tenant, VplexBackendIngestionContext context) throws Exception {
        UnManagedVolume unManagedVirtualVolume = context.getUnmanagedVirtualVolume();
        List<UnManagedVolume> unManagedBackendVolumes = context.getUnmanagedBackendVolumes();
        _logger.info("validating the backend volumes: " + unManagedBackendVolumes);
        
        _logger.info("validating if we have found enough backend volumes for ingestion");
        boolean isLocal = VolumeIngestionUtil.isVplexLocal(unManagedVirtualVolume);
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
        }
        
        // TODO more validation - needs further analysis
        // check if selected vpool can contain all the backend volumes

        
        
        // check for Quotas
        long unManagedVolumesCapacity = VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(_dbClient, unManagedBackendVolumeUris);
        _logger.info("UnManagedVolume provisioning quota validation successful");
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vPool, context.getBackendProject(), tenant, unManagedVolumesCapacity, "volume");
        VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(unManagedBackendVolumeUris, vPool, _dbClient);
    }

    private void ingestBackendVolumes(List<URI> systemCache,
            List<URI> poolCache, VirtualPool vPool, VirtualArray virtualArray, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, StringBuffer> taskStatusMap,
            VplexBackendIngestionContext context) throws Exception {

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
                        VolumeIngestionUtil.getBlockObjectClass(associatedVolume), taskStatusMap);
                
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
                
                ExportGroup exportGroup = this.createExportGroup(system, associatedSystem, initiators, 
                        virtualArray.getId(), context.getBackendProject().getId(), tenant.getId(), 4, uem); 
                boolean exportGroupCreated = exportGroup != null;
                
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
            for (UnManagedVolume umv : context.getUnmanagedMirrors()) {
                // find mirror and create a VplexMirror object
                BlockObject mirror = context.getCreatedObjectMap().get(umv.getNativeGuid()
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
                        String deviceName = PropertySetterUtil.extractValueFromStringSet(
                                SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(),
                                    context.getUnmanagedVirtualVolume().getVolumeInformation());
                        vplexMirror.setDeviceLabel(deviceName);
                        vplexMirror.setCapacity(mirrorVolume.getCapacity());
                        vplexMirror.setLabel(mirrorVolume.getLabel());
                        vplexMirror.setNativeId(umv.getNativeGuid());
                        vplexMirror.setProject(new NamedURI(
                                context.getFrontendProject().getId(), mirrorVolume.getLabel()));
                        vplexMirror.setProvisionedCapacity(mirrorVolume.getProvisionedCapacity());
                        vplexMirror.setSource(new NamedURI(virtualVolume.getId(), virtualVolume.getLabel()));
                        vplexMirror.setStorageController(mirrorVolume.getStorageController());
                        vplexMirror.setTenant(mirrorVolume.getTenant());
                        vplexMirror.setThinPreAllocationSize(mirrorVolume.getThinVolumePreAllocationSize());
                        vplexMirror.setThinlyProvisioned(vplexMirror.getThinlyProvisioned());
                        vplexMirror.setVirtualArray(vplexMirror.getVirtualArray());
                        vplexMirror.setVirtualPool(vplexMirror.getVirtualPool());
                        
                        _dbClient.createObject(vplexMirror);
                    }
                }
            }
        }
    }
    
    private void setAssociatedVolumes(VplexBackendIngestionContext context, Volume virtualVolume) {
        if (null != virtualVolume && virtualVolume instanceof Volume) {
            Collection<BlockObject> createdObjects = context.getCreatedObjectMap().values();
            StringSet vols = new StringSet();
            for (BlockObject vol : createdObjects) {
                if (context.getBackendVolumeGuids().contains(vol.getNativeGuid())) {
                    vols.add(vol.getId().toString());
                }
            }
            ((Volume) virtualVolume).setAssociatedVolumes(vols);
            // TODO: set associated vol on clone
        }

    }

    private void handleFinalDetails(VplexBackendIngestionContext context) {
        for (BlockObject o : context.getCreatedObjectMap().values()) {
            if (o instanceof Volume) {
                ((Volume) o).addInternalFlags(Flag.INTERNAL_OBJECT);
            }
        }
        
        for (UnManagedVolume vol : context.getUnmanagedFullClones().values()) {
            // a full clone vvol is not an internal object, so make external
            BlockObject bo = 
                context.getCreatedObjectMap().get(vol.getNativeGuid().replace(
                        VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME));
            if (bo instanceof Volume) {
                NamedURI namedUri = new NamedURI(
                        context.getFrontendProject().getId(), ((Volume) bo).getLabel());
                ((Volume) bo).setProject(namedUri);
                _logger.info("set project named uri on {} to " 
                            + namedUri.toString(), bo.getLabel());
                ((Volume) bo).clearInternalFlags(Flag.INTERNAL_OBJECT);
            }
        }
        
        _logger.info(context.toString());
    }

    private void handlePersistence(VplexBackendIngestionContext context) {
        _dbClient.createObject(context.getIngestedObjects());
        _dbClient.createObject(context.getCreatedObjectMap().values());
        for (List<DataObject> dos : context.getUpdatedObjectMap().values()) {
            _dbClient.persistObject(dos);
        }
        _dbClient.persistObject(context.getProcessedUnManagedVolumeMap().values());
    }

    
    // THE FOLLOWING TWO METHODS ARE BASICALLY COPIED FROM VplexBackendManager 
    // with some tweakage, probably should be consolidated somehow

    /**
     * Create an ExportGroup.
     * @param vplex -- VPLEX StorageSystem
     * @param array -- Array StorageSystem
     * @param initiators -- Collection<Initiator> representing VPLEX back-end ports.
     * @param virtualArrayURI
     * @param projectURI
     * @param tenantURI
     * @param numPaths Value of maxPaths to be put in ExportGroup
     * @param exportMask IFF non-null, will add the exportMask to the Export Group.
     * @return newly created ExportGroup persisted in DB.
     */
    ExportGroup createExportGroup(StorageSystem vplex,
            StorageSystem array, Collection<Initiator> initiators, 
            URI virtualArrayURI,
            URI projectURI, URI tenantURI, int numPaths, 
            UnManagedExportMask exportMask) {
        String groupName = getExportGroupName(vplex, array) 
                + "_" + UUID.randomUUID().toString().substring(28);
        if (exportMask != null) {
            String arrayName = array.getSystemType().replace("block", "") 
                    + array.getSerialNumber().substring(array.getSerialNumber().length()-4);
            groupName = exportMask.getMaskName() + "_" + arrayName;
        }
        
        // No existing group has the mask, let's create one.
        ExportGroup exportGroup = new ExportGroup();
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

//        // If we have an Export Mask, add it into the Export Group.
//        if (exportMask != null) {
//            exportGroup.addExportMask(exportMask.getId());
//        }
        
        // Persist the ExportGroup
        _dbClient.createObject(exportGroup);  // TODO probably want to be able to roll back
        _logger.info(String.format("Returning new ExportGroup %s", exportGroup.getLabel()));
        return exportGroup;
        
    }
    
    /**
     * Returns the ExportGroup name to be used between a particular VPlex and underlying Storage Array.
     * It is based on the serial numbers of the Vplex and Array. Therefore the same ExportGroup name
     * will always be used, and it always starts with "VPlex".
     * @param vplex
     * @param array
     * @return
     */
    private String getExportGroupName(StorageSystem vplex, StorageSystem array) {
        // Unfortunately, using the full VPlex serial number with the Array serial number
        // proves to be quite lengthy! We can run into issues on SMIS where
        // max length (represented as STOR_DEV_GROUP_MAX_LEN) is 64 characters.
        // Not to mention, other steps append to this name too.
        // So lets chop everything but the last 4 digits from both serial numbers.
        // This should be unique enough.
        int endIndex = vplex.getSerialNumber().length();
        int beginIndex = endIndex - 4;
        String modfiedVPlexSerialNumber = vplex.getSerialNumber().substring(beginIndex, endIndex);
        
        endIndex = array.getSerialNumber().length();
        beginIndex = endIndex - 4;
        String modfiedArraySerialNumber = array.getSerialNumber().substring(beginIndex, endIndex);
        
        return String.format("VPlex_%s_%s", modfiedVPlexSerialNumber, modfiedArraySerialNumber);
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
