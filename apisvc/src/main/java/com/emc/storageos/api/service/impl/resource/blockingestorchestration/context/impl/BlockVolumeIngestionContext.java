/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * A VolumeIngestionContext implementation for general Block Volume ingestion.
 */
public class BlockVolumeIngestionContext implements VolumeIngestionContext {

    protected DbClient _dbClient;

    private UnManagedVolume _unManagedVolume;
    private List<String> _errorMessages;

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
        // basic block volume ingestion doesn't need to commit anything
        // as all database saves are handled at the end of the ingestion process
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        // basic block volume ingestion doesn't need to roll back anything
        // as all database saves are handled at the end of the ingestion process
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
}
