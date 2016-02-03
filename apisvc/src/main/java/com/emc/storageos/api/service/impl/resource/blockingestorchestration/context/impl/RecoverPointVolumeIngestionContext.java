/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * A combined implementation of VolumeIngestionContext (by extending BlockVolumeIngestionContext)
 * and IngestionRequestContext for RecoverPoint volumes.
 * 
 * The VolumeIngestionContext implementation serves as context for ingestion of the
 * parent RecoverPort volume. The IngestionRequestContext implementation serves
 * as context for processing the RecoverPoint volume's backend structure.
 */
public class RecoverPointVolumeIngestionContext extends BlockVolumeIngestionContext implements IngestionRequestContext {

    private static final Logger _logger = LoggerFactory.getLogger(RecoverPointVolumeIngestionContext.class);

    // these members are part of the IngestionRequestContext
    // for the RecoverPoint volume's backend ingestion processing
    private Map<String, VolumeIngestionContext> _processedUnManagedVolumeMap;
    private final IngestionRequestContext _parentRequestContext;
    private Map<String, BlockObject> _objectsToBeCreatedMap;
    private Map<String, List<DataObject>> _objectsToBeUpdatedMap;
    private List<UnManagedVolume> _unManagedVolumesToBeDeleted;

    // members related to Recover finalbackend export mask ingestion
    private boolean _exportGroupCreated = false;
    private ExportGroup _exportGroup;
    private List<Initiator> _deviceInitiators;
    private List<BlockObject> _objectsIngestedByExportProcessing;

    // other RecoverPoint backend tracking members, used for commit and rollback
    private Volume _managedBlockObject;
    private ProtectionSet _managedProtectionSet;
    private BlockConsistencyGroup _managedBlockConsistencyGroup;
    private List<Volume> _managedSourceVolumesToUpdate;
    private List<UnManagedVolume> _unmanagedSourceVolumesToUpdate;
    private List<UnManagedVolume> _unmanagedTargetVolumesToUpdate;
    private UnManagedProtectionSet _unManagedProtectionSet;

