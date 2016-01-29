/**
 * 
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * 
 *
 */
public class BlockVplexCGIngestDecorator extends BlockCGIngestDecorator {
    private static final Logger logger = LoggerFactory.getLogger(BlockVplexCGIngestDecorator.class);

    @Override
    public void setNextDecorator(BlockCGIngestDecorator decorator) {
        this.nextCGIngestDecorator = decorator;
    }

    @Override
    public void decorateCG(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        if (null != associatedObjects && !associatedObjects.isEmpty()) {
            for (BlockObject blockObj : associatedObjects) {
                StringSetMap systemCGs = cg.getSystemConsistencyGroups();
                if (null != systemCGs && !systemCGs.isEmpty()) {
                    for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                        if (systemCGEntry.getKey().equalsIgnoreCase(blockObj.getStorageController().toString())) {
                            if (systemCGEntry.getValue().contains(blockObj.getReplicationGroupInstance())) {
                                logger.info(String.format("Found blockObject %s,%s system details in cg %s", blockObj.getNativeGuid(),
                                        blockObj.getReplicationGroupInstance(), cg.getLabel()));
                                continue;
                            } else {
                                logger.info(String.format("Adding blockObj %s/%s in CG %s", blockObj.getNativeGuid(),
                                        blockObj.getReplicationGroupInstance(), cg.getLabel()));
                                cg.addSystemConsistencyGroup(blockObj.getStorageController().toString(),
                                        blockObj.getReplicationGroupInstance());
                            }
                        } else {
                            logger.info(String.format("Block object %s system %s not found in CG %s. Hence adding.",
                                    blockObj.getNativeGuid(),
                                    blockObj.getStorageController(), cg.getLabel()));
                            cg.addSystemConsistencyGroup(blockObj.getStorageController().toString(), blockObj.getReplicationGroupInstance());
                        }
                    }
                }

            }
        }
    }

    @Override
    public void
            decorateCGBlockObjects(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
                    IngestionRequestContext requestContext)
                    throws Exception {
        if (null != associatedObjects && !associatedObjects.isEmpty()) {
            for (BlockObject blockObj : associatedObjects) {
                if (!NullColumnValueGetter.isNullURI(blockObj.getConsistencyGroup())) {
                    blockObj.setConsistencyGroup(cg.getId());
                }
            }
        }

    }

    @Override
    public List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, IngestionRequestContext requestContext) {
        return null;
    }

}
