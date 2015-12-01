package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BlockVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RPVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.VplexVolumeIngestionContext;
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
public class IngestionRequestContext implements Iterator<UnManagedVolume> {

    private static Logger _logger = LoggerFactory.getLogger(IngestionRequestContext.class);
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

    private List<UnManagedVolume> unManagedVolumesSuccessfullyProcessed;
    private List<UnManagedVolume> unManagedVolumesToBeDeleted;
    private Map<String, BlockObject> createdObjectMap; 
    private Map<String, List<DataObject>> updatedObjectMap; 

    private VolumeIngestionContext currentVolumeIngestionContext;
    private URI currentUnManagedVolumeUri;

    // export ingestion related items
    private boolean exportGroupCreated = false;
    private ExportGroup exportGroup;
    private Host host;
    private Cluster cluster;
    private List<Initiator> deviceInitiators;
    List<BlockObject> ingestedObjects;

    public IngestionRequestContext(DbClient dbClient, List<URI> unManagedVolumeUrisToProcess, VirtualPool vpool, 
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
        // TODO: no-op maybe?
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
    
    /**
     * Private setter of the current UnManagedVolume, used by the UnManagedVolume
     * iterator.
     * 
     * @param unManagedVolume
     */
    private void setCurrentUnmanagedVolume(UnManagedVolume unManagedVolume) {
        currentVolumeIngestionContext = 
                VolumeIngestionContextFactory.getVolumeIngestionContext(unManagedVolume, _dbClient);
    }
    
    /**
     * @return the current UnManagedVolume via the current VolumeIngestionContext
     */
    public UnManagedVolume getCurrentUnmanagedVolume() {
        if (currentVolumeIngestionContext == null) {
            return null;
        }
        
        return currentVolumeIngestionContext.getUnmanagedVolume();
    }

    /**
     * @return the currentUnManagedVolumeUri
     */
    public URI getCurrentUnManagedVolumeUri() {
        return currentUnManagedVolumeUri;
    }

    /**
     * @return the current VolumeIngestionContext
     */
    public VolumeIngestionContext getVolumeContext() {
        return currentVolumeIngestionContext;
    }

    /**
     * @return the storageSystem
     */
    public StorageSystem getStorageSystem() {

        URI storageSystemUri = getCurrentUnmanagedVolume().getStorageSystemUri();
        StorageSystem storageSystem = getSystemMap().get(storageSystemUri.toString());
        if (null == storageSystem) {
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
            getSystemMap().put(storageSystemUri.toString(), storageSystem);
        }

        return storageSystem;
    }

    /**
     * @return the vpool
     */
    public VirtualPool getVpool() {
        return vpool;
    }

    /**
     * @return the virtualArray
     */
    public VirtualArray getVirtualArray() {
        return virtualArray;
    }

    /**
     * @return the project
     */
    public Project getProject() {
        return project;
    }

    /**
     * @return the tenant
     */
    public TenantOrg getTenant() {
        return tenant;
    }

    /**
     * @return the vplexIngestionMethod
     */
    public String getVplexIngestionMethod() {
        return vplexIngestionMethod;
    }

    /**
     * @return the systemMap
     */
    public Map<String, StorageSystem> getSystemMap() {
        if (null == systemMap) {
            systemMap = new HashMap<String, StorageSystem>();
        }
        
        return systemMap;
    }

    /**
     * @return the systemCache
     */
    public List<URI> getSystemCache() {
        if (null == systemCache) {
            systemCache = new ArrayList<URI>();
        }
        
        return systemCache;
    }

    /**
     * @return the poolCache
     */
    public List<URI> getPoolCache() {
        if (null == poolCache) {
            poolCache = new ArrayList<URI>();
        }
        
        return poolCache;
    }

    /**
     * @return the unManagedVolumesSuccessfullyProcessed
     */
    public List<UnManagedVolume> getUnManagedVolumesSuccessfullyProcessed() {
        if (null == unManagedVolumesSuccessfullyProcessed) {
            unManagedVolumesSuccessfullyProcessed = new ArrayList<UnManagedVolume>();
        }
        
        return unManagedVolumesSuccessfullyProcessed;
    }

    /**
     * @return the unManagedVolumesToBeDeleted
     */
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted() {
        if (null == unManagedVolumesToBeDeleted) {
            unManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        }

        return unManagedVolumesToBeDeleted;
    }

    /**
     * @return the createdObjectMap
     */
    public Map<String, BlockObject> getCreatedObjectMap() {
        if (null == createdObjectMap) {
            createdObjectMap = new HashMap<String, BlockObject>();
        }
        
        return createdObjectMap;
    }

    /**
     * @return the updatedObjectMap
     */
    public Map<String, List<DataObject>> getUpdatedObjectMap() {
        if (null == updatedObjectMap) {
            updatedObjectMap = new HashMap<String, List<DataObject>>();
        }
        
        return updatedObjectMap;
    }

    /**
     * @return the taskStatusMap
     */
    public Map<String, StringBuffer> getTaskStatusMap() {
        if (null == taskStatusMap) {
            taskStatusMap = new HashMap<String, StringBuffer>();
        }
        
        return taskStatusMap;
    }

    /**
     * @return the processedUnManagedVolumeMap
     */
    public Map<String, VolumeIngestionContext> getProcessedUnManagedVolumeMap() {
        if (null == processedUnManagedVolumeMap) {
            processedUnManagedVolumeMap = new HashMap<String, VolumeIngestionContext>();
        }
        
        return processedUnManagedVolumeMap;
    }

    /**
     * Returns the UnManagedVolume that has been processed for the given nativeGuid,
     * or null if none was found.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return an UnManagedVolume
     */
    public UnManagedVolume getProcessedUnManagedVolume(String nativeGuid) {
        VolumeIngestionContext volumeContext = getProcessedUnManagedVolumeMap().get(nativeGuid);
        if (null != volumeContext) {
            return volumeContext.getUnmanagedVolume();
        }
        
        return null;
    }

    /**
     * Returns the VolumeIngestionContext for the given nativeGuid,
     * or null if none was found in the processed UnManagedVolume Map.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return a VolumeIngestionContext
     */
    public VolumeIngestionContext getProcessedVolumeContext(String nativeGuid) {
        return getProcessedUnManagedVolumeMap().get(nativeGuid);
    }

    /**
     * @return the ingestedObjects
     */
    public List<BlockObject> getIngestedObjects() {
        if (null == ingestedObjects) {
            ingestedObjects = new ArrayList<BlockObject>();
        }
        
        return ingestedObjects;
    }

    /**
     * @return the exportGroupCreated
     */
    public boolean isExportGroupCreated() {
        return exportGroupCreated;
    }

    /**
     * @param exportGroupCreated the exportGroupCreated to set
     */
    public void setExportGroupCreated(boolean exportGroupCreated) {
        this.exportGroupCreated = exportGroupCreated;
    }

    /**
     * @return the exportGroup
     */
    public ExportGroup getExportGroup() {
        return exportGroup;
    }

    /**
     * @param exportGroup the exportGroup to set
     */
    public void setExportGroup(ExportGroup exportGroup) {
        this.exportGroup = exportGroup;
    }

    /**
     * @return the host
     */
    public Host getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(Host host) {
        this.host = host;
    }

    /**
     * @return the cluster
     */
    public Cluster getCluster() {
        return cluster;
    }

    /**
     * @param cluster the cluster to set
     */
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * @return the deviceInitiators
     */
    public List<Initiator> getDeviceInitiators() {
        return deviceInitiators;
    }

    /**
     * @param deviceInitiators the deviceInitiators to set
     */
    public void setDeviceInitiators(List<Initiator> deviceInitiators) {
        this.deviceInitiators = deviceInitiators;
    }

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
