package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 */
@SuppressWarnings("serial")
public class SmisBlockLinkSnapshotSessionTargetJob extends SmisJob {

    private static final String JOB_NAME = "SmisBlockLinkSnapshotSessionTargetJob";

    //
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockCreateSnapshotSessionJob.class);

    /**
     * 
     * @param cimJob
     * @param systemURI
     * @param taskCompleter
     */
    public SmisBlockLinkSnapshotSessionTargetJob(CIMObjectPath cimJob, URI systemURI, TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        try {
            @SuppressWarnings("unused")
            DbClient dbClient = jobContext.getDbClient();
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                s_logger.info("Post-processing successful link snapshot session target for task ", getTaskCompleter().getOpId());
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to link snapshot session target for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in link snapshot session target job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in link snapshot session target job status processing", e);
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
