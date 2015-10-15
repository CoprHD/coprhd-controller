/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
/**
 * 
 * @author Administrator
 *
 */
public class SmisSnapvxSessionCreateJob extends SmisJob{
	
	private static final Logger log = LoggerFactory.getLogger(SmisSnapvxSessionCreateJob.class);

	public SmisSnapvxSessionCreateJob(CIMObjectPath cimJob, URI storageSystem,
			TaskCompleter taskCompleter) {
		super(cimJob, storageSystem, taskCompleter, "CreateSnapVXSession");
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
                log.info("Post-processing successful snapshot session creation for task ", getTaskCompleter().getOpId());

                // Get the snapshot .
                BlockSnapshot snapObject = dbClient.queryObject(BlockSnapshot.class, getTaskCompleter().getId());

                // Update Settings instance for the snapshot.
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
                        log.info("SettingsState instance id is {}", instanceId);
                        snapObject.setSettingsInstance(instanceId);
                        dbClient.updateObject(snapObject);
                        /**
                         * TODO we need to terminate the old session after creating new one.
                         */
                    }
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                log.info("Failed to create snapshot session for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in create snapshot session job status processing: "
                    + e.getMessage());
            log.error("Encountered an internal error in create snapshot session job status processing", e);
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
