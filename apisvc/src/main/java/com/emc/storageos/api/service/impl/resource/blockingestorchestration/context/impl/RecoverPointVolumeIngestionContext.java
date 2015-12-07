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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockIngestOrchestrator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BasicIngestionRequestContext.VolumeIngestionContextFactory;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;

public class RecoverPointVolumeIngestionContext extends BlockVolumeIngestionContext implements IngestionRequestContext {

    private static final Logger _logger = LoggerFactory.getLogger(RecoverPointVolumeIngestionContext.class);

    
    
    
    private Map<String, VolumeIngestionContext> processedUnManagedVolumeMap;
    private Map<String, BlockObject> createdObjectMap;
    private Map<String, List<DataObject>> updatedObjectMap;
    private List<UnManagedVolume> unManagedVolumesToBeDeleted;

    private List<Volume> managedSourceVolumesToUpdate;
    private List<UnManagedVolume> unmanagedSourceVolumesToUpdate;
    private List<UnManagedVolume> unmanagedTargetVolumesToUpdate;

    private List<String> errorMessages;
    
    private IngestionRequestContext parentRequestContext;

    // export ingestion related items
    private boolean exportGroupCreated = false;
    private ExportGroup exportGroup;
    private List<Initiator> deviceInitiators;
    private List<BlockObject> ingestedObjects;

    
    
    private UnManagedProtectionSet unManagedProtectionSet;
    
    
    
    private Volume managedBlockObject;
    
    private ProtectionSet managedProtectionSet;
    private BlockConsistencyGroup managedBlockConsistencyGroup;

    public RecoverPointVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient,
            IngestionRequestContext parentRequestContext) {
        super(unManagedVolume, dbClient);
        this.parentRequestContext = parentRequestContext;
    }
    
    /**
     * @return the ingestedVolume
     */
    public BlockObject getManagedBlockObject() {

        if (null == managedBlockObject) {
            String volumeNativeGuid = 
                    getUnmanagedVolume().getNativeGuid().replace(
                            VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
            managedBlockObject = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
        }
        
        return managedBlockObject;
    }

    /**
     * @param managedBlockObject the ingestedVolume to set
     */
    public void setManagedBlockObject(Volume managedBlockObject) {
        this.managedBlockObject = managedBlockObject;
    }

    /**
     * @return the unManagedProtectionSet
     */
    public UnManagedProtectionSet getUnManagedProtectionSet() {
        
        // Find the UnManagedProtectionSet associated with this unmanaged volume
        List<UnManagedProtectionSet> umpsets = 
                CustomQueryUtility.getUnManagedProtectionSetByUnManagedVolumeId(_dbClient, getUnmanagedVolume().getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (!umpsetsItr.hasNext()) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + getUnmanagedVolume().getId());
            // caller will throw exception
            return null;
        }
        
        unManagedProtectionSet = umpsetsItr.next();

        return unManagedProtectionSet;
    }

    /**
     * @return the managedProtectionSet
     */
    public ProtectionSet getManagedProtectionSet() {
        return managedProtectionSet;
    }
    
    /**
     * @param ingestedProtectionSet the ingestedProtectionSet to set
     */
    public void setManagedProtectionSet(ProtectionSet ingestedProtectionSet) {
        this.managedProtectionSet = ingestedProtectionSet;
    }

    /**
     * @return the managedBlockConsistencyGroup
     */
    public BlockConsistencyGroup getManagedBlockConsistencyGroup() {
        return managedBlockConsistencyGroup;
    }

    /**
     * @param ingestedBlockConsistencyGroup the ingestedBlockConsistencyGroup to set
     */
    public void setManagedBlockConsistencyGroup(BlockConsistencyGroup ingestedBlockConsistencyGroup) {
        this.managedBlockConsistencyGroup = ingestedBlockConsistencyGroup;
    }

    @Override
    public void commit() {

        _dbClient.createObject(getObjectsIngestedByExportProcessing());
        _dbClient.createObject(getObjectsToBeCreatedMap().values());

        for (List<DataObject> dos : getObjectsToBeUpdatedMap().values()) {
            _dbClient.updateObject(dos);
        }
        _dbClient.updateObject(getUnManagedVolumesToBeDeleted());

        _dbClient.updateObject(managedSourceVolumesToUpdate);
        _dbClient.updateObject(unmanagedSourceVolumesToUpdate);
        _dbClient.updateObject(unmanagedTargetVolumesToUpdate);
        
        
        if (null != managedProtectionSet) {
            
            managedProtectionSet.getVolumes().add(managedBlockObject.getId().toString());
            _dbClient.createObject(managedProtectionSet);
            
            // the protection set was created, so deleted the unmanaged one
            _dbClient.removeObject(unManagedProtectionSet);
        }
        
        if (null != managedBlockConsistencyGroup) {
            _dbClient.createObject(managedBlockConsistencyGroup);
        }
    }

    @Override
    public void rollback() {
        getObjectsIngestedByExportProcessing().clear();
        getObjectsToBeCreatedMap().clear();
        getObjectsToBeUpdatedMap().clear();
        getUnManagedVolumesToBeDeleted().clear();
        managedSourceVolumesToUpdate.clear();
        unmanagedSourceVolumesToUpdate.clear();
        unmanagedTargetVolumesToUpdate.clear();
        managedProtectionSet = null;
        managedBlockConsistencyGroup = null;
        managedBlockObject = null;
        if (exportGroupCreated) {
            _dbClient.removeObject(exportGroup);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#next()
     */
    @Override
    public UnManagedVolume next() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        // nothin'
    }

    protected void setCurrentUnmanagedVolume(UnManagedVolume unManagedVolume) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCurrentUnmanagedVolume()
     */
    @Override
    public UnManagedVolume getCurrentUnmanagedVolume() {
        return getUnmanagedVolume();
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
        return this;
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

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVpool()
     */
    @Override
    public VirtualPool getVpool() {
        return parentRequestContext.getVpool();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVarray()
     */
    @Override
    public VirtualArray getVarray() {
        return parentRequestContext.getVarray();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProject()
     */
    @Override
    public Project getProject() {
        return parentRequestContext.getProject();
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

    public void addObjectToCreate(BlockObject blockObject) {
        getObjectsToBeCreatedMap().put(getUnmanagedVolume().getNativeGuid(), blockObject);
    }

    public void addObjectToUpdate(DataObject dataObject) {
        List<DataObject> objectsToUpdate = getObjectsToBeUpdatedMap().get(getUnmanagedVolume().getNativeGuid());
        if (null == objectsToUpdate) {
            objectsToUpdate = new ArrayList<DataObject>();
        }
        objectsToUpdate.add(dataObject);
    }

    
    

    
    
    public void addManagedSourceVolumeToUpdate(Volume volume) {
        if (null == managedSourceVolumesToUpdate) {
            managedSourceVolumesToUpdate = new ArrayList<Volume>();
        }
        managedSourceVolumesToUpdate.add(volume);
    }
    
    public void addUnmanagedSourceVolumeToUpdate(UnManagedVolume volume) {
        if (null == unmanagedSourceVolumesToUpdate) {
            unmanagedSourceVolumesToUpdate = new ArrayList<UnManagedVolume>();
        }
        unmanagedSourceVolumesToUpdate.add(volume);
    }
    
    public void addUnmanagedTargetVolumeToUpdate(UnManagedVolume volume) {
        if (null == unmanagedTargetVolumesToUpdate) {
            unmanagedTargetVolumesToUpdate = new ArrayList<UnManagedVolume>();
        }
        unmanagedTargetVolumesToUpdate.add(volume);
    }
    
    
}
