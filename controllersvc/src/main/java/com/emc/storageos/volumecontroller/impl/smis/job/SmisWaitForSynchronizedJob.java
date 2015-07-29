/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import java.net.URI;

public class SmisWaitForSynchronizedJob extends SmisJob {

    private static final Logger log = LoggerFactory.getLogger(SmisWaitForSynchronizedJob.class);
    private static final String TARGET_PATH = "TargetPath";
    private static final String COMPLETE = "100";
    private Class<? extends BlockObject> clazz;

    public SmisWaitForSynchronizedJob(Class<? extends BlockObject> clazz, CIMObjectPath targetPath, URI storageSystem,
            TaskCompleter taskCompleter) {
        super(null, storageSystem, taskCompleter, "WaitForSynchronized");
        _map.put(TARGET_PATH, targetPath);
        this.clazz = clazz;
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        JobPollResult pollResult = new JobPollResult();

        DbClient dbClient = jobContext.getDbClient();
        CIMConnectionFactory factory = jobContext.getCimConnectionFactory();
        WBEMClient client = getWBEMClient(dbClient, factory);

        TaskCompleter completer = getTaskCompleter();
        BlockObject target = dbClient.queryObject(clazz, completer.getId());
        CloseableIterator<CIMInstance> references = null;

        try {
            pollResult.setJobName(getJobName());
            pollResult.setJobId(SmisConstants.CP_PERCENT_SYNCED);
            pollResult.setJobStatus(JobStatus.IN_PROGRESS);

            CIMObjectPath path = getTargetPath();
            // no corresponding sync obj, set to complete
            if (SmisConstants.NULL_IBM_CIM_OBJECT_PATH.equals(path)) {
                log.info("Sync complete");
                pollResult.setJobPercentComplete(100);
                pollResult.setJobStatus(JobStatus.SUCCESS);
                completer.ready(dbClient);
                return pollResult;
            }

            references = client.referenceInstances(getTargetPath(),
                    SmisConstants.CIM_STORAGE_SYNCHRONIZED, null, false, null);
            if (references.hasNext()) {
                CIMInstance syncInstance = references.next();
                String state = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_STATE);
                String type = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_TYPE);
                String percent = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_PERCENT_SYNCED);
                String status = CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_PROGRESS_STATUS);
                String msg = String.format("Target=%s, State=%s, Type=%s, Percent=%s, Status=%s",
                        target.getId(), state, type, percent, status);
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
        } finally {
            if (references != null) {
                references.close();
            }
        }

        return pollResult;
    }

    private CIMObjectPath getTargetPath() {
        return (CIMObjectPath) _map.get(TARGET_PATH);
    }
}
