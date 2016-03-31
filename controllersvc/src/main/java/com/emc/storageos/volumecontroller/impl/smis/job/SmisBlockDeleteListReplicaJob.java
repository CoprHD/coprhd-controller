/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

public class SmisBlockDeleteListReplicaJob extends SmisJob {
    private static final long serialVersionUID = 1L;
    private static Logger _log = LoggerFactory.getLogger(SmisBlockDeleteListReplicaJob.class);

    public SmisBlockDeleteListReplicaJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "DeleteListReplica");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            List<? extends BlockObject> replicas = BlockObject.fetch(dbClient, getTaskCompleter().getIds());

            // If terminal state update storage pool capacity
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                Set<URI> poolURIs = new HashSet<URI>();
                for (BlockObject replica : replicas) {
                    if (replica instanceof Volume)
                    poolURIs.add(((Volume)replica).getPool());
                }

                for (URI poolURI : poolURIs) {
                    // Update capacity of storage pools.
                    SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);
                }
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("List replica delete success");
                dbClient.markForDeletion(replicas);
            } else if (jobStatus == JobStatus.FATAL_ERROR || jobStatus == JobStatus.FAILED) {
                String msg = String.format("Failed to delete list replica");
                _log.error(msg);
                getTaskCompleter().error(dbClient, DeviceControllerErrors.smis.jobFailed(msg));
            }
        } catch (Exception e) {
            setFatalErrorStatus("Encountered an internal error during block delete replica replica job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockDeleteListReplicaJob", e);
            getTaskCompleter().error(dbClient, DeviceControllerErrors.smis.jobFailed(e.getMessage()));
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
