/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
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
    private Map<String, BlockObject> _blockObjectsToBeCreatedMap;
    private Map<String, Set<DataObject>> _dataObjectsToBeUpdatedMap;
    private Map<String, Set<DataObject>> _dataObjectsToBeCreatedMap;
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

    // flags to help with final ingestion
    private boolean _managedPsetWasCreatedByAnotherContext = false;
    private boolean _managedBcgWasCreatedByAnotherContext = false;

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
            String volumeNativeGuid = getUnmanagedVolume().getNativeGuid().replace(
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
     * Returns the UnManagedProtectionSet for better or worse (possibly null).
     *
     * @return the UnManagedProtectionSet for this UnManagedVolume
     */
    public UnManagedProtectionSet getUnManagedProtectionSetLocal() {
        return _unManagedProtectionSet;
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
        UnManagedProtectionSet umpset = VolumeIngestionUtil.getUnManagedProtectionSetForUnManagedVolume(this, getUnmanagedVolume(),
                _dbClient);

        if (umpset != null) {
            _unManagedProtectionSet = umpset;
            return _unManagedProtectionSet;
        }

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
     * Sets whether or not the managed ProtectionSet was created by another context already.
     * 
     * @param managedPsetWasCreatedByAnotherContext true if the ProtectionSet was created by another context
     */
    public void setManagedPsetWasCreatedByAnotherContext(boolean managedPsetWasCreatedByAnotherContext) {
        this._managedPsetWasCreatedByAnotherContext = managedPsetWasCreatedByAnotherContext;
    }

    /**
     * Sets whether or not the managed BlockConsistencyGroup was created by another context already.
     * 
     * @param managedBcgWasCreatedByAnotherContext true if the BlockConsistencyGroup was created by another context
     */
    public void setManagedBcgWasCreatedByAnotherContext(boolean managedBcgWasCreatedByAnotherContext) {
        this._managedBcgWasCreatedByAnotherContext = managedBcgWasCreatedByAnotherContext;
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

        _logger.info("persisting RecoverPoint backend for volume " + getUnmanagedVolume().forDisplay());

        // commit the basic IngestionRequestContext collections
        for (BlockObject bo : getObjectsIngestedByExportProcessing()) {
            _logger.info("Creating BlockObject {} (hash {})", bo.forDisplay(), bo.hashCode());
            _dbClient.createObject(bo);
        }
        for (BlockObject bo : getBlockObjectsToBeCreatedMap().values()) {
            _logger.info("Creating BlockObject {} (hash {})", bo.forDisplay(), bo.hashCode());
            _dbClient.createObject(bo);
        }

        for (Set<DataObject> createdObjects : getDataObjectsToBeCreatedMap().values()) {
            if (createdObjects != null && !createdObjects.isEmpty()) {
                for (DataObject dob : createdObjects) {
                    _logger.info("Creating DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                    _dbClient.createObject(dob);
                }
            }
        }

        for (Set<DataObject> updatedObjects : getDataObjectsToBeUpdatedMap().values()) {
            if (updatedObjects != null && !updatedObjects.isEmpty()) {
                for (DataObject dob : updatedObjects) {
                    if (dob.getInactive()) {
                        _logger.info("Deleting DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                    } else {
                        _logger.info("Updating DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                    }
                    _dbClient.updateObject(dob);
                }
            }
        }

        for (UnManagedVolume umv : getUnManagedVolumesToBeDeleted()) {
            _logger.info("Deleting UnManagedVolume {} (hash {})", umv.forDisplay(), umv.hashCode());
            _dbClient.updateObject(umv);
        }

        // now commit the RecoverPoint specific data
        if (_managedSourceVolumesToUpdate != null) {
            _logger.info("Updating RP Source Volumes: " + _managedSourceVolumesToUpdate);
            _dbClient.updateObject(_managedSourceVolumesToUpdate);
        }
        if (_unmanagedSourceVolumesToUpdate != null) {
            _logger.info("Updating RP Source UnManagedVolumes: " + _unmanagedSourceVolumesToUpdate);
            _dbClient.updateObject(_unmanagedSourceVolumesToUpdate);
        }
        if (_unmanagedTargetVolumesToUpdate != null) {
            _logger.info("Updating RP Target UnManagedVolumes: " + _unmanagedTargetVolumesToUpdate);
            _dbClient.updateObject(_unmanagedTargetVolumesToUpdate);
        }

        // commit the ProtectionSet, if created, and remove the UnManagedProtectionSet
        ProtectionSet managedProtectionSet = getManagedProtectionSet();
        if (null != managedProtectionSet) {
            if (getManagedBlockObject() != null) {
                managedProtectionSet.getVolumes().add(_managedBlockObject.getId().toString());
            }

            if (!_managedPsetWasCreatedByAnotherContext) {
                _logger.info("Creating ProtectionSet {} (hash {})", 
                        managedProtectionSet.forDisplay(), managedProtectionSet.hashCode());
                _dbClient.createObject(managedProtectionSet);
            }

            // the protection set was created, so delete the unmanaged one
            _logger.info("Deleting UnManagedProtectionSet {} (hash {})", 
                    _unManagedProtectionSet.forDisplay(), _unManagedProtectionSet.hashCode());
            _dbClient.removeObject(_unManagedProtectionSet);
        }

        // commit the BlockConsistencyGroup, if created
        if (null != getManagedBlockConsistencyGroup()) {
            if (!_managedBcgWasCreatedByAnotherContext) {
                _logger.info("Creating BlockConsistencyGroup {} (hash {})" + 
                        _managedBlockConsistencyGroup.forDisplay(), _managedBlockConsistencyGroup.hashCode());
                _dbClient.createObject(_managedBlockConsistencyGroup);
            }
        }

        ExportGroup exportGroup = getExportGroup();
        if (isExportGroupCreated()) {
            _logger.info("Creating ExportGroup {} (hash {})", exportGroup.forDisplay(), exportGroup.hashCode());
            _dbClient.createObject(exportGroup);
        } else {
            _logger.info("Updating ExportGroup {} (hash {})", exportGroup.forDisplay(), exportGroup.hashCode());
            _dbClient.updateObject(exportGroup);
        }

        super.commit();
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

        if (getBlockObjectsToBeCreatedMap() != null) {
            getBlockObjectsToBeCreatedMap().clear();
        }

        if (getDataObjectsToBeCreatedMap() != null) {
            getDataObjectsToBeCreatedMap().clear();
        }

        if (getDataObjectsToBeUpdatedMap() != null) {
            getDataObjectsToBeUpdatedMap().clear();
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

        super.rollback();
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
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getVolumeContext(java.lang.
     * String
     * )
     */
    @Override
    public VolumeIngestionContext getVolumeContext(String unmanagedVolumeGuid) {
        if (getProcessedUnManagedVolumeMap().get(unmanagedVolumeGuid) != null) {
            return getProcessedUnManagedVolumeMap().get(unmanagedVolumeGuid);
        }
        return getVolumeContext();
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
        return getRootIngestionRequestContext().findCreatedBlockObject(objectGUID);
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
    public Map<String, BlockObject> getBlockObjectsToBeCreatedMap() {
        if (null == _blockObjectsToBeCreatedMap) {
            _blockObjectsToBeCreatedMap = new HashMap<String, BlockObject>();
        }

        return _blockObjectsToBeCreatedMap;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getDataObjectsToBeCreatedMap()
     */
    @Override
    public Map<String, Set<DataObject>> getDataObjectsToBeCreatedMap() {
        if (null == _dataObjectsToBeCreatedMap) {
            _dataObjectsToBeCreatedMap = new HashMap<String, Set<DataObject>>();
        }

        return _dataObjectsToBeCreatedMap;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getObjectsToBeUpdatedMap()
     */
    @Override
    public Map<String, Set<DataObject>> getDataObjectsToBeUpdatedMap() {
        if (null == _dataObjectsToBeUpdatedMap) {
            _dataObjectsToBeUpdatedMap = new HashMap<String, Set<DataObject>>();
        }

        return _dataObjectsToBeUpdatedMap;
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

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findCreatedBlockObject(java.
     * lang.
     * String)
     */
    @Override
    public BlockObject findCreatedBlockObject(String nativeGuid) {
        BlockObject blockObject = getBlockObjectsToBeCreatedMap().get(nativeGuid);
        return blockObject;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#
     * findAllUnManagedVolumesToBeDeleted()
     */
    @Override
    public List<UnManagedVolume> findAllUnManagedVolumesToBeDeleted() {
        return _parentRequestContext.findAllUnManagedVolumesToBeDeleted();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findInUpdatedObjects(java.net.
     * URI)
     */
    @Override
    public DataObject findInUpdatedObjects(URI uri) {
        return _parentRequestContext.findInUpdatedObjects(uri);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findCreatedBlockObject(java.net.
     * URI)
     */
    @Override
    public BlockObject findCreatedBlockObject(URI uri) {

        if (!URIUtil.isValid(uri)) {
            _logger.warn("URI ({}) for findCreatedBlockObject is null or invalid", uri);
            return null;
        }

        for (BlockObject bo : getBlockObjectsToBeCreatedMap().values()) {
            if (bo.getId() != null && uri.toString().equals(bo.getId().toString())) {
                _logger.info("\tfound block object in RP request context: " + bo.forDisplay());
                return bo;
            }
        }

        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findUnManagedConsistencyGroup(
     * com.emc.storageos.db.client.model.BlockConsistencyGroup)
     */
    @Override
    public UnManagedConsistencyGroup findUnManagedConsistencyGroup(String cgName) {
        return _parentRequestContext.findUnManagedConsistencyGroup(cgName);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#addObjectToCreate(com.emc.
     * storageos.db.client.model.BlockObject)
     */
    @Override
    public void addBlockObjectToCreate(BlockObject blockObject) {
        getBlockObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#addObjectToUpdate(com.emc.
     * storageos.db.client.model.DataObject)
     */
    @Override
    public void addDataObjectToUpdate(DataObject dataObject, UnManagedVolume unManagedVolume) {
        if (null == getDataObjectsToBeUpdatedMap().get(unManagedVolume.getNativeGuid())) {
            getDataObjectsToBeUpdatedMap().put(unManagedVolume.getNativeGuid(), new HashSet<DataObject>());
        }
        getDataObjectsToBeUpdatedMap().get(unManagedVolume.getNativeGuid()).add(dataObject);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#addDataObjectToCreate(com.emc.
     * storageos.db.client.model.DataObject)
     */
    @Override
    public void addDataObjectToCreate(DataObject dataObject, UnManagedVolume unManagedVolume) {
        if (null == getDataObjectsToBeCreatedMap().get(unManagedVolume.getNativeGuid())) {
            getDataObjectsToBeCreatedMap().put(unManagedVolume.getNativeGuid(), new HashSet<DataObject>());
        }
        getDataObjectsToBeCreatedMap().get(unManagedVolume.getNativeGuid()).add(dataObject);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findExportGroup(java.lang.
     * String)
     */
    @Override
    public ExportGroup findExportGroup(String exportGroupLabel, URI project, URI varray, URI computeResource, String resourceType) {
        if (exportGroupLabel != null) {

            ExportGroup localExportGroup = getExportGroup();
            if (null != localExportGroup && exportGroupLabel.equals(localExportGroup.getLabel())) {
                if (VolumeIngestionUtil.verifyExportGroupMatches(localExportGroup,
                        exportGroupLabel, project, varray, computeResource, resourceType)) {
                    _logger.info("Found existing local ExportGroup {} in RP ingestion request context",
                            localExportGroup.forDisplay());
                    return localExportGroup;
                }
            }
        }

        ExportGroup nestedExportGroup = null;
        for (VolumeIngestionContext volumeContext : getProcessedUnManagedVolumeMap().values()) {
            if (volumeContext instanceof IngestionRequestContext) {
                nestedExportGroup = ((IngestionRequestContext) volumeContext).findExportGroup(
                        exportGroupLabel, project, varray, computeResource, resourceType);
            }
            if (null != nestedExportGroup) {
                if (VolumeIngestionUtil.verifyExportGroupMatches(nestedExportGroup,
                        exportGroupLabel, project, varray, computeResource, resourceType)) {
                    _logger.info("Found existing nested ExportGroup {} in volume context {}",
                            nestedExportGroup.forDisplay(), volumeContext.getUnmanagedVolume().forDisplay());
                    return nestedExportGroup;
                }
            }
        }

        _logger.info("Could not find existing export group for label " + exportGroupLabel);
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findAllNewExportMasks()
     */
    @Override
    public List<ExportMask> findAllNewExportMasks() {
        List<ExportMask> newExportMasks = new ArrayList<ExportMask>();

        for (Set<DataObject> createdObjects : this.getDataObjectsToBeCreatedMap().values()) {
            for (DataObject createdObject : createdObjects) {
                if (createdObject instanceof ExportMask) {
                    newExportMasks.add((ExportMask) createdObject);
                }
            }
        }

        return newExportMasks;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getRootIngestionRequestContext()
     */
    @Override
    public IngestionRequestContext getRootIngestionRequestContext() {
        return _parentRequestContext;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findDataObjectByType(java.lang.Class, java.net.URI)
     */
    @Override
    public <T extends DataObject> T findDataObjectByType(Class<T> clazz, URI id, boolean fallbackToDatabase) {
        return getRootIngestionRequestContext().findDataObjectByType(clazz, id, fallbackToDatabase);
    }

    /**
     * Finds an existing ProtectionSet in any RecoverPoint volume ingestion context within the scope of this ingestion request.
     * 
     * @param psetLabel the label for the ProtectionSet
     * @param rpProtectionId the RecoverPoint protection set id
     * @param protectionSystemUri the RecoverPoint device URI
     * @param umpsetNativeGuid the nativeGuid for the discovered UnManagedProtectionSet
     * @return
     */
    public ProtectionSet findExistingProtectionSet(String psetLabel, String rpProtectionId, URI protectionSystemUri, String umpsetNativeGuid) {
        for (VolumeIngestionContext volumeContext : getRootIngestionRequestContext().getProcessedUnManagedVolumeMap().values()) {
            if (volumeContext instanceof RecoverPointVolumeIngestionContext) {
                RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext) volumeContext;
                ProtectionSet pset = rpContext.getManagedProtectionSet();
                if (pset != null) {
                    if ((pset.getLabel().equals(psetLabel)) 
                     && (pset.getProtectionId().equals(rpProtectionId))
                     && (pset.getProtectionSystem().equals(protectionSystemUri))
                     && (pset.getNativeGuid().equals(umpsetNativeGuid))) {
                        _logger.info("found already-instantiated ProtectionSet {} (hash {})", pset.getLabel(), pset.hashCode());
                        return pset;
                    }
                }
            }
        }

        _logger.info("did not find an already-instantiated ProtectionSet for ", psetLabel);
        return null;
    }

    public BlockConsistencyGroup findExistingBlockConsistencyGroup(String psetLabel, NamedURI projectNamedUri, NamedURI tenantOrg) {
        for (VolumeIngestionContext volumeContext : getRootIngestionRequestContext().getProcessedUnManagedVolumeMap().values()) {
            if (volumeContext instanceof RecoverPointVolumeIngestionContext) {
                RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext) volumeContext;
                BlockConsistencyGroup bcg = rpContext.getManagedBlockConsistencyGroup();
                if (bcg != null) {
                    if ((bcg.getLabel().equals(psetLabel)) 
                     && (bcg.getProject().equals(projectNamedUri))
                     && (bcg.getTenant().equals(tenantOrg))) {
                        _logger.info("found already-instantiated BlockConsistencyGroup {} (hash {})", bcg.getLabel(), bcg.hashCode());
                        return bcg;
                    }
                }
            }
        }

        _logger.info("did not find an already-instantiated BlockConsistencyGroup for ", psetLabel);
        return null;
    }

}
