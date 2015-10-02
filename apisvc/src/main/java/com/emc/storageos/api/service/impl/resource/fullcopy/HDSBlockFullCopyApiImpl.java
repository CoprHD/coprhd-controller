/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * The HDS storage system implementation for the block full copy API.
 */
public class HDSBlockFullCopyApiImpl extends DefaultBlockFullCopyApiImpl {

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     * @param fullCopyMgr A reference to the full copy manager.
     */
    public HDSBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator, Scheduler scheduler, BlockFullCopyManager fullCopyMgr) {
        super(dbClient, coordinator, scheduler, fullCopyMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        // No CG operation support for HDS.
        List<BlockObject> fcSourceObjList = new ArrayList<BlockObject>();
        fcSourceObjList.add(fcSourceObj);
        return fcSourceObjList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        if (!fcSourceObjList.isEmpty()) {
            URI fcSourceObjURI = fcSourceObjList.get(0).getId();
            if (URIUtil.isType(fcSourceObjURI, BlockSnapshot.class)) {
                // Not supported for snapshots.
                throw APIException.badRequests.fullCopyNotSupportedFromSnapshot(
                        DiscoveredDataObject.Type.hds.name(), fcSourceObjURI);
            } else {
                // Call super first.
                super.validateFullCopyCreateRequest(fcSourceObjList, count);

                // Now platform specific checks.
                for (BlockObject fcSourceObj : fcSourceObjList) {
                    // Verify the volume is exported.
                    Volume fcSourceVolume = (Volume) fcSourceObj;
                    if (!fcSourceVolume.isVolumeExported(_dbClient)) {
                        throw APIException.badRequests.sourceNotExported(fcSourceObjURI);
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray,
            String name, boolean createInactive, int count, String taskId) {
        return super.create(fcSourceObjList, varray, name, createInactive, count, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        return super.activate(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {
        return super.detach(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        return super.restoreSource(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        return super.resynchronizeCopy(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        return super.checkProgress(sourceURI, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyFullCopyRequestCount(BlockObject fcSourceObj, int count) {
        // Verify the requested copy count. For HDS
        // we must account for continuous copies as
        // well as full copies as they both count
        // against the shadow image pair limit for a
        // volume.
        Volume fcSourceVolume = (Volume) fcSourceObj;
        int currentMirrorCount = 0;
        if (null != fcSourceVolume.getMirrors()) {
            currentMirrorCount = fcSourceVolume.getMirrors().size();
        }
        BlockFullCopyUtils.validateActiveFullCopyCount(fcSourceObj, count,
                currentMirrorCount, _dbClient);
    }
}