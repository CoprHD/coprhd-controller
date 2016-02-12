/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.List;

import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * The RecoverPoint storage system implementation for the block full copy API.
 */
public class RPBlockFullCopyApiImpl extends AbstractBlockFullCopyApiImpl {

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     * @param fullCopyMgr A reference to the full copy manager.
     */
    public RPBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator, Scheduler scheduler, BlockFullCopyManager fullCopyMgr) {
        super(dbClient, coordinator, scheduler, fullCopyMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        return super.getAllSourceObjectsForFullCopyRequest(fcSourceObj);
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
        BlockFullCopyApi fullCopyApiImpl = null;
        if (fcSourceObjList != null && !fcSourceObjList.isEmpty()) {
            fullCopyApiImpl = getFullCopyApiImpl(fcSourceObjList.get(0));
        }
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.create(fcSourceObjList, varray, name, createInactive, count, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(fcSourceObj);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.activate(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(fcSourceObj);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.detach(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(sourceVolume);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.restoreSource(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(sourceVolume);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.resynchronizeCopy(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * get the full copy implementation based on the block object
     * 
     * @param fcSourceObj block object
     * @return
     */
    private BlockFullCopyApi getFullCopyApiImpl(BlockObject fcSourceObj) {
        BlockFullCopyApi fullCopyApiImpl = null;
        if (!NullColumnValueGetter.isNullURI(fcSourceObj.getStorageController())) {
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, fcSourceObj.getStorageController());
            if (storage.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
                fullCopyApiImpl = _fullCopyMgr.getVplexFullCopyImpl();
            } else {
                fullCopyApiImpl = _fullCopyMgr.getPlatformSpecificFullCopyImplForSystem(storage);
            }
        }
        return fullCopyApiImpl;
    }
}
