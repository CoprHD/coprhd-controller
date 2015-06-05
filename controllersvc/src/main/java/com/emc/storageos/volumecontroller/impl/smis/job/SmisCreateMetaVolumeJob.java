/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.net.URI;
import java.util.ArrayList;

public class SmisCreateMetaVolumeJob extends SmisCreateVolumeJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateMetaVolumeJob.class);

    private MetaVolumeTaskCompleter _metaVolumeTaskCompleter;
    private Volume _metaHead;
    Boolean _isLastJob;

    public SmisCreateMetaVolumeJob(CIMObjectPath cimJob,
                                   URI storageSystem,
                                   URI storagePool,
                                   Volume metaHead,
                                   MetaVolumeTaskCompleter metaVolumeTaskCompleter,
                                   Boolean isLastJob) {
        super(cimJob, storageSystem, storagePool, metaVolumeTaskCompleter.getVolumeTaskCompleter(), "CreateMetaVolume");
        _metaVolumeTaskCompleter = metaVolumeTaskCompleter;
        _metaHead = metaHead;
        _isLastJob = isLastJob;
    }

    /**
     * Called to update the job status when the create meta volume job completes.
     *
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        try {
            DbClient dbClient = jobContext.getDbClient();

            if (jobStatus == Job.JobStatus.IN_PROGRESS) {
                return;
            } else if (jobStatus == JobStatus.SUCCESS) {
                // Reset list of meta members native ids in WF data (when meta is created meta members are removed from array)
                String opId = _metaVolumeTaskCompleter.getVolumeTaskCompleter().getOpId();
                WorkflowService.getInstance().storeStepData(opId, new ArrayList<String>());
                // Reset list  of meta member volumes in the meta head
                _metaHead.getMetaVolumeMembers().clear();
                dbClient.persistObject(_metaHead);
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to process status for " + this.getJobName(), e);
            setPostProcessingErrorStatus("Encountered an internal error during " + this.getJobName() + " job status processing : " + e.getMessage());
        } finally {
            _metaVolumeTaskCompleter.setLastStepStatus(jobStatus);
            if (_isLastJob) {
            super.updateStatus(jobContext);
            }  else {
                // Complete only if error (this is not the last job for meta volume create).
                if ( isJobInTerminalFailedState() ) {
                    super.updateStatus(jobContext);
        }
    }
        }
    }
}
