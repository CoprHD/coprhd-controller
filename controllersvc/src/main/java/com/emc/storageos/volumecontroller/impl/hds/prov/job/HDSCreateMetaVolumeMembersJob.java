/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.workflow.WorkflowService;

import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class HDSCreateMetaVolumeMembersJob extends HDSJob {
    private static final Logger _log = LoggerFactory.getLogger(HDSCreateMetaVolumeMembersJob.class);

    private int count;
    private Volume metaHead;
    // Actually denotes the LogicalUnit id's.
    private List<String> metaMembers = new ArrayList<String>();
    
    private MetaVolumeTaskCompleter metaVolumeTaskCompleter;

    public HDSCreateMetaVolumeMembersJob(String messageId, URI storageSystem, Volume metaHead, int count, MetaVolumeTaskCompleter metaVolumeTaskCompleter) {
        super(messageId, storageSystem, metaVolumeTaskCompleter.getVolumeTaskCompleter(), "CreateMetaVolumeMembers");
        this.metaVolumeTaskCompleter = metaVolumeTaskCompleter;
        this.count = count;
        this.metaHead = metaHead;
    }

    public List<String> getMetaMembers() {
        return metaMembers;
    }


    public JobStatus getStatus() {
        return _status;
    }

    /**
     * Called to update the job status when the create meta members job completes.
     *
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder =
                    new StringBuilder(String.format("Updating status of job %s to %s, task: %s", this.getJobName(), _status.name(), opId));
            
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());

            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(HDSUtils.getHDSServerManagementServerInfo(storageSystem), storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            
            JavaResult javaResult = hdsApiClient.checkAsyncTaskStatus(getHDSJobMessageId());
            if (_status == JobStatus.SUCCESS) {
                List<LogicalUnit> luList = (List<LogicalUnit>) javaResult.getBean(HDSConstants.LOGICALUNIT_LIST_BEAN_NAME);
                List<String> luObjectIdList = new ArrayList<String>();
                // verify that all meta members have been created
                if (null != luList && !luList.isEmpty()) {
                    for (LogicalUnit lu : luList) {
                        luObjectIdList.add(lu.getObjectID());
                    }
                }

                if (luObjectIdList.size() != count) {
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format("   Failed to create required number %s of meta members for meta head %s, task: %s .",
                            count, metaHead.getLabel(), opId));
                    _log.error(logMsgBuilder.toString());
                    setFailedStatus(logMsgBuilder.toString());
                } else {
                    // Process meta members
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format("   Created required number %s of meta members for meta head %s, task: %s .",
                            count, metaHead.getLabel(), opId));
                    metaMembers.addAll(luObjectIdList);
                    logMsgBuilder.append(String.format("\n   Meta member device ID's: %s", metaMembers));
                   
                    _log.info(logMsgBuilder.toString());
                }
            } else if (_status == JobStatus.FAILED) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create meta members for meta head volume: %s", opId, metaHead.getLabel()));
                _log.error(logMsgBuilder.toString());
                setFailedStatus(logMsgBuilder.toString());
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for " + this.getJobName(), e);
            setErrorStatus("Encountered an internal error during " + this.getJobName() + " job status processing : " + e.getMessage());
        } finally {
           
            metaVolumeTaskCompleter.setLastStepStatus(_status);

            if (_status != JobStatus.IN_PROGRESS) {
                // set meta members native ids in step data in WF
                String opId = metaVolumeTaskCompleter.getVolumeTaskCompleter().getOpId();
                WorkflowService.getInstance().storeStepData(opId, metaMembers);
                _log.debug("Set meta members for meta volume in WF. Members: {}", metaMembers);
            }
            // Do this last, after everything is complete. Do not update status in case of success. This is not independent
            // operation.
            if (_status == Job.JobStatus.FAILED || _status == Job.JobStatus.ERROR) {
                super.updateStatus(jobContext);
            }
        }
    }
}
