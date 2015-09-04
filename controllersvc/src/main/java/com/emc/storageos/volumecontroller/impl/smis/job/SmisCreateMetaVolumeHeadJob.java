/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SmisCreateMetaVolumeHeadJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateMetaVolumeHeadJob.class);

    private URI _metaHeadId;
    private List<String> _metaMembers = new ArrayList<String>();
    private MetaVolumeTaskCompleter _metaVolumeTaskCompleter;

    public SmisCreateMetaVolumeHeadJob(CIMObjectPath cimJob, URI storageSystem, MetaVolumeTaskCompleter metaVolumeTaskCompleter,
            URI metaHeadId) {
        super(cimJob, storageSystem, metaVolumeTaskCompleter.getVolumeTaskCompleter(), "CreateMetaVolumeHead");
        _metaVolumeTaskCompleter = metaVolumeTaskCompleter;
        _metaHeadId = metaHeadId;
    }

    /**
     * Called to update the job status when the create meta volume head job completes.
     * Sets native device ID to meta head volume.
     *
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == Job.JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder =
                    new StringBuilder(String.format("Updating post processing status of job %s to %s, task: %s", this.getJobName(),
                            jobStatus.name(), opId));

            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
            iterator = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
            Calendar now = Calendar.getInstance();
            Volume metaHead = dbClient.queryObject(Volume.class, _metaHeadId);

            if (jobStatus == Job.JobStatus.SUCCESS) {
                CIMObjectPath volumePath = iterator.next();
                CIMProperty<String> deviceID = (CIMProperty<String>) volumePath.getKey(SmisConstants.CP_DEVICE_ID);
                String headNativeID = deviceID.getValue();
                metaHead.setCreationTime(now);
                metaHead.setNativeId(headNativeID);
                metaHead.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, metaHead));
                dbClient.persistObject(metaHead);
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "\n   Task %s created meta head volume: %s with device ID: %s", opId, metaHead.getLabel(), headNativeID));
                _log.info(logMsgBuilder.toString());
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create meta head volume: %s caused by: %s", opId, metaHead.getLabel(), _errorDescription));
                Volume volume = dbClient.queryObject(Volume.class, _metaHeadId);
                volume.setInactive(true);
                dbClient.persistObject(volume);
                _log.error(logMsgBuilder.toString());
                setFailedStatus(logMsgBuilder.toString());
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to process status for " + this.getJobName(), e);
            setPostProcessingErrorStatus("Encountered an internal error during " + this.getJobName() + " job status processing : "
                    + e.getMessage());
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            _metaVolumeTaskCompleter.setLastStepStatus(jobStatus);
            if (isJobInTerminalFailedState()) {
                super.updateStatus(jobContext);
            }
        }
    }
}
