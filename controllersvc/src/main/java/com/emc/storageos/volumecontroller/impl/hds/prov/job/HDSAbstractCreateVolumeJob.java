/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.ObjectLabel;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

/**
 * This class will have a base implementation of the
 * updateStatus for create volume operations.
 */
public abstract class HDSAbstractCreateVolumeJob extends HDSJob {
    private static final Logger _log = LoggerFactory.getLogger(HDSAbstractCreateVolumeJob.class);
    private URI storagePoolURI;

    public HDSAbstractCreateVolumeJob(String hdsJob, URI storageSystem, URI storagePoolURI, TaskCompleter taskCompleter, String name) {
        super(hdsJob, storageSystem, taskCompleter, name);
        this.storagePoolURI = storagePoolURI;
    }

    /**
     * Called to update the job status when the volume create job completes.
     * <p/>
     * This is common update code for volume create operations.
     *
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        List<LogicalUnit> luList = null;
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            int volumeCount = 0;
            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            
            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(HDSUtils.getHDSServerManagementServerInfo(storageSystem), storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            
            JavaResult javaResult = hdsApiClient.checkAsyncTaskStatus(getHDSJobMessageId());

            // If terminal state update storage pool capacity and remove reservation for  volumes capacity
            // from pool's reserved capacity map.
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
                HDSUtils.updateStoragePoolCapacity(dbClient, hdsApiClient, storagePool);
                StringMap reservationMap = storagePool.getReservedCapacityMap();
                for (URI volumeId : getTaskCompleter().getIds()) {
                    // remove from reservation map
                    reservationMap.remove(volumeId.toString());
                }
                dbClient.persistObject(storagePool);
            }
            boolean isThinVolumeRequest = checkThinVolumesRequest(getTaskCompleter().getIds(), dbClient);
            if (_status == JobStatus.SUCCESS) {
                List<URI> volumes = new ArrayList<URI>();
                luList = getLuListBasedOnModel(storageSystem, javaResult, isThinVolumeRequest);
                Iterator<LogicalUnit> luListItr = luList.iterator();
                Calendar now = Calendar.getInstance();
                while (luListItr.hasNext()) {
                    LogicalUnit logicalUnit = luListItr.next();
                    URI volumeId = getTaskCompleter().getId(volumeCount++);
                    volumes.add(volumeId);
                    processVolume(volumeId, logicalUnit, dbClient, hdsApiClient, now, logMsgBuilder);
                }
            } else if (_status == JobStatus.FAILED) {
                for (URI id : getTaskCompleter().getIds()) {
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format(
                            "Task %s failed to create volume: %s", opId, id.toString()));
                    BlockObject object = BlockObject.fetch(dbClient, id);
                    if(object!=null){
                    	object.setInactive(true);
                        dbClient.persistObject(object);
                    }
                }
            }
            _log.info(logMsgBuilder.toString());
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for HDSCreateVolumeJob", e);
            setErrorStatus("Encountered an internal error during volume create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }
    
    
    /**
     * This method is abstract and should be implemented by the derived class for
     * specific updates or processing for a derived class.
     *
     * @param dbClient     [in] - Client for reading/writing from/to database.
     * @param client       [in] - HDSApiClient for accessing Hitachi provider data
     * @param volume       [in] - Reference to Bourne's Volume object
     *
     */
    abstract void specificProcessing(DbClient dbClient, HDSApiClient client, Volume volume);
    
    /**
     * Return the LUList based on the model.
     * @param storageSystem
     * @param javaResult
     * @param isThinVolumeRequest
     * @return
     */
    private List<LogicalUnit> getLuListBasedOnModel(StorageSystem storageSystem, JavaResult javaResult,
            boolean isThinVolumeRequest) {
        List<Pool> arrayGroupList = (List<Pool>) javaResult
                .getBean(HDSConstants.ARRAYGROUP_RESPONSE_BEAN_ID);
        List<LogicalUnit> luList = getAllLogicalUnits(arrayGroupList);
        if (null == luList || luList.isEmpty()) {
            luList = (List<LogicalUnit>) javaResult.getBean(HDSConstants.LOGICALUNIT_LIST_BEAN_NAME);
        }
        return luList;
    }

