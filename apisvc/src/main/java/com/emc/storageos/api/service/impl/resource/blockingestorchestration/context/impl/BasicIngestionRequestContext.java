package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * IngestionRequestContext is instantiated once per user request
 * for ingestion of UnManagedVolumes in the UnManagedVolume service.
 * It can be used for ingestion of both exported and unexported volumes.
 * 
 * This class implements Iterator<UnManagedVolume> and holds a nested
 * iterator of URI for these UnManagedVolumes, so the UnManagedVolumeService
 * can iterate this class directly.  Each UnManagedVolume is
 * instantiated when next is called, and this ensure the current unmanaged
 * volume is set correctly and the current VolumeIngestionContext is
 * created for the currently iterating volume.
 * 
 * This class includes a VolumeIngestionContextFactory that will 
 * creates the correct VolumeIngestionContext object for the current
 * volume based on the UnManagedVolume type.
 * 
 * This class holds all the tracking collections for persistence of
 * ingested unmanaged objects at the end of a successful ingestion run.
 * 
 * And finally, it has a rollbackAll method which can rollback all
 * VolumeIngestionContexts in the event of an overall failure (but
 * generally rollback would only happen on an individual volume if
 * something about that volume's ingestion failed).
 */
public class BasicIngestionRequestContext implements IngestionRequestContext {

    private static Logger _logger = LoggerFactory.getLogger(BasicIngestionRequestContext.class);
    private DbClient _dbClient;

    private Iterator<URI> unManagedVolumeUrisToProcessIterator;
    private Map<String, VolumeIngestionContext> processedUnManagedVolumeMap;

    private VirtualPool vpool; 
    private VirtualArray virtualArray; 
    private Project project; 
    private TenantOrg tenant;
    private String vplexIngestionMethod;
    private Map<String, StringBuffer> taskStatusMap;

    private Map<String, StorageSystem> systemMap;
    private List<URI> systemCache;
    private List<URI> poolCache;

//     private List<UnManagedVolume> unManagedVolumesSuccessfullyProcessed;  (think this is confusing wording for to be deleted)
    private List<UnManagedVolume> unManagedVolumesToBeDeleted;
    private Map<String, BlockObject> createdObjectMap; 
    private Map<String, List<DataObject>> updatedObjectMap; 

    private VolumeIngestionContext currentVolumeIngestionContext;
    private URI currentUnManagedVolumeUri;

    // export ingestion related items
    private boolean exportGroupCreated = false;
    private ExportGroup exportGroup;
    private URI host;
    private URI cluster;
    private List<Initiator> deviceInitiators;
    List<BlockObject> ingestedObjects;

    public BasicIngestionRequestContext(DbClient dbClient, List<URI> unManagedVolumeUrisToProcess, VirtualPool vpool, 
            VirtualArray virtualArray, Project project, TenantOrg tenant, String vplexIngestionMethod) {
        this._dbClient = dbClient;
        this.unManagedVolumeUrisToProcessIterator = unManagedVolumeUrisToProcess.iterator();
        this.vpool = vpool;
        this.virtualArray = virtualArray;
        this.project = project;
        this.tenant = tenant;
        this.vplexIngestionMethod = vplexIngestionMethod;
    }

    @Override
    public boolean hasNext() {
        return unManagedVolumeUrisToProcessIterator.hasNext();
    }

    @Override
    public UnManagedVolume next() {
        currentUnManagedVolumeUri = unManagedVolumeUrisToProcessIterator.next();
        UnManagedVolume currentVolume = _dbClient.queryObject(UnManagedVolume.class, currentUnManagedVolumeUri);
        if (null != currentVolume) {
            this.setCurrentUnmanagedVolume(currentVolume);
        }
        return currentVolume;
    }

    @Override
    public void remove() {
        unManagedVolumeUrisToProcessIterator.remove();
    }

    /**
     * Instantiates the correct VolumeIngestionContext object
     * for the current volume, based on the UnManagedVolume type.
     */
    private static class VolumeIngestionContextFactory {
        
        public static VolumeIngestionContext getVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient) {
            if (null == unManagedVolume) {
                return null;
            }
            
            if (VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume)) {
                return new RPVolumeIngestionContext(unManagedVolume, dbClient);
            } else if (VolumeIngestionUtil.isVplexVolume(unManagedVolume)) {
                return new VplexVolumeIngestionContext(unManagedVolume, dbClient);
            } else {
                return new BlockVolumeIngestionContext(unManagedVolume, dbClient);
            }
        }
        
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setCurrentUnmanagedVolume(com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume)
     */
    @Override
    public void setCurrentUnmanagedVolume(UnManagedVolume unManagedVolume) {
        currentVolumeIngestionContext = 
                VolumeIngestionContextFactory.getVolumeIngestionContext(unManagedVolume, _dbClient);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getCurrentUnmanagedVolume()
     */
    @Override
    public UnManagedVolume getCurrentUnmanagedVolume() {
        if (currentVolumeIngestionContext == null) {
            return null;
        }
        
        return currentVolumeIngestionContext.getUnmanagedVolume();
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getCurrentUnManagedVolumeUri()
     */
    @Override
    public URI getCurrentUnManagedVolumeUri() {
        return currentUnManagedVolumeUri;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getVolumeContext()
     */
    @Override
    public VolumeIngestionContext getVolumeContext() {
        return currentVolumeIngestionContext;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getVolumeContext(java.lang.String)
     */
    @Override
    public VolumeIngestionContext getVolumeContext(String unmanagedVolumeGuid) {
        return getProcessedUnManagedVolumeMap().get(unmanagedVolumeGuid);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getStorageSystem()
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

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getVpool()
     */
    @Override
    public VirtualPool getVpool() {
        return vpool;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setVpool(com.emc.storageos.db.client.model.VirtualPool)
     */
    @Override
    public void setVpool(VirtualPool vpool) {
        this.vpool = vpool;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getVarray()
     */
    @Override
    public VirtualArray getVarray() {
        return virtualArray;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setVarray(com.emc.storageos.db.client.model.VirtualArray)
     */
    @Override
    public void setVarray(VirtualArray varray) {
        this.virtualArray = varray;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProject()
     */
    @Override
    public Project getProject() {
        return project;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setProject(com.emc.storageos.db.client.model.Project)
     */
    @Override
    public void setProject(Project project) {
        this.project = project;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getTenant()
     */
    @Override
    public TenantOrg getTenant() {
        return tenant;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getVplexIngestionMethod()
     */
    @Override
    public String getVplexIngestionMethod() {
        return vplexIngestionMethod;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getSystemMap()
     */
    @Override
    public Map<String, StorageSystem> getSystemMap() {
        if (null == systemMap) {
            systemMap = new HashMap<String, StorageSystem>();
        }
        
        return systemMap;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getSystemCache()
     */
    @Override
    public List<URI> getSystemCache() {
        if (null == systemCache) {
            systemCache = new ArrayList<URI>();
        }
        
        return systemCache;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getPoolCache()
     */
    @Override
    public List<URI> getPoolCache() {
        if (null == poolCache) {
            poolCache = new ArrayList<URI>();
        }
        
        return poolCache;
    }
//
//    /**
//     * @return the unManagedVolumesSuccessfullyProcessed
//     */
//    public List<UnManagedVolume> getUnManagedVolumesSuccessfullyProcessed() {
//        if (null == unManagedVolumesSuccessfullyProcessed) {
//            unManagedVolumesSuccessfullyProcessed = new ArrayList<UnManagedVolume>();
//        }
//        
//        return unManagedVolumesSuccessfullyProcessed;
//    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getUnManagedVolumesToBeDeleted()
     */
    @Override
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted() {
        if (null == unManagedVolumesToBeDeleted) {
            unManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        }

        return unManagedVolumesToBeDeleted;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getCreatedObjectMap()
     */
    @Override
    public Map<String, BlockObject> getCreatedObjectMap() {
        if (null == createdObjectMap) {
            createdObjectMap = new HashMap<String, BlockObject>();
        }
        
        return createdObjectMap;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getUpdatedObjectMap()
     */
    @Override
    public Map<String, List<DataObject>> getUpdatedObjectMap() {
        if (null == updatedObjectMap) {
            updatedObjectMap = new HashMap<String, List<DataObject>>();
        }
        
        return updatedObjectMap;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getTaskStatusMap()
     */
    @Override
    public Map<String, StringBuffer> getTaskStatusMap() {
        if (null == taskStatusMap) {
            taskStatusMap = new HashMap<String, StringBuffer>();
        }
        
        return taskStatusMap;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedUnManagedVolumeMap()
     */
    @Override
    public Map<String, VolumeIngestionContext> getProcessedUnManagedVolumeMap() {
        if (null == processedUnManagedVolumeMap) {
            processedUnManagedVolumeMap = new HashMap<String, VolumeIngestionContext>();
        }
        
        return processedUnManagedVolumeMap;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedUnManagedVolume(java.lang.String)
     */
    @Override
    public UnManagedVolume getProcessedUnManagedVolume(String nativeGuid) {
        VolumeIngestionContext volumeContext = getProcessedUnManagedVolumeMap().get(nativeGuid);
        if (null != volumeContext) {
            return volumeContext.getUnmanagedVolume();
        }
        
        return null;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedVolumeContext(java.lang.String)
     */
    @Override
    public VolumeIngestionContext getProcessedVolumeContext(String nativeGuid) {
        return getProcessedUnManagedVolumeMap().get(nativeGuid);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getIngestedObjects()
     */
    @Override
    public List<BlockObject> getIngestedObjects() {
        if (null == ingestedObjects) {
            ingestedObjects = new ArrayList<BlockObject>();
        }
        
        return ingestedObjects;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#isExportGroupCreated()
     */
    @Override
    public boolean isExportGroupCreated() {
        return exportGroupCreated;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setExportGroupCreated(boolean)
     */
    @Override
    public void setExportGroupCreated(boolean exportGroupCreated) {
        this.exportGroupCreated = exportGroupCreated;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getExportGroup()
     */
    @Override
    public ExportGroup getExportGroup() {
        return exportGroup;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setExportGroup(com.emc.storageos.db.client.model.ExportGroup)
     */
    @Override
    public void setExportGroup(ExportGroup exportGroup) {
        this.exportGroup = exportGroup;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getHost()
     */
    @Override
    public URI getHost() {
        return host;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setHost(java.net.URI)
     */
    @Override
    public void setHost(URI host) {
        this.host = host;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getCluster()
     */
    @Override
    public URI getCluster() {
        return cluster;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setCluster(java.net.URI)
     */
    @Override
    public void setCluster(URI cluster) {
        this.cluster = cluster;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getDeviceInitiators()
     */
    @Override
    public List<Initiator> getDeviceInitiators() {
        return deviceInitiators;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#setDeviceInitiators(java.util.List)
     */
    @Override
    public void setDeviceInitiators(List<Initiator>  deviceInitiators) {
        this.deviceInitiators = deviceInitiators;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#getProcessedBlockObject(java.lang.String)
     */
    @Override
    public BlockObject getProcessedBlockObject(String unmanagedVolumeGuid) {
        String objectGUID = unmanagedVolumeGuid.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
        return getCreatedObjectMap().get(objectGUID);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IIngestionRequestContext#rollbackAll()
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

}
