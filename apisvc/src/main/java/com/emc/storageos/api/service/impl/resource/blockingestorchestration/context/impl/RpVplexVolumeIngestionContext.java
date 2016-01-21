package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class RpVplexVolumeIngestionContext extends RecoverPointVolumeIngestionContext {

    private VplexVolumeIngestionContext _vplexVolumeIngestionContext;
    
    public RpVplexVolumeIngestionContext(UnManagedVolume unManagedVolume, 
            DbClient dbClient, IngestionRequestContext parentRequestContext) {
        super(unManagedVolume, dbClient, parentRequestContext);
        
        _vplexVolumeIngestionContext = new VplexVolumeIngestionContext(
                unManagedVolume, dbClient, parentRequestContext);
    }

    public VplexVolumeIngestionContext getVplexVolumeIngestionContext() {
        return _vplexVolumeIngestionContext;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#commit()
     */
    @Override
    public void commit() {
        _vplexVolumeIngestionContext.commit();
        super.commit();
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        _vplexVolumeIngestionContext.rollback();
        super.rollback();
    }
}
