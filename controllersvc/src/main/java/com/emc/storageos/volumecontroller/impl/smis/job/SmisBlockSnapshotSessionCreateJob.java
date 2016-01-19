/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * ViPR Job created when an underlying CIM job is created to create
 * a new array snapshot point-in-time copy represented in ViPR by a
 * BlockSnapshotSession instance.
 */
@SuppressWarnings("serial")
public class SmisBlockSnapshotSessionCreateJob extends SmisJob {

    // The unique job name.
    private static final String JOB_NAME = "SmisBlockSnapshotSessionCreateJob";

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockSnapshotSessionCreateJob.class);

    /**
     * Constructor.
     * 
     * @param cimJob The CIM object path of the underlying CIM Job.
     * @param systemURI The URI of the storage system.
     * @param taskCompleter A reference to the task completer.
     */
    public SmisBlockSnapshotSessionCreateJob(CIMObjectPath cimJob, URI systemURI, TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        CloseableIterator<CIMObjectPath> syncAspectIter = null;
        CloseableIterator<CIMObjectPath> settingsStateIter = null;
        try {
            DbClient dbClient = jobContext.getDbClient();
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                s_logger.info("Post-processing successful snapshot session creation for task ", getTaskCompleter().getOpId());

                // Get the snapshot session.
                BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, getTaskCompleter().getId());

                // Update Settings instance for the session.
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                syncAspectIter = client.associatorNames(getCimJob(), null,
                        SmisConstants.SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE, null, null);
                if (syncAspectIter.hasNext()) {
                    CIMObjectPath syncAspectPath = syncAspectIter.next();
                    String instanceId = syncAspectPath.getKeyValue(Constants.INSTANCEID).toString();
                    s_logger.info("SynchronizationAspect instance id is {}", instanceId);
                    snapSession.setSessionInstance(instanceId);
                    dbClient.updateObject(snapSession);
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to create snapshot session for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in create snapshot session job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in create snapshot session job status processing", e);
        } finally {
            if (syncAspectIter != null) {
                syncAspectIter.close();
            }
            if (settingsStateIter != null) {
                settingsStateIter.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
