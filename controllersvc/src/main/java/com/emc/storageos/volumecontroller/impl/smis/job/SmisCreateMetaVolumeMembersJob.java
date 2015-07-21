/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SmisCreateMetaVolumeMembersJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateMetaVolumeMembersJob.class);

    private int _count;
    private Volume _metaHead;
    private List<String> _metaMembers = new ArrayList<String>();
    private MetaVolumeTaskCompleter _metaVolumeTaskCompleter;

    public SmisCreateMetaVolumeMembersJob(CIMObjectPath cimJob, URI storageSystem, Volume metaHead, int count, MetaVolumeTaskCompleter metaVolumeTaskCompleter) {
        super(cimJob, storageSystem, metaVolumeTaskCompleter.getVolumeTaskCompleter(), "CreateMetaVolumeMembers");
        _metaVolumeTaskCompleter = metaVolumeTaskCompleter;
        _count = count;
        _metaHead = metaHead;
    }

    public List<String> getMetaMembers() {
        return _metaMembers;
    }


    /**
     * Called to update the job status when the create meta members job completes.
     *
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder =
                    new StringBuilder(String.format("Updating status of job %s to %s, task: %s", this.getJobName(), jobStatus.name(), opId));

            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
            iterator = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);

            if (jobStatus == JobStatus.SUCCESS) {
                // verify that all meta members have been created
                List<CIMObjectPath> volumePaths = new ArrayList<CIMObjectPath>();
                while (iterator.hasNext()) {
                    volumePaths.add(iterator.next());
                }

                if (volumePaths.size() != _count) {
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format("   Failed to create required number %s of meta members for meta head %s caused by %s: , task: %s.",
                            _count, _metaHead.getLabel(), _errorDescription, opId));
                    _log.error(logMsgBuilder.toString());
                    setFailedStatus(logMsgBuilder.toString());
                } else {
                    // Process meta members
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format("   Created required number %s of meta members for meta head %s, task: %s .",
                            _count, _metaHead.getLabel(), opId));
                    Iterator<CIMObjectPath> volumePathsIterator = volumePaths.iterator();
                    while (volumePathsIterator.hasNext()) {
                        CIMObjectPath volumePath = volumePathsIterator.next();
                        CIMProperty<String> deviceID = (CIMProperty<String>) volumePath.getKey(SmisConstants.CP_DEVICE_ID);
                        String nativeID = deviceID.getValue();
                        _metaMembers.add(nativeID);
                        logMsgBuilder.append(String.format("%n   Meta member device ID: %s", nativeID));
                    }
                    _log.info(logMsgBuilder.toString());
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create meta members for meta head volume: %s caused by: %s", opId, _metaHead.getLabel(), _errorDescription));
                _log.error(logMsgBuilder.toString());
                setFailedStatus(logMsgBuilder.toString());
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for " + this.getJobName(), e);
            setPostProcessingErrorStatus("Encountered an internal error during " + this.getJobName() + " job status processing : " + e.getMessage());
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            _metaVolumeTaskCompleter.setLastStepStatus(jobStatus);

            if (jobStatus != JobStatus.IN_PROGRESS) {
                // set meta members native ids in step data in WF
                String opId = _metaVolumeTaskCompleter.getVolumeTaskCompleter().getOpId();
                WorkflowService.getInstance().storeStepData(opId, _metaMembers);
                _log.debug("Set meta members for meta volume in WF. Members: {}", _metaMembers);
                // Also set meta members in volume itself. Can be used to do cleanup at delete time
                // (in case rollback fails).
                StringSet metaMembersSet = new StringSet(_metaMembers);
                _metaHead.setMetaVolumeMembers(metaMembersSet);
                dbClient.persistObject(_metaHead);
                _log.info("Set meta members for meta volume in metaHead. Members: {}", _metaMembers);
                // TEMPER USED for negative testing.
                // jobStatus = Job.JobStatus.FAILED;
                // TEMPER
            }
            // Do this last, after everything is complete. Do not update status in case of success. This is not independent
            // operation.
            if ( isJobInTerminalFailedState() ){
                super.updateStatus(jobContext);
            }
        }
    }
}
