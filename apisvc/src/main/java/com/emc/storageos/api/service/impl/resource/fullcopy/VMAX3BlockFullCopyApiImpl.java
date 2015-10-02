/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * The VMAX3 storage system implementation for the block full copy API,
 * which has some additional platform restrictions that do not apply
 * for older VMAX arrays.
 */
public class VMAX3BlockFullCopyApiImpl extends VMAXBlockFullCopyApiImpl {

    private static final String FULLCOPIES = "Full Copies";

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     * @param fullCopyMgr A reference to the full copy manager.
     */
    public VMAX3BlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator, Scheduler scheduler, BlockFullCopyManager fullCopyMgr) {
        super(dbClient, coordinator, scheduler, fullCopyMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        // Call super first.
        super.validateFullCopyCreateRequest(fcSourceObjList, count);

        // For VMAX3 you cannot have active snap and full copy sessions,
        // so verify there are no active snapshots for the volume. Note
        // that we know the source is a volume, because full copies are
        // not allowed for vmax snapshots, which would have been caught
        // in the call to super.
        for (BlockObject fcSourceObj : fcSourceObjList) {
            BlockServiceUtils.validateVMAX3ActiveSnapSessionsExists(fcSourceObj.getId(), _dbClient, FULLCOPIES);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotCreateRequest(Volume requestedVolume, List<Volume> volumesToSnap) {
        // For VMAX3 you cannot have active snap and full copy sessions,
        // so verify there are no active full copies for the volume.
        for (Volume volumeToSnap : volumesToSnap) {
            // Check if the volume to snap is an active full copy.
            if ((BlockFullCopyUtils.isVolumeFullCopy(volumeToSnap, _dbClient)) &&
                    (!BlockFullCopyUtils.isFullCopyDetached(volumeToSnap, _dbClient)) &&
                    (!BlockFullCopyUtils.isFullCopyInactive(volumeToSnap, _dbClient))) {
                throw APIException.badRequests.noSnapshotsForVMAX3VolumeWithActiveFullCopy();
            }

            // Now check if the volume to be snapped is a full copy source
            // that has active full copies.
            StringSet fullCopyIds = volumeToSnap.getFullCopies();
            if ((fullCopyIds != null) && (!fullCopyIds.isEmpty())) {
                Iterator<String> fullCopyIdsIter = fullCopyIds.iterator();
                while (fullCopyIdsIter.hasNext()) {
                    URI fullCopyURI = URI.create(fullCopyIdsIter.next());
                    Volume fullCopyVolume = _dbClient.queryObject(Volume.class, fullCopyURI);
                    if ((fullCopyVolume != null) && (!fullCopyVolume.getInactive()) &&
                            (!BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) &&
                            (!BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient))) {
                        throw APIException.badRequests.noSnapshotsForVMAX3VolumeWithActiveFullCopy();
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean volumeCanBeExpanded(Volume volume) {
        // VMAX3 does not yet support volume expansion, so
        // whether or not the volume is a full copy or a full
        // copy source is irrelevant.
        return false;
    }
}
