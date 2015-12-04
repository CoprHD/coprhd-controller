package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BasicIngestionRequestContext.VolumeIngestionContextFactory;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
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
    private List<BlockObject> ingestedObjects;
    private List<UnManagedVolume> unManagedVolumesToBeDeleted;

    private List<String> errorMessages;
    
    private IngestionRequestContext parentRequestContext;

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
        _dbClient.createObject(getIngestedObjects());
        _dbClient.createObject(getCreatedObjectMap().values());
        _dbClient.createObject(getCreatedSnapshotMap().values());
        for (List<DataObject> dos : getUpdatedObjectMap().values()) {
            _dbClient.persistObject(dos);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        getIngestedObjects().clear();
        getCreatedObjectMap().clear();
        getCreatedSnapshotMap().clear();
        getUpdatedObjectMap().clear();
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

    
    private Iterator<UnManagedVolume> backendVolumeUrisToProcessIterator;
    private VolumeIngestionContext currentBackendVolumeIngestionContext;

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
        StorageSystem storageSystem = getSystemMap().get(storageSystemUri.toString());
        if (null == storageSystem) {
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
            getSystemMap().put(storageSystemUri.toString(), storageSystem);
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

    private String sourceClusterId;
    private String haClusterId;

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
    public Map<String, StorageSystem> getSystemMap() {
        return parentRequestContext.getSystemMap();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getSystemCache()
     */
    @Override
    public List<URI> getSystemCache() {
        return parentRequestContext.getSystemCache();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getPoolCache()
     */
    @Override
    public List<URI> getPoolCache() {
        return parentRequestContext.getPoolCache();
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
    public List<BlockObject> getIngestedObjects() {
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
        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setExportGroupCreated(boolean)
     */
    @Override
    public void setExportGroupCreated(boolean exportGroupCreated) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExportGroup()
     */
    @Override
    public ExportGroup getExportGroup() {
        // TODO Auto-generated method stub
        return null;
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
        // TODO Auto-generated method stub

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

    private List<Initiator> deviceInitiators;
    
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
        return getCreatedObjectMap().get(objectGUID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#rollbackAll()
     */
    @Override
    public void rollbackAll() {
        try {
            for (VolumeIngestionContext volumeContext : getProcessedUnManagedVolumeMap().values()) {
                volumeContext.rollback();
            }
        } catch (Exception ex) {
            _logger.error("failure during rollback", ex);
        }
    }

    /**
     * Returns the Map of created objects, used
     * by the general ingestion framework.
     * 
     * @return the created object Map
     */
    public Map<String, BlockObject> getCreatedObjectMap() {
        return createdObjectMap;
    }

    /**
     * Returns the Map of updated objects, used
     * by the general ingestion framework.
     * 
     * @return the updated object Map
     */
    public Map<String, List<DataObject>> getUpdatedObjectMap() {
        return updatedObjectMap;
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
        s.append("ingested objects: ").append(this.getIngestedObjects()).append(" \n\t ");
        s.append("created objects map: ").append(this.getCreatedObjectMap()).append(" \n\t ");
        s.append("updated objects map: ");
        for (Entry<String, List<DataObject>> e : this.getUpdatedObjectMap().entrySet()) {
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
