/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisWaitForGroupSynchronizedJob extends SmisJob{
    private static final Logger log = LoggerFactory.getLogger(SmisWaitForGroupSynchronizedJob.class);
    private static final String GROUP_SYNC_PATH = "GroupSynchronizationPath";
    private static final String COMPLETE = "100";

    public SmisWaitForGroupSynchronizedJob(CIMObjectPath groupSyncPath, URI storgeSystemURI, TaskCompleter taskCompleter) {
        super(null, storgeSystemURI, taskCompleter, "WaitForGroupSynchronization");
        _map.put(GROUP_SYNC_PATH, groupSyncPath);

    }
    
    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        JobPollResult pollResult = new JobPollResult();

        DbClient dbClient = jobContext.getDbClient();
        CIMConnectionFactory factory = jobContext.getCimConnectionFactory();
        WBEMClient client = getWBEMClient(dbClient, factory);

        TaskCompleter completer = getTaskCompleter();
        List<Volume> clones = dbClient.queryObject(Volume.class, completer.getIds());
        try {
            pollResult.setJobName(getJobName());
            pollResult.setJobId(SmisConstants.CP_PERCENT_SYNCED);
            pollResult.setJobStatus(JobStatus.IN_PROGRESS);
            
            CIMObjectPath path = getGroupSyncPath();
            // no corresponding sync obj, set to complete
            if (SmisConstants.NULL_IBM_CIM_OBJECT_PATH.equals(path)) {
                log.info("Sync complete");
                pollResult.setJobPercentComplete(100);
                pollResult.setJobStatus(JobStatus.SUCCESS);
                completer.ready(dbClient);                 
                return pollResult;
            }
            String[] propertyKeys =
                {SmisConstants.CP_SYNC_STATE, SmisConstants.CP_SYNC_TYPE, SmisConstants.CP_PERCENT_SYNCED,SmisConstants.CP_PROGRESS_STATUS };
            CIMInstance syncInstance = client.getInstance(path, false, false, propertyKeys);
            if (syncInstance != null) {
                String state = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_STATE);
                String type = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_TYPE);
                String percent = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_PERCENT_SYNCED);
                String status = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_PROGRESS_STATUS);
                String msg = String.format("Target=%s, State=%s, Type=%s, Percent=%s, Status=%s",
                        clones.get(0).getId(),state, type, percent, status);
                log.info(msg);
                pollResult.setJobPercentComplete(Integer.parseInt(percent));

                if (COMPLETE.equals(percent)) {
                    pollResult.setJobStatus(JobStatus.SUCCESS);
                    completer.ready(dbClient);
                }
            } else {
                pollResult.setJobStatus(JobStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("Failed to update synchronization", e);
            pollResult.setJobStatus(JobStatus.FAILED);
            completer.error(dbClient, DeviceControllerException.errors.jobFailed(e));
        } 

        return pollResult;
    }

    private CIMObjectPath getGroupSyncPath() {
        return (CIMObjectPath) _map.get(GROUP_SYNC_PATH);
    }

}
