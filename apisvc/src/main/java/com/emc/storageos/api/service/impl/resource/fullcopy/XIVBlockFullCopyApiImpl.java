/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * The IBM XIV storage system implementation for the block full copy API.
 */
public class XIVBlockFullCopyApiImpl extends DefaultBlockFullCopyApiImpl {

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     * @param fullCopyMgr A reference to the full copy manager.
     */
    public XIVBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator, Scheduler scheduler,
            BlockFullCopyManager fullCopyMgr) {
        super(dbClient, coordinator, scheduler, fullCopyMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        // No CG operation support for XIV.
        List<BlockObject> fcSourceObjList = new ArrayList<BlockObject>();
        fcSourceObjList.add(fcSourceObj);
        return fcSourceObjList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URI, Volume> getFullCopySetMap(BlockObject fcSourceObj,
            Volume fullCopyVolume) {
        Map<URI, Volume> fullCopyMap = new HashMap<URI, Volume>();
        fullCopyMap.put(fullCopyVolume.getId(), fullCopyVolume);
        return fullCopyMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        super.validateFullCopyCreateRequest(fcSourceObjList, count);
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
        throw APIException.methodNotAllowed.notSupportedForXIV();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        throw APIException.methodNotAllowed.notSupportedForXIV();
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
    protected void verifyCGVolumeRequestCount(int count) {
        // Do nothing here. XIV only supports clone of single volume,
        // thus no full copy count restriction in ViPR.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyCGSnapshotRequest() {
        // Do nothing here. XIV only supports clone of single volume,
        // this includes clone of snapshot of volume in a CG.
    }
}