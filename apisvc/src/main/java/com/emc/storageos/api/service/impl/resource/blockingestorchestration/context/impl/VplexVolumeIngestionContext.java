/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockIngestOrchestrator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BaseIngestionRequestContext.VolumeIngestionContextFactory;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;

public class VplexVolumeIngestionContext extends VplexBackendIngestionContext implements VolumeIngestionContext, IngestionRequestContext {

    private Map<String, VolumeIngestionContext> processedUnManagedVolumeMap;
    private Map<String, BlockObject> createdObjectMap;
    private Map<String, List<DataObject>> updatedObjectMap;
    private List<UnManagedVolume> unManagedVolumesToBeDeleted;

    private VolumeIngestionContext currentBackendVolumeIngestionContext;
    private Iterator<UnManagedVolume> backendVolumeUrisToProcessIterator;
    private List<VplexMirror> createdVplexMirrors;

    private String sourceClusterId;
    private String haClusterId;

    private List<String> errorMessages;
    
    private IngestionRequestContext parentRequestContext;

    // export ingestion related items
    private boolean exportGroupCreated = false;
    private ExportGroup exportGroup;
    private List<Initiator> deviceInitiators;
    private List<BlockObject> ingestedObjects;
    
    public VplexVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient, IngestionRequestContext parentRequestContext) {
        super(unManagedVolume, dbClient);
        
        this.parentRequestContext = parentRequestContext;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getUnManagedVolume()
     */
    @Override
    public UnManagedVolume getUnmanagedVolume() {
        return super.getUnmanagedVirtualVolume();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#isVolumeExported()
     */
    @Override
    public boolean isVolumeExported() {
        return VolumeIngestionUtil.checkUnManagedResourceAlreadyExported(getUnmanagedVolume());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#commit()
     */
    @Override
    public void commit() {
        setFlags();

        createVplexMirrorObjects();
        _dbClient.createObject(getCreatedVplexMirrors());

        _dbClient.createObject(getObjectsIngestedByExportProcessing());
        _dbClient.createObject(getObjectsToBeCreatedMap().values());
        _dbClient.createObject(getCreatedSnapshotMap().values());

        for (List<DataObject> dos : getObjectsToBeUpdatedMap().values()) {
            _dbClient.updateObject(dos);
        }
        _dbClient.updateObject(getUnManagedVolumesToBeDeleted());
        _logger.info(toStringDebug());
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        getObjectsIngestedByExportProcessing().clear();
        getObjectsToBeCreatedMap().clear();
        getCreatedSnapshotMap().clear();
        getObjectsToBeUpdatedMap().clear();
        getUnManagedVolumesToBeDeleted().clear();
        getCreatedVplexMirrors().clear();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getErrorMessages()
     */
    public List<String> getErrorMessages() {
        if (null == errorMessages) {
            errorMessages = new ArrayList<String>();
        }

        return errorMessages;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        iteratorIniterator();
        return backendVolumeUrisToProcessIterator.hasNext();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#next()
     */
    @Override
    public UnManagedVolume next() {
        iteratorIniterator();
        return backendVolumeUrisToProcessIterator.next();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        iteratorIniterator();
        backendVolumeUrisToProcessIterator.remove();
    }

    private void iteratorIniterator() {
        if (null == backendVolumeUrisToProcessIterator) {
            backendVolumeUrisToProcessIterator = this.getUnmanagedVolumesToIngest().iterator();
        }
    }
    

    protected void setCurrentUnmanagedVolume(UnManagedVolume unManagedVolume) {
        currentBackendVolumeIngestionContext = 
                VolumeIngestionContextFactory.getVolumeIngestionContext(unManagedVolume, _dbClient, this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCurrentUnmanagedVolume()
     */
    @Override
    public UnManagedVolume getCurrentUnmanagedVolume() {
        if (currentBackendVolumeIngestionContext == null) {
            return null;
        }
        
        return currentBackendVolumeIngestionContext.getUnmanagedVolume();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCurrentUnManagedVolumeUri()
     */
    @Override
    public URI getCurrentUnManagedVolumeUri() {
        return getCurrentUnmanagedVolume().getId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVolumeContext()
     */
    @Override
    public VolumeIngestionContext getVolumeContext() {
        return currentBackendVolumeIngestionContext;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVolumeContext(java.lang.String
     * )
     */
    @Override
    public VolumeIngestionContext getVolumeContext(String unmanagedVolumeGuid) {
        return getProcessedUnManagedVolumeMap().get(unmanagedVolumeGuid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getStorageSystem()
     */
    @Override
    public StorageSystem getStorageSystem() {
        URI storageSystemUri = getCurrentUnmanagedVolume().getStorageSystemUri();
        StorageSystem storageSystem = getStorageSystemCache().get(storageSystemUri.toString());
        if (null == storageSystem) {
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
            getStorageSystemCache().put(storageSystemUri.toString(), storageSystem);
        }

        return storageSystem;
    }

    public VirtualPool getHaVpool() {

        VirtualPool haVpool = null;
        StringMap haVarrayVpoolMap = parentRequestContext.getVpool().getHaVarrayVpoolMap();
        
        if (haVarrayVpoolMap != null && !haVarrayVpoolMap.isEmpty()) {
            String haVarrayStr = haVarrayVpoolMap.keySet().iterator().next();
            String haVpoolStr = haVarrayVpoolMap.get(haVarrayStr);
            if (haVpoolStr != null && !(haVpoolStr.equals(NullColumnValueGetter.getNullURI().toString()))) {
                haVpool = _dbClient.queryObject(VirtualPool.class, URI.create(haVpoolStr));
            }
        }
        
        return haVpool;
    }
    
    public VirtualArray getHaVarray() {
        
        VirtualArray haVarray = null;
        StringMap haVarrayVpoolMap = parentRequestContext.getVpool().getHaVarrayVpoolMap();
        if (haVarrayVpoolMap != null && !haVarrayVpoolMap.isEmpty()) {
            String haVarrayStr = haVarrayVpoolMap.keySet().iterator().next();
            if (haVarrayStr != null && !(haVarrayStr.equals(NullColumnValueGetter.getNullURI().toString()))) {
                haVarray = _dbClient.queryObject(VirtualArray.class, URI.create(haVarrayStr));
            }
        }
        
        return haVarray;
    }

    /**
     * @param sourceClusterId the sourceClusterId to set
     */
    public void setSourceClusterId(String sourceClusterId) {
        this.sourceClusterId = sourceClusterId;
    }

    /**
     * @param haClusterId the haClusterId to set
     */
    public void setHaClusterId(String haClusterId) {
        this.haClusterId = haClusterId;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVpool()
     */
    @Override
    public VirtualPool getVpool() {
        UnManagedVolume associatedVolume = getCurrentUnmanagedVolume(); 
        VirtualPool vpoolForThisVolume = parentRequestContext.getVpool();

        // get the backend volume cluster id
        String backendClusterId = VplexBackendIngestionContext.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_BACKEND_CLUSTER_ID.toString(),
                associatedVolume.getVolumeInformation());
        _logger.info("backend cluster id is " + backendClusterId);
        if (null != backendClusterId && null != haClusterId
                && backendClusterId.equals(haClusterId)) {
            if (null != getHaVpool()) {
                _logger.info("using high availability vpool " + getHaVpool().getLabel());
                vpoolForThisVolume = getHaVpool();
            }
        }

        // finally, double check for a separate mirror / continuous copies vpool
        if (getUnmanagedVplexMirrors().keySet().contains(associatedVolume)
                && vpoolForThisVolume.getMirrorVirtualPool() != null) {
            _logger.info("this associated volume is a mirror and separate mirror vpool is defined");
            VirtualPool mirrorVpool = _dbClient.queryObject(
                    VirtualPool.class, URI.create(vpoolForThisVolume.getMirrorVirtualPool()));
            _logger.info("using mirror vpool " + mirrorVpool.getLabel());
            vpoolForThisVolume = mirrorVpool;
        }
        
        return vpoolForThisVolume;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVarray()
     */
    @Override
    public VirtualArray getVarray() {

        UnManagedVolume associatedVolume = getCurrentUnmanagedVolume(); 
        VirtualArray varrayForThisVolume = parentRequestContext.getVarray();

        // get the backend volume cluster id
        String backendClusterId = VplexBackendIngestionContext.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_BACKEND_CLUSTER_ID.toString(),
                associatedVolume.getVolumeInformation());
        _logger.info("backend cluster id is " + backendClusterId);
        if (null != backendClusterId && null != haClusterId
                && backendClusterId.equals(haClusterId)) {
            _logger.info("using high availability varray " + getHaVarray().getLabel());
            varrayForThisVolume = getHaVarray();
        }

        return varrayForThisVolume;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProject()
     */
    @Override
    public Project getProject() {
        // determine the correct project to use with this volume:
        // the backend volumes have the vplex backend Project, but
        // the rest have the same Project as the virtual volume.
        Project project = getUnmanagedBackendVolumes().contains(getCurrentUnmanagedVolume()) ?
                getBackendProject() : getFrontendProject();
                
        return project;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getTenant()
     */
    @Override
    public TenantOrg getTenant() {
        return parentRequestContext.getTenant();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVplexIngestionMethod()
     */
    @Override
    public String getVplexIngestionMethod() {
        return parentRequestContext.getVplexIngestionMethod();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getSystemMap()
     */
    @Override
    public Map<String, StorageSystem> getStorageSystemCache() {
        return parentRequestContext.getStorageSystemCache();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getSystemCache()
     */
    @Override
    public List<URI> getExhaustedStorageSystems() {
        return parentRequestContext.getExhaustedStorageSystems();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getPoolCache()
     */
    @Override
    public List<URI> getExhaustedPools() {
        return parentRequestContext.getExhaustedPools();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getUnManagedVolumesToBeDeleted()
     */
    @Override
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted() {
        if (null == unManagedVolumesToBeDeleted) {
            unManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        }

        return unManagedVolumesToBeDeleted;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getTaskStatusMap()
     */
    @Override
    public Map<String, StringBuffer> getTaskStatusMap() {
        return parentRequestContext.getTaskStatusMap();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedUnManagedVolumeMap
     * ()
     */
    @Override
    public Map<String, VolumeIngestionContext> getProcessedUnManagedVolumeMap() {
        if (null == processedUnManagedVolumeMap) {
            processedUnManagedVolumeMap = new HashMap<String, VolumeIngestionContext>();
        }

        return processedUnManagedVolumeMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedUnManagedVolume
     * (java.lang.String)
     */
    @Override
    public UnManagedVolume getProcessedUnManagedVolume(String nativeGuid) {
        VolumeIngestionContext volumeContext = getProcessedUnManagedVolumeMap().get(nativeGuid);
        if (null != volumeContext) {
            return volumeContext.getUnmanagedVolume();
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedVolumeContext(java
     * .lang.String)
     */
    @Override
    public VolumeIngestionContext getProcessedVolumeContext(String nativeGuid) {
        return getProcessedUnManagedVolumeMap().get(nativeGuid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getIngestedObjects()
     */
    @Override
    public List<BlockObject> getObjectsIngestedByExportProcessing() {
        if (null == ingestedObjects) {
            ingestedObjects = new ArrayList<BlockObject>();
        }

        return ingestedObjects;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#isExportGroupCreated()
     */
    @Override
    public boolean isExportGroupCreated() {
        return exportGroupCreated;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setExportGroupCreated(boolean)
     */
    @Override
    public void setExportGroupCreated(boolean exportGroupCreated) {
        this.exportGroupCreated = exportGroupCreated;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExportGroup()
     */
    @Override
    public ExportGroup getExportGroup() {
        return exportGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setExportGroup(com.emc.storageos
     * .db.client.model.ExportGroup)
     */
    @Override
    public void setExportGroup(ExportGroup exportGroup) {
        this.exportGroup = exportGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getHost()
     */
    @Override
    public URI getHost() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setHost(java.net.URI)
     */
    @Override
    public void setHost(URI host) {
        // no-op; vplex ingestion only uses device initiators for export
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCluster()
     */
    @Override
    public URI getCluster() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setCluster(java.net.URI)
     */
    @Override
    public void setCluster(URI cluster) {
        // no-op; vplex ingestion only uses device initiators for export
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getDeviceInitiators()
     */
    @Override
    public List<Initiator> getDeviceInitiators() {
        return deviceInitiators;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setDeviceInitiators(java.util
     * .List)
     */
    @Override
    public void setDeviceInitiators(List<Initiator> deviceInitiators) {
        this.deviceInitiators = deviceInitiators;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProcessedBlockObject(java
     * .lang.String)
     */
    @Override
    public BlockObject getProcessedBlockObject(String unmanagedVolumeGuid) {
        String objectGUID = unmanagedVolumeGuid.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
        return getObjectsToBeCreatedMap().get(objectGUID);
    }

    /**
     * Returns the Map of created objects, used
     * by the general ingestion framework.
     * 
     * @return the created object Map
     */
    public Map<String, BlockObject> getObjectsToBeCreatedMap() {
        if (null == createdObjectMap) {
            createdObjectMap = new HashMap<String, BlockObject>();
        }
        
        return createdObjectMap;
    }

    /**
     * Returns the Map of created objects, used
     * by the general ingestion framework.
     * 
     * @return the created object Map
     */
    public List<VplexMirror> getCreatedVplexMirrors() {
        if (null == createdVplexMirrors) {
            createdVplexMirrors = new ArrayList<VplexMirror>();
        }
        
        return createdVplexMirrors;
    }

    /**
     * Returns the Map of updated objects, used
     * by the general ingestion framework.
     * 
     * @return the updated object Map
     */
    public Map<String, List<DataObject>> getObjectsToBeUpdatedMap() {
        if (null == updatedObjectMap) {
            updatedObjectMap = new HashMap<String, List<DataObject>>();
        }
        
        return updatedObjectMap;
    }

    /**
     * Updates any internal flags on the ingested backend resources.
     * 
     * @param context the VplexBackendIngestionContext
     */
    private void setFlags() {
        // set internal object flag on any backend volumes
        for (BlockObject o : getObjectsToBeCreatedMap().values()) {
            if (getBackendVolumeGuids().contains(o.getNativeGuid())) {
                _logger.info("setting INTERNAL_OBJECT flag on " + o.getLabel());
                o.addInternalFlags(Flag.INTERNAL_OBJECT);
            }
        }

        // Look to see if the backend ingestion resulted in the creation of a
        // BlockSnapshot instance, which would occur if the backend volume is
        // also a snapshot target volume. It is possible that the snapshot is
        // still marked internal if the VPLEX volume built on the snapshot
        // is ingested after the VPLEX volume whose backend volume is the
        // snapshot source volume. If the snapshot source is set, then snapshot
        // and source are fully ingested and we need to make sure the snapshot
        // is public.
        for (BlockSnapshot snapshot : getCreatedSnapshotMap().values()) {
            if (!NullColumnValueGetter.isNullValue(snapshot.getSourceNativeId())) {
                snapshot.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
            }
        }
    }

    /**
     * Create a VplexMirror database object if a VPLEX native mirror is present.
     * This should be called after the parent virtual volume has already been ingested.
     * 
     * @param context the VplexBackendIngestionContext
     * @param virtualVolume the ingested virtual volume's Volume object.
     */
    private void createVplexMirrorObjects() {
        if (!getUnmanagedVplexMirrors().isEmpty()) {
            Volume virtualVolume = (Volume) parentRequestContext.getProcessedBlockObject(
                    getUnmanagedVirtualVolume().getNativeGuid());
            _logger.info("creating VplexMirror object for virtual volume " + virtualVolume.getLabel());
            for (Entry<UnManagedVolume, String> entry : getUnmanagedVplexMirrors().entrySet()) {
                // find mirror and create a VplexMirror object
                BlockObject mirror = getObjectsToBeCreatedMap().get(entry.getKey().getNativeGuid()
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
                        vplexMirror.setAllocatedCapacity(mirrorVolume.getAllocatedCapacity());
                        vplexMirror.setProvisionedCapacity(mirrorVolume.getProvisionedCapacity());
                        vplexMirror.setSource(new NamedURI(virtualVolume.getId(), virtualVolume.getLabel()));
                        vplexMirror.setStorageController(virtualVolume.getStorageController());
                        vplexMirror.setTenant(mirrorVolume.getTenant());
                        vplexMirror.setThinPreAllocationSize(mirrorVolume.getThinVolumePreAllocationSize());
                        vplexMirror.setThinlyProvisioned(mirrorVolume.getThinlyProvisioned());
                        vplexMirror.setVirtualArray(mirrorVolume.getVirtualArray());
                        vplexMirror.setVirtualPool(mirrorVolume.getVirtualPool());

                        // set the associated volume for this VplexMirror
                        StringSet associatedVolumes = new StringSet();
                        associatedVolumes.add(mirrorVolume.getId().toString());
                        vplexMirror.setAssociatedVolumes(associatedVolumes);

                        // VplexMirror will have the same project
                        // as the virtual volume (i.e., the front-end project)
                        // but the mirror backend will have the backend project
                        vplexMirror.setProject(new NamedURI(
                                getFrontendProject().getId(), mirrorVolume.getLabel()));
                        mirrorVolume.setProject(new NamedURI(
                                getBackendProject().getId(), mirrorVolume.getLabel()));

                        // update flags on mirror volume
                        List<DataObject> updatedObjects =
                                getObjectsToBeUpdatedMap().get(mirrorVolume.getNativeGuid());
                        if (updatedObjects == null) {
                            updatedObjects = new ArrayList<DataObject>();
                            getObjectsToBeUpdatedMap().put(mirrorVolume.getNativeGuid(), updatedObjects);
                        }
                        VolumeIngestionUtil.clearInternalFlags(mirrorVolume, updatedObjects, _dbClient);
                        // VPLEX backend volumes should still have the INTERNAL_OBJECT flag
                        mirrorVolume.addInternalFlags(Flag.INTERNAL_OBJECT);

                        // deviceLabel will be the very last part of the native guid
                        String[] devicePathParts = entry.getValue().split("/");
                        String deviceName = devicePathParts[devicePathParts.length - 1];
                        vplexMirror.setDeviceLabel(deviceName);

                        // save the new VplexMirror & persist backend & updated objects
                        getCreatedVplexMirrors().add(vplexMirror);

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
     * Returns a detailed report on the state of everything in this context,
     * useful for debugging.
     * 
     * @return a detailed report on the context
     */
    public String toStringDebug() {
        StringBuilder s = new StringBuilder("\n\nVplexBackendIngestionContext \n\t ");
        s.append("unmanaged virtual volume: ").append(getUnmanagedVirtualVolume()).append(" \n\t ");
        s.append("unmanaged backend volume(s): ").append(this.getUnmanagedBackendVolumes()).append(" \n\t ");
        s.append("unmanaged snapshots: ").append(this.getUnmanagedSnapshots()).append(" \n\t ");
        s.append("unmanaged full clones: ").append(this.getUnmanagedVplexClones()).append(" \n\t ");
        s.append("unmanaged backend only clones: ").append(this.getUnmanagedBackendOnlyClones()).append(" \n\t ");
        s.append("unmanaged mirrors: ").append(this.getUnmanagedVplexMirrors()).append(" \n\t ");
        s.append("ingested objects: ").append(this.getObjectsIngestedByExportProcessing()).append(" \n\t ");
        s.append("created objects map: ").append(this.getObjectsToBeCreatedMap()).append(" \n\t ");
        s.append("updated objects map: ");
        for (Entry<String, List<DataObject>> e : this.getObjectsToBeUpdatedMap().entrySet()) {
            s.append(e.getKey()).append(": ");
            for (DataObject o : e.getValue()) {
                s.append(o.getLabel()).append("; ");
            }
        }
        s.append(" \n\t ");
        s.append("processed unmanaged volumes: ").append(this.getProcessedUnManagedVolumeMap()).append("\n");
        return s.toString();
    }

    @Override
    public String toString() {
        if (_logger.isDebugEnabled()) {
            return toStringDebug();
        }

        return super.toString();
    }

}
