package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.List;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class BlockVolumeCGIngestDecorator extends BlockCGIngestDecorator {

    @Override
    public void setNextDecorator(BlockCGIngestDecorator decorator) {
        this.nextCGIngestDecorator = decorator;
    }

    @Override
    public void decorateCG(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
    }

    @Override
    public void
            decorateCGBlockObjects(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
                    IngestionRequestContext requestContext)
                    throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, IngestionRequestContext requestContext) {
        return null;
    }
}
