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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class BlockVolumeIngestionContext implements VolumeIngestionContext {

    protected DbClient _dbClient;

    private UnManagedVolume unManagedVolume;
    private List<String> errorMessages;

    public BlockVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient) {
        this.unManagedVolume = unManagedVolume;
        this._dbClient = dbClient;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getUnmanagedVolume()
     */
    @Override
    public UnManagedVolume getUnmanagedVolume() {
        return unManagedVolume;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#isVolumeExported()
     */
    @Override
    public boolean isVolumeExported() {
        return VolumeIngestionUtil.checkUnManagedResourceAlreadyExported(getUnmanagedVolume());
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#commit()
     */
    @Override
    public void commit() {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getErrorMessages()
     */
    @Override
    public List<String> getErrorMessages() {
        if (null == errorMessages) {
            errorMessages = new ArrayList<String>();
        }
        
        return errorMessages;
    }
}
