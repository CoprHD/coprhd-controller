/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * A VolumeIngestionContext implementation for general Block Volume ingestion.
 */
public class BlockVolumeIngestionContext implements VolumeIngestionContext {

    private static final Logger _logger = LoggerFactory.getLogger(BlockVolumeIngestionContext.class);
    protected DbClient _dbClient;

    private UnManagedVolume _unManagedVolume;
    private List<String> _errorMessages;

    private Map<String, BlockConsistencyGroup> _cgsToCreateMap;
    private List<UnManagedConsistencyGroup> _umCGsToUpdate;

    /**
     * Constructor.
     * 
     * @param unManagedVolume the parent UnManagedVolume for this context
     * @param dbClient a reference to the database client
     */
    public BlockVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient) {
        _unManagedVolume = unManagedVolume;
        _dbClient = dbClient;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getUnmanagedVolume()
     */
    @Override
    public UnManagedVolume getUnmanagedVolume() {
        return _unManagedVolume;
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
        for (UnManagedConsistencyGroup umcg : getUmCGObjectsToUpdate()) {
            if (umcg.getInactive()) {
                _logger.info("Deleting UnManagedConsistencyGroup {} (hash {})", umcg.forDisplay(), umcg.hashCode());
            } else {
                _logger.info("Updating UnManagedConsistencyGroup {} (hash {})", umcg.forDisplay(), umcg.hashCode());
            }
            _dbClient.updateObject(umcg);
        }

        for (BlockConsistencyGroup bcg : getCGObjectsToCreateMap().values()) {
            _logger.info("Creating BlockConsistencyGroup {} (hash {})", bcg.forDisplay(), bcg.hashCode());
            _dbClient.createObject(bcg);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        // rollback the UnmanagedConsistencyGroups and CGs to create
        getUmCGObjectsToUpdate().clear();
        getCGObjectsToCreateMap().clear();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getErrorMessages()
     */
    @Override
    public List<String> getErrorMessages() {
        if (null == _errorMessages) {
            _errorMessages = new ArrayList<String>();
        }

        return _errorMessages;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#getCGObjectsToCreateMap()
     */
    @Override
    public Map<String, BlockConsistencyGroup> getCGObjectsToCreateMap() {
        if (null == _cgsToCreateMap) {
            _cgsToCreateMap = new HashMap<String, BlockConsistencyGroup>();
        }

        return _cgsToCreateMap;
    }

    @Override
    public List<UnManagedConsistencyGroup> getUmCGObjectsToUpdate() {
        if (null == _umCGsToUpdate) {
            _umCGsToUpdate = new ArrayList<UnManagedConsistencyGroup>();
        }

        return _umCGsToUpdate;
    }

}
