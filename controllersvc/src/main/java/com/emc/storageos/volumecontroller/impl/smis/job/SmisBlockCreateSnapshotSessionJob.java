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
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * 
 */
@SuppressWarnings("serial")
public class SmisBlockCreateSnapshotSessionJob extends SmisJob {

    private static final String JOB_NAME = "SmisBlockCreateSnapshotSessionJob";

    //
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockCreateSnapshotSessionJob.class);

    /**
     * 
     * @param cimJob
     * @param systemURI
     * @param taskCompleter
     */
    public SmisBlockCreateSnapshotSessionJob(CIMObjectPath cimJob, URI systemURI, TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
    }

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
                // TBD - Need to resolve how parameter is used in snapshot session and block snapshot,
                // which keeps the instance Id of the SettingsData of the SettingsState.
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                syncAspectIter = client
                        .associatorNames(getCimJob(), null, SmisConstants.SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE, null, null);
                if (syncAspectIter.hasNext()) {
                    CIMObjectPath syncAspectPath = syncAspectIter.next();
                    settingsStateIter = client.referenceNames(syncAspectPath, SmisConstants.CIM_SETTINGS_DEFINE_STATE, null);
                    if (settingsStateIter.hasNext()) {
                        CIMObjectPath settingsStatePath = settingsStateIter.next();
                        String instanceId = settingsStatePath.toString();
                        s_logger.info("SettingsState instance id is {}", instanceId);
                        snapSession.setSessionInstance(instanceId);
                        dbClient.persistObject(snapSession);
                    }
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
