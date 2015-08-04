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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A job used for creating VDEVs for a snapshot target group
 */
public class SmisCreateVmaxCGTargetVolumesJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateVmaxCGTargetVolumesJob.class);

    private List<String> _deviceIds;

    public SmisCreateVmaxCGTargetVolumesJob(CIMObjectPath cimJob, URI storageSystem,
            String sourceGroupName,
            String snapshotLabel, Boolean createInactive,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "CreateVdevVolume");
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.SUCCESS) {
                _deviceIds = new ArrayList<String>();
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                iterator = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                while (iterator.hasNext()) {
                    CIMObjectPath volumePath = iterator.next();
                    CIMProperty<String> deviceID = (CIMProperty<String>) volumePath.getKey(SmisConstants.CP_DEVICE_ID);
                    String nativeID = deviceID.getValue();
                    if (nativeID == null || nativeID.isEmpty()) {
                        throw new IllegalStateException("Could not determine volume native ID from the SMI-S provider");
                    }
                    _deviceIds.add(nativeID);
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create volume: {}", getTaskCompleter().getId());
            }
        } catch (Exception e) {
            setPostProcessingFailedStatus(e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisCreateVmaxCGTargetVolumesJob", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("updateStatus", e.getMessage());
            getTaskCompleter().error(dbClient, error);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    public List<String> getTargetDeviceIds() {
        return _deviceIds;
    }
}
