/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;
import java.net.URI;

public class SmisCloneVolumeJob extends SmisReplicaCreationJobs {
    private static final Logger _log = LoggerFactory.getLogger(SmisCloneVolumeJob.class);

    public SmisCloneVolumeJob(CIMObjectPath job, URI storgeSystemURI, TaskCompleter taskCompleter) {
        super(job, storgeSystemURI, taskCompleter, "CloneVolume");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        _log.info("START updateStatus for clone volume");
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            CIMConnectionFactory cimConnectionFactory;
            WBEMClient client = null;
            CloneCreateCompleter completer = (CloneCreateCompleter) getTaskCompleter();
            Volume cloneVolume = dbClient.queryObject(Volume.class, completer.getId());

            // If terminal state update storage pool capacity and remove reservation for  volume capacity
            // from pool's reserved capacity map.
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                cimConnectionFactory = jobContext.getCimConnectionFactory();
                client = getWBEMClient(dbClient, cimConnectionFactory);
                URI poolURI = cloneVolume.getPool();
                SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);

                StoragePool pool = dbClient.queryObject(StoragePool.class, poolURI);
                StringMap reservationMap = pool.getReservedCapacityMap();
                // remove from reservation map
                reservationMap.remove(cloneVolume.getId().toString());
                dbClient.persistObject(pool);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("Clone creation success");

                iterator = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                if (iterator.hasNext()) {
                    CIMObjectPath cloneVolumePath = iterator.next();
                    CIMInstance syncVolume = client.getInstance(cloneVolumePath, false, false, null);
                    
                    String deviceId = cloneVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
                    String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
                    String alternateName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_NAME);
                    cloneVolume.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
                    cloneVolume.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
                    cloneVolume.setWWN(wwn.toUpperCase());
                    cloneVolume.setAlternateName(alternateName);
                    cloneVolume.setNativeId(deviceId);
                    cloneVolume.setDeviceLabel(elementName);
                    cloneVolume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, cloneVolume));
                    cloneVolume.setInactive(false);
                    if (cloneVolume.getSyncActive()) {
                        cloneVolume.setReplicaState(ReplicationState.CREATED.name());
                    } else {
                        cloneVolume.setReplicaState(ReplicationState.INACTIVE.name());
                    }
                    dbClient.persistObject(cloneVolume);
                    
                }
                /*
                for (URI id : completer.getIds()) {
                    completer.ready(dbClient);
                }*/
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                String msg = String.format("Failed job to create full copy from %s to %s",
                        cloneVolume.getAssociatedSourceVolume(), cloneVolume.getId());
                _log.error(msg);
                cloneVolume.setInactive(true);
                dbClient.persistObject(cloneVolume);
            }
        } catch (Exception e) {
            String errorMsg = String.format("Encountered an internal error during block create clone job status " +
                    "processing: %s", e.getMessage());
            setPostProcessingErrorStatus(errorMsg);
            _log.error("Failed to update status for " + getClass().getSimpleName(), e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            super.updateStatus(jobContext);
            _log.info("FINISH updateStatus for clone volume");
        }
    }
}
