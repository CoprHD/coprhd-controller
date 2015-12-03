package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;

public class VplexVolumeIngestionContext extends VplexBackendIngestionContext implements VolumeIngestionContext {

    private List<String> errorMessages;

    public VplexVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient) {
        super(unManagedVolume, dbClient);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext#getUnManagedVolume()
     */
    @Override
    public UnManagedVolume getUnmanagedVolume() {
        return super.getUnmanagedVirtualVolume();
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
    public List<String> getErrorMessages() {
        if (null == errorMessages) {
            errorMessages = new ArrayList<String>();
        }
        
        return errorMessages;
    }

}
