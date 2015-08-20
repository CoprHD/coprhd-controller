/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisCreateCGCloneJob extends SmisReplicaCreationJobs {

    private static final Logger _log = LoggerFactory.getLogger(SmisCreateCGCloneJob.class);
    protected Boolean isSyncActive;

    public SmisCreateCGCloneJob(CIMObjectPath job, URI storgeSystemURI, Boolean syncActive, TaskCompleter taskCompleter) {
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
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            if (jobStatus == JobStatus.SUCCESS) {

                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

                CIMObjectPath replicationGroupPath = client.associatorNames(getCimJob(), null, SmisConstants.SE_REPLICATION_GROUP, null,
                        null).next();
                String replicationGroupID = (String) replicationGroupPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();

                // VMAX instanceID, e.g., 000196700567+EMC_SMI_RG1414546375042 (8.0.2 provider)
                final String replicationGroupInstance = replicationGroupID.split(Constants.PATH_DELIMITER_REGEX)[storage.getUsingSmis80() ? 1
                        : 0];
                syncVolumeIter = client.associatorNames(replicationGroupPath, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                processCGClones(syncVolumeIter, client, dbClient, clones, replicationGroupInstance, isSyncActive);
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create clone");
                for (Volume clone : clones) {
                    clone.setInactive(true);
                }
                dbClient.persistObject(clones);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during create CG clone job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisCreateCGCloneJob", e);
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