    /**
     * Constructor.
     * 
     * @param unManagedVolume the parent UnManagedVolume for this context
     * @param dbClient a reference to the database client
     * @param parentRequestContext the parent IngestionRequestContext
     */
    public RecoverPointVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient,
            IngestionRequestContext parentRequestContext) {
        super(unManagedVolume, dbClient);
        _parentRequestContext = parentRequestContext;
    }

    /**
     * Returns the managed BlockObject for this RecoverPoint ingestion
     * context, or null if it doesn't exist yet in the database and
     * hasn't yet been created by this ingestion process.
     * 
     * @return the managed BlockObject object for this RecoverPoint ingestion context
     */
    public BlockObject getManagedBlockObject() {

        if (null == _managedBlockObject) {
            String volumeNativeGuid =
                    getUnmanagedVolume().getNativeGuid().replace(
                            VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
            _managedBlockObject = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
        }

        return _managedBlockObject;
    }

    /**
     * Sets the managed BlockObject for this RecoverPoint ingestion context.
     * 
     * @param managedBlockObject the managed BlockObject for this RecoverPoint ingestion context
     */
    public void setManagedBlockObject(Volume managedBlockObject) {
        this._managedBlockObject = managedBlockObject;
    }

    /**
     * Returns the UnManagedProtectionSet for this UnManagedVolume, or null
     * if none could be found.
     * 
     * @return the UnManagedProtectionSet for this UnManagedVolume
     */
    public UnManagedProtectionSet getUnManagedProtectionSet() {

        if (_unManagedProtectionSet != null) {
            return _unManagedProtectionSet;
        }

        // Find the UnManagedProtectionSet associated with this unmanaged volume
        UnManagedProtectionSet umpset = VolumeIngestionUtil.getUnManagedProtectionSetForUnManagedVolume(this, getUnmanagedVolume(), _dbClient);

        // It is possible that the unmanaged volume was already ingested in which case the ingested block object will be part of the
        // unmanaged protection set's managed volumes list.
        String managedVolumeNativeGUID = getUnmanagedVolume().getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        BlockObject managedVolume = VolumeIngestionUtil.getBlockObject(managedVolumeNativeGUID, _dbClient);
        if (managedVolume != null) {
            umpset = VolumeIngestionUtil.getUnManagedProtectionSetForManagedVolume(this, managedVolume, _dbClient);
        }

        if (umpset == null) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + getUnmanagedVolume().getId());
            // caller will throw exception
            return null;
        }

        _unManagedProtectionSet = umpset;

        return _unManagedProtectionSet;
    }

    /**
     * Returns the managed ProtectionSet for this context, or null
     * if it hasn't been set yet by ingestion.
     * 
     * @return the managed ProtectionSet for this context
     */
    public ProtectionSet getManagedProtectionSet() {
        return _managedProtectionSet;
    }

    /**
     * Sets the managed ProtectionSet for this context once it has been created.
     * 
     * @param ingestedProtectionSet the managed ProtectionSet to set
     */
    public void setManagedProtectionSet(ProtectionSet ingestedProtectionSet) {
        this._managedProtectionSet = ingestedProtectionSet;
    }

    /**
     * Returns the managed BlockConsistencyGroup for this context, or null
     * if it hasn't been set yet by ingestion.
     * 
     * @return the managed BlockConsistencyGroup for this context
     */
    public BlockConsistencyGroup getManagedBlockConsistencyGroup() {
        return _managedBlockConsistencyGroup;
    }

    /**
     * Sets the managed BlockConsistencyGroup for this context once it has been created.
     * 
     * @param ingestedBlockConsistencyGroup the managed ProtectionSet to set
     */
    public void setManagedBlockConsistencyGroup(BlockConsistencyGroup ingestedBlockConsistencyGroup) {
        this._managedBlockConsistencyGroup = ingestedBlockConsistencyGroup;
    }

    /**
     * Adds a source Volume to the list of Volumes that should be updated
     * when this RecoverPoint ingestion process is committed.
     * 
     * @param volume the Volume to update
     */
    public void addManagedSourceVolumeToUpdate(Volume volume) {
        if (null == _managedSourceVolumesToUpdate) {
            _managedSourceVolumesToUpdate = new ArrayList<Volume>();
        }
        _managedSourceVolumesToUpdate.add(volume);
    }

    /**
     * Adds a target Volume to the list of Volumes that should be updated
     * when this RecoverPoint ingestion process is committed.
     * 
     * @param volume the Volume to update
     */
    public void addUnmanagedTargetVolumeToUpdate(UnManagedVolume volume) {
        if (null == _unmanagedTargetVolumesToUpdate) {
            _unmanagedTargetVolumesToUpdate = new ArrayList<UnManagedVolume>();
        }
        _unmanagedTargetVolumesToUpdate.add(volume);
    }

    /**
     * Adds a source UnManagedVolume to the list of UnManagedVolumes that should be updated
     * when this RecoverPoint ingestion process is committed.
     * 
     * @param volume the UnManagedVolume to update
     */
    public void addUnmanagedSourceVolumeToUpdate(UnManagedVolume volume) {
        if (null == _unmanagedSourceVolumesToUpdate) {
            _unmanagedSourceVolumesToUpdate = new ArrayList<UnManagedVolume>();
        }
        _unmanagedSourceVolumesToUpdate.add(volume);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#commit()
     */
    @Override
    public void commit() {

        // commit the basic IngestionRequestContext collections
        _dbClient.createObject(getObjectsIngestedByExportProcessing());
        _dbClient.createObject(getObjectsToBeCreatedMap().values());

        for (List<DataObject> dos : getObjectsToBeUpdatedMap().values()) {
            _dbClient.updateObject(dos);
        }
        _dbClient.updateObject(getUnManagedVolumesToBeDeleted());

        // now commit the RecoverPoint specific data
        _dbClient.updateObject(_managedSourceVolumesToUpdate);
        _dbClient.updateObject(_unmanagedSourceVolumesToUpdate);
        _dbClient.updateObject(_unmanagedTargetVolumesToUpdate);

        // commit the ProtectionSet, if created, and remove the UnManagedProtectionSet
        if (null != _managedProtectionSet) {
            _managedProtectionSet.getVolumes().add(_managedBlockObject.getId().toString());
            _dbClient.createObject(_managedProtectionSet);

            // the protection set was created, so delete the unmanaged one
            _dbClient.removeObject(_unManagedProtectionSet);
        }

        // commit the BlockConsistencyGroup, if created
        if (null != _managedBlockConsistencyGroup) {
            _dbClient.createObject(_managedBlockConsistencyGroup);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        // basically dereferenced everything so that it won't be saved
        if (getObjectsIngestedByExportProcessing() != null) {
            getObjectsIngestedByExportProcessing().clear();
        }

        if (getObjectsToBeCreatedMap() != null) {
            getObjectsToBeCreatedMap().clear();
        }

        if (getObjectsToBeUpdatedMap() != null) {
            getObjectsToBeUpdatedMap().clear();
        }

        if (getUnManagedVolumesToBeDeleted() != null) {
            getUnManagedVolumesToBeDeleted().clear();
        }

        if (_managedSourceVolumesToUpdate != null) {
            _managedSourceVolumesToUpdate.clear();
        }

        if (_unmanagedSourceVolumesToUpdate != null) {
            _unmanagedSourceVolumesToUpdate.clear();
        }

        if (_unmanagedTargetVolumesToUpdate != null) {
            _unmanagedTargetVolumesToUpdate.clear();
        }

        _managedProtectionSet = null;
        _managedBlockConsistencyGroup = null;
        _managedBlockObject = null;

        // the ExportGroup was created by this ingestion
        // process, make sure it gets cleaned up
        if (_exportGroupCreated) {
            _dbClient.markForDeletion(_exportGroup);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        // no-op, a RecoverPoint volume will not have any child volumes to iterate
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#next()
     */
    @Override
    public UnManagedVolume next() {
        // no-op, a RecoverPoint volume will not have any child volumes to iterate
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        // no-op, a RecoverPoint volume will not have any child volumes to iterate
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
    public VirtualPool getVpool(UnManagedVolume unmanagedVolume) {
        return _parentRequestContext.getVpool(unmanagedVolume);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVarray()
     */
    @Override
    public VirtualArray getVarray(UnManagedVolume unmanagedVolume) {
        return _parentRequestContext.getVarray(unmanagedVolume);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getProject()
     */
    @Override
    public Project getProject() {
        return _parentRequestContext.getProject();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getTenant()
     */
    @Override
    public TenantOrg getTenant() {
        return _parentRequestContext.getTenant();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVplexIngestionMethod()
     */
    @Override
    public String getVplexIngestionMethod() {
        return _parentRequestContext.getVplexIngestionMethod();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getStorageSystemCache()
     */
    @Override
    public Map<String, StorageSystem> getStorageSystemCache() {
        return _parentRequestContext.getStorageSystemCache();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExhaustedStorageSystems()
     */
    @Override
    public List<URI> getExhaustedStorageSystems() {
        return _parentRequestContext.getExhaustedStorageSystems();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExhaustedPools()
     */
    @Override
    public List<URI> getExhaustedPools() {
        return _parentRequestContext.getExhaustedPools();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getUnManagedVolumesToBeDeleted()
     */
    @Override
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted() {
        if (null == _unManagedVolumesToBeDeleted) {
            _unManagedVolumesToBeDeleted = new ArrayList<UnManagedVolume>();
        }

        return _unManagedVolumesToBeDeleted;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getTaskStatusMap()
     */
    @Override
    public Map<String, StringBuffer> getTaskStatusMap() {
        return _parentRequestContext.getTaskStatusMap();
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
        if (null == _processedUnManagedVolumeMap) {
            _processedUnManagedVolumeMap = new HashMap<String, VolumeIngestionContext>();
        }

        return _processedUnManagedVolumeMap;
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
        return findCreatedBlockObject(objectGUID);
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
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getErrorMessagesForVolume(java
     * .lang.String)
     */
    @Override
    public List<String> getErrorMessagesForVolume(String nativeGuid) {
        // for RP, we want to return the error messages List for the
        // main UnManagedVolume, whose status would be returned to the user
        return getErrorMessages();
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
        if (null == _objectsIngestedByExportProcessing) {
            _objectsIngestedByExportProcessing = new ArrayList<BlockObject>();
        }

        return _objectsIngestedByExportProcessing;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#isExportGroupCreated()
     */
    @Override
    public boolean isExportGroupCreated() {
        return _exportGroupCreated;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setExportGroupCreated(boolean)
     */
    @Override
    public void setExportGroupCreated(boolean exportGroupCreated) {
        this._exportGroupCreated = exportGroupCreated;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getExportGroup()
     */
    @Override
    public ExportGroup getExportGroup() {
        return _exportGroup;
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
        this._exportGroup = exportGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getHost()
     */
    @Override
    public URI getHost() {
        // the backend ingestion request context for RecoverPoint only consists of device initiator (i.e., the RP device)
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setHost(java.net.URI)
     */
    @Override
    public void setHost(URI host) {
        // no-op; RecoverPoint backend ingestion request context only uses device initiators (i.e., the RP device) for export
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCluster()
     */
    @Override
    public URI getCluster() {
        // the backend ingestion request context for RecoverPoint only consists of device initiator (i.e., the RP device)
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#setCluster(java.net.URI)
     */
    @Override
    public void setCluster(URI cluster) {
        // no-op; RecoverPoint backend ingestion request context only uses device initiators (i.e., the RP device) for export
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getDeviceInitiators()
     */
    @Override
    public List<Initiator> getDeviceInitiators() {
        return _deviceInitiators;
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
        this._deviceInitiators = deviceInitiators;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getObjectsToBeCreatedMap()
     */
    @Override
    public Map<String, BlockObject> getObjectsToBeCreatedMap() {
        if (null == _objectsToBeCreatedMap) {
            _objectsToBeCreatedMap = new HashMap<String, BlockObject>();
        }

        return _objectsToBeCreatedMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getObjectsToBeUpdatedMap()
     */
    @Override
    public Map<String, List<DataObject>> getObjectsToBeUpdatedMap() {
        if (null == _objectsToBeUpdatedMap) {
            _objectsToBeUpdatedMap = new HashMap<String, List<DataObject>>();
        }

        return _objectsToBeUpdatedMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#isVolumeExported()
     */
    @Override
    public boolean isVolumeExported() {
        return VolumeIngestionUtil.checkUnManagedResourceIsNonRPExported(getUnmanagedVolume());
    }

    /**
     * Adds a BlockObject to the objectsToBeCreated Map by its native GUID.
     * 
     * @param blockObject the BlockObject to add for creation in the database
     */
    public void addObjectToCreate(BlockObject blockObject) {
        getObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
    }

    /**
     * Adds a DataObjects to the objectsToBeUpdated Map for the current UnManagedVolume.
     * 
     * @param dataObject the DataObject that needs to be updated in the database
     */
    public void addObjectToUpdate(DataObject dataObject) {
        if (null == getObjectsToBeUpdatedMap().get(getCurrentUnmanagedVolume().getNativeGuid())) {
            getObjectsToBeUpdatedMap().put(getCurrentUnmanagedVolume().getNativeGuid(), new ArrayList<DataObject>());
        }
        getObjectsToBeUpdatedMap().get(getCurrentUnmanagedVolume().getNativeGuid()).add(dataObject);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findCreatedBlockObject(java.lang.
     * String)
     */
    @Override
    public BlockObject findCreatedBlockObject(String nativeGuid) {

        BlockObject blockObject = getObjectsToBeCreatedMap().get(nativeGuid);
        if (blockObject == null) {
            blockObject = _parentRequestContext.getObjectsToBeCreatedMap().get(nativeGuid);
        }

        return blockObject;
    }

    @Override
    public Map<String, BlockConsistencyGroup> getCGObjectsToCreateMap() {
        return _parentRequestContext.getCGObjectsToCreateMap();
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findAllProcessedUnManagedVolumes()
     */
    @Override
    public List<UnManagedVolume> findAllProcessedUnManagedVolumes() {
        return _parentRequestContext.findAllProcessedUnManagedVolumes();
}

    @Override
    public List<UnManagedConsistencyGroup> getUmCGObjectsToUpdate() {
        return _parentRequestContext.getUmCGObjectsToUpdate();
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findInUpdatedObjects(java.net.URI)
     */
    @Override
    public DataObject findInUpdatedObjects(URI uri) {
        return _parentRequestContext.findInUpdatedObjects(uri);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findCreatedBlockObject(java.net.URI)
     */
    @Override
    public BlockObject findCreatedBlockObject(URI uri) {
        return _parentRequestContext.findCreatedBlockObject(uri);
    }
}
