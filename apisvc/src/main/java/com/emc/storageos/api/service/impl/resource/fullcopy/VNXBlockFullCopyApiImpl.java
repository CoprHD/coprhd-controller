/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;


import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * The VNX storage system implementation for the block full copy API.
 */
public class VNXBlockFullCopyApiImpl extends DefaultBlockFullCopyApiImpl {
    
    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     */
    public VNXBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator,
        Scheduler scheduler) {
        super(dbClient, coordinator, scheduler);
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
        
        // For VNX, if a full copy source is itself a full copy, 
        // and it is not detached, full copy creation would fail.
        // So, we prevent a full copy of an attached full copy.
        Iterator<BlockObject> fcSourceObjIter = fcSourceObjList.iterator();
        while (fcSourceObjIter.hasNext()) {
            BlockObject fcSourceObj = fcSourceObjIter.next();
            URI fcSourceURI = fcSourceObj.getId();
            if (URIUtil.isType(fcSourceURI, Volume.class)) {
                Volume fcSourceVolume = (Volume) fcSourceObj;
                URI systemURI = fcSourceObj.getStorageController();
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
                if ((DiscoveredDataObject.Type.vnxblock.name().equals(system.getSystemType())) && 
                    (BlockFullCopyUtils.isVolumeFullCopy(fcSourceVolume, _dbClient)) && 
                    (!BlockFullCopyUtils.isFullCopyDetached(fcSourceVolume, _dbClient))) {
                    throw APIException.badRequests.cantCreateFullCopyOfVNXFullCopy();
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
}