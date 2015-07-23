/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * 
 */
public abstract class AbstractBlockSnapshotSessionApiImpl implements BlockSnapshotSessionApi {

    // A reference to a database client.
    protected DbClient _dbClient;
    
    // A reference to the coordinator.
    protected CoordinatorClient _coordinator = null;

    @SuppressWarnings("unused")
    private static final Logger s_logger = LoggerFactory.getLogger(AbstractBlockSnapshotSessionApiImpl.class);
    
    /**
     * Protected default constructor.
     */
    protected  AbstractBlockSnapshotSessionApiImpl() {
    }
    
    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     */
    protected AbstractBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator) {
        _dbClient = dbClient;
        _coordinator = coordinator;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForSnapshotSessionRequest(BlockObject sourceObj) {
        // TBD handle source objects in CGs.
        List<BlockObject> sourceObjList = new ArrayList<BlockObject>();
        sourceObjList.add(sourceObj);
        return sourceObjList;
    }
    
    /**
     * {@inheritDoc}
     * TBD Reconcile with implementation in AbstractBlockServiceApi.
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj,
        List<BlockObject> sourceObjList, String name, BlockFullCopyManager fcManager) {
        for (BlockObject sourceObj : sourceObjList) {
            URI sourceObjURI = sourceObj.getId();
            if (URIUtil.isType(sourceObjURI, Volume.class)) {
                VirtualPool vpool = BlockSnapshotSessionUtils.querySnapshotSessionSourceVPool(sourceObj, _dbClient);
                // Verify that array snapshots are allowed.
                int maxNumberOfArraySnapsForSource = vpool.getMaxNativeSnapshots();
                if (maxNumberOfArraySnapsForSource == 0)
                    throw APIException.badRequests.maxNativeSnapshotsIsZero(vpool.getLabel());
                // Verify the number of array snapshots does not exceed
                // the limit for the platform.
                if (getNumNativeSnapshots((Volume)sourceObj) >= vpool.getMaxNativeSnapshots()){
                    throw APIException.methodNotAllowed.maximumNumberSnapshotsReached();
                }
                
                // Check for duplicate name.
                checkForDuplicatSnapshotName(name, (Volume)sourceObj);
            } else {
                // TBD 
                // What about when the source is a BlockSnapshot. It has no vpool
                // and no max snaps value. It could be determined from and be the 
                // same as the source device. Or, these cascaded snaps could be
                // cumulative and count against the max for the source.
            }    
        }
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList createSnapshotSession() {
        return new TaskList();
    }
    
    /**
     * Counts and returns the number of arrays snapshots for 
     * the passed volume.
     * 
     * TBD Reconcile with implementation in AbstractBlockServiceApiImpl,
     * which needs to be updated.
     *
     * @param volume A reference to a snapshot source volume.
     * 
     * @return The number of array snapshots on a volume.
     */
    protected Integer getNumNativeSnapshots(Volume volume){
        // The number of native array snapshots is determined by the 
        // number of snapshots sessions for which the passed volume
        // is the source.
        Integer numSnapshots = 0;
        URI volumeURI = volume.getId();
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(
            volumeURI), queryResults);
        Iterator<URI> queryResultsIter = queryResults.iterator();
        while (queryResultsIter.hasNext()) {
            URI snapSessionURI = queryResultsIter.next();
            BlockSnapshotSession snapSession = _dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
            // TBD took out snapshot type == native
            if ((snapSession != null) && (!snapSession.getInactive())) {
                numSnapshots++;
            }
        }
        return numSnapshots;
    }
    
    /**
     * Check if a array snapshot with the same name exists for the passed volume.
     * Note that we need to compare the passed name to the session label for
     * the volumes snapshot sessions because the actual session names can have
     * an appended suffix when the volume is in a CG with multiple volumes.
     * Also, we need to run the name through the generator, which is done 
     * prior to setting the session label for a snapshot session.
     * 
     * TBD make sure to run the session label through the generator.
     * 
     * TBD Reconcile with implementation in AbstractBlockServiceApiImpl,
     * which needs to be updated.
     * 
     * @param requestedName The name to verify.
     * @param volume A reference to a snapshot source volume.
     */
    protected void checkForDuplicatSnapshotName(String requestedName, Volume volume) {
        String sessionLabel = ResourceOnlyNameGenerator.removeSpecialCharsForName(
            requestedName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility
            .queryActiveResourcesByConstraint(_dbClient, BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(volume.getId()));
        for (BlockSnapshotSession snapSession : snapSessions) {
            if (sessionLabel.equals(snapSession.getSessionLabel())) {
                throw APIException.badRequests.duplicateLabel(requestedName);
            }
        }
    }
}
