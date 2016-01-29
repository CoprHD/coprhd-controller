package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public abstract class BlockCGIngestDecorator {

    BlockCGIngestDecorator nextCGIngestDecorator = null;

    DbClient dbClient = null;

    /**
     * Decorate the given CG with respective attributes.
     * 
     * @param cg
     * @param associatedObjects
     */
    public void decorate(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext)
            throws Exception {
        List<BlockObject> associatedObjects = getAssociatedObjects(cg, requestContext);
        if (null != cg && !associatedObjects.isEmpty()) {
            decorateCG(cg, umv, associatedObjects, requestContext);
            decorateCGBlockObjects(cg, umv, associatedObjects, requestContext);
        }
        if (null != nextCGIngestDecorator) {
            associatedObjects = nextCGIngestDecorator.getAssociatedObjects(cg, requestContext);
            nextCGIngestDecorator.decorate(cg, umv, requestContext);
        }
    }

    public abstract void decorateCG(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception;

    public abstract void decorateCGBlockObjects(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext) throws Exception;

    /**
     * Set the next decorator to execute.
     * 
     * @param decorator
     */
    public abstract void setNextDecorator(BlockCGIngestDecorator decorator);

    protected List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, IngestionRequestContext requestContext)
            throws Exception {

        List<BlockObject> associatedObjects = new ArrayList<BlockObject>();
        URIQueryResultList cgVolumesQueryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeByReplicationGroupInstance(cg.getLabel()), cgVolumesQueryResult);
        Iterator<Volume> cgVolumesItr = dbClient.queryIterativeObjects(Volume.class, cgVolumesQueryResult);
        while (cgVolumesItr.hasNext()) {
            associatedObjects.add(cgVolumesItr.next());
        }
        URIQueryResultList cgSnapsQueryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getSnapshotReplicationGroupInstanceConstraint(cg.getLabel()),
                cgSnapsQueryResult);
        Iterator<BlockSnapshot> cgSnapsItr = dbClient.queryIterativeObjects(BlockSnapshot.class, cgSnapsQueryResult);
        while (cgSnapsItr.hasNext()) {
            associatedObjects.add(cgSnapsItr.next());
        }
        URIQueryResultList cgMirrorsQueryResult = new URIQueryResultList();

        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getMirrorReplicationGroupInstanceConstraint(cg.getLabel()),
                cgMirrorsQueryResult);
        Iterator<BlockMirror> cgMirrorsItr = dbClient.queryIterativeObjects(BlockMirror.class, cgMirrorsQueryResult);
        while (cgMirrorsItr.hasNext()) {
            associatedObjects.add(cgMirrorsItr.next());
        }
        return associatedObjects;

    }

    /**
     * @param dbClient the dbClient to set
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

}