    /**
     * When multiple thin volumes are created, volumes are reported under ArrayGroup seperately.
     * Hence we should iterate through all ArrayGroups and get all the logical units.
     * @param arrayGroupList
     * @return
     */
    private List<LogicalUnit> getAllLogicalUnits(List<Pool> arrayGroupList) {
        List<LogicalUnit> luList = new ArrayList<LogicalUnit>();
        if (null != arrayGroupList && !arrayGroupList.isEmpty()) {
            for (Pool pool : arrayGroupList) {
                if (null != pool.getVirtualLuList() && !pool.getVirtualLuList().isEmpty()) {
                    luList.addAll(pool.getVirtualLuList());
                }
            }
        }
        return luList;
    }

    /**
     * Verifies whether the request contains thin volumes or not.
     * @param volumeIds
     * @param dbClient
     * @return
     */
    private boolean checkThinVolumesRequest(List<URI> volumeIds, DbClient dbClient) {
        boolean isThinVolumeRequest = false;
        if (null != volumeIds && !volumeIds.isEmpty()) {
            for (URI volumeId : volumeIds) {
                //Volume volume = dbClient.queryObject(Volume.class, volumeId);
                Volume volume = (Volume)BlockObject.fetch(dbClient, volumeId);
                if (volume.getThinlyProvisioned().booleanValue()) {
                    isThinVolumeRequest = true;
                    break;
                }
            }
        }
        return isThinVolumeRequest;
    }

    /**
     * Process the LogicalUnit response received from server by setting the
     * LogicalUnit attributes in Volume db object.
     * @param volumeId : volume URI.
     * @param logicalUnit : LogicalUnit.
     * @param dbClient : dbClient reference.
     * @param hdsApiClient : HDS APIClient reference.
     * @param now : current timeStamp.
     * @param logMsgBuilder: log Msg.
     */
    private void processVolume(URI volumeId, LogicalUnit logicalUnit, DbClient dbClient,
            HDSApiClient hdsApiClient, Calendar now, StringBuilder logMsgBuilder) {
        try {
            //Volume volume = dbClient.queryObject(Volume.class, volumeId);
        	Volume volume = (Volume)BlockObject.fetch(dbClient, volumeId);
            volume.setCreationTime(now);
            volume.setNativeId(String.valueOf(logicalUnit.getDevNum()));
            volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
            long capacityInBytes = Long.valueOf(logicalUnit.getCapacityInKB()) * 1024L;
            volume.setAllocatedCapacity(capacityInBytes);
            volume.setWWN(HDSUtils.generateHitachiWWN(logicalUnit.getObjectID(), String.valueOf(logicalUnit.getDevNum())));
            volume.setProvisionedCapacity(capacityInBytes);
            volume.setInactive(false);
            dbClient.persistObject(volume);
            specificProcessing(dbClient, hdsApiClient, volume);
            if (logMsgBuilder.length() != 0) {
                logMsgBuilder.append("\n");
            }
            logMsgBuilder.append(String.format(
                    "Created volume successfully .. NativeId: %s, URI: %s", volume.getNativeId(),
                    getTaskCompleter().getId()));
        } catch (IOException e) {
            _log.error("Caught an exception while trying to update volume attributes", e);
        }
    }
    
    /**
     * Method will modify the name of a given volume to a generate name.
     *
     * @param dbClient   [in] - Client instance for reading/writing from/to DB
     * @param client     [in] - HDSApiClient used for reading/writing from/to HiCommand DM.
     * @param volume     [in] - Volume object
     */
    protected void changeVolumeName(DbClient dbClient, HDSApiClient client, Volume volume, String name) {
        try {
            _log.info(String.format("Attempting to add volume label %s to %s", name, volume.getWWN()));
            StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
            String systemObjectId = HDSUtils.getSystemObjectID(system);
            LogicalUnit logicalUnit = client.getLogicalUnitInfo(systemObjectId, HDSUtils.getLogicalUnitObjectId(volume.getNativeId(), system));
            if (null != logicalUnit && null != logicalUnit.getLdevList() && !logicalUnit.getLdevList().isEmpty()) {
                Iterator<LDEV> ldevItr = logicalUnit.getLdevList().iterator();
                if (ldevItr.hasNext()) {
                    LDEV ldev = ldevItr.next();
                    ObjectLabel objectLabel = client.addVolumeLabel(ldev.getObjectID(), name);
                    volume.setDeviceLabel(objectLabel.getLabel());
                    dbClient.persistObject(volume);
                }
            } else {
                _log.info("No LDEV's found on volume: {}", volume.getWWN());
            }
            _log.info(String.format("Volume label has been added to volume %s", volume.getWWN()));
        } catch (DatabaseException e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        } catch (Exception e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        }
    }
}
