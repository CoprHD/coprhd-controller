/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisVnxCreateCGCloneJob extends SmisReplicaCreationJobs {

    private static final Logger _log = LoggerFactory.getLogger(SmisVnxCreateCGCloneJob.class);
    protected Boolean isSyncActive;

    public SmisVnxCreateCGCloneJob(CIMObjectPath job, URI storgeSystemURI, Boolean syncActive, TaskCompleter taskCompleter) {
        super(job, storgeSystemURI, taskCompleter, "CreateGroupClone");
        this.isSyncActive = syncActive;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> syncVolumeIter = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            CloneCreateCompleter completer = (CloneCreateCompleter) getTaskCompleter();
            List<Volume> clones = dbClient.queryObject(Volume.class, completer.getIds());
            if (jobStatus == JobStatus.SUCCESS) {
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                // generate a UUID for the set of clones
                String setId = UUID.randomUUID().toString();
                syncVolumeIter = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                processCGClones(syncVolumeIter, client, dbClient, clones, setId, isSyncActive);
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create clone");
                for (Volume clone : clones) {
                    clone.setInactive(true);
                }
                dbClient.persistObject(clones);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during create CG clone job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisVnxCreateCGCloneJob", e);
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }

}
