package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
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

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#findCreatedBlockObject(java.lang.String)
     */
    @Override
    public BlockObject findCreatedBlockObject(String nativeGuid) {
        BlockObject blockObject = _vplexVolumeIngestionContext.findCreatedBlockObject(nativeGuid);
        if (blockObject == null) {
            blockObject = super.findCreatedBlockObject(nativeGuid);
        }
        return blockObject;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#findCreatedBlockObject(java.net.URI)
     */
    @Override
    public BlockObject findCreatedBlockObject(URI uri) {
        BlockObject blockObject = _vplexVolumeIngestionContext.findCreatedBlockObject(uri);
        if (blockObject == null) {
            blockObject = super.findCreatedBlockObject(uri);
        }
        return blockObject;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#findInUpdatedObjects(java.net.URI)
     */
    @Override
    public DataObject findInUpdatedObjects(URI uri) {
        DataObject dataObject = _vplexVolumeIngestionContext.findInUpdatedObjects(uri);
        if (dataObject == null) {
            dataObject = super.findInUpdatedObjects(uri);
        }
        return dataObject;
    }
}
