/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

/**
 * A VMAX Remove Volume job
 */
public class SmisMaskingViewRemoveVolumeJob extends SmisJob
{
    private static final Logger _log = LoggerFactory.getLogger(SmisMaskingViewRemoveVolumeJob.class);
    List<CIMObjectPath> volumePaths;
    private String parentGroupName;
    private String childGroupName;
    private CIMObjectPathFactory cimPath;

    public SmisMaskingViewRemoveVolumeJob(CIMObjectPath cimJob,
            URI storageSystem,
            List<CIMObjectPath> volumePaths,
            String parentGroupName,
            String childGroupName,
            CIMObjectPathFactory _cimPath,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "RemoveVolumeFromMaskingView");
        this.volumePaths = volumePaths;
        this.parentGroupName = parentGroupName;
        this.childGroupName = childGroupName;
        this.cimPath = _cimPath;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.smis.job.SmisJob#updateStatus(com.emc.storageos.volumecontroller.JobContext)
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        try {
            JobStatus jobStatus = getJobStatus();
            if (jobStatus == JobStatus.SUCCESS) {
                DbClient dbClient = jobContext.getDbClient();
                SmisCommandHelper helper = jobContext.getSmisCommandHelper();
                StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                _log.info("Updating status of SmisMaskingViewRemoveVolumeJob");
                removeEmptyStorageGroups(helper, storageSystem);
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for SmisRemoveMaskingViewJob", e);
            setPostProcessingErrorStatus("Encountered an internal error during masking view remove job status processing : "
                    + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    private void removeEmptyStorageGroups(SmisCommandHelper helper, StorageSystem storage) {
        try {
            // disassociate the Group from parent if any, and remove the Group, if empty Group found
            int volumeCount = helper.getVMAXStorageGroupVolumeCount(storage, childGroupName);
            if (volumeCount == 0) {
                if (parentGroupName != null) {
                    _log.info("Child Group {} size 0, trying to remove the child Group from parent", childGroupName);
                    if (helper.findStorageGroupAChildOfParent(storage, childGroupName, parentGroupName)) {
                        _log.info("Disassociating Child Group {} from parent Group {}", childGroupName, parentGroupName);
                        CIMObjectPath childGroupPath = cimPath.getMaskingGroupPath(storage, childGroupName,
                                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                        // dissociating empty child from parent.
                        helper.removeGroupsFromCascadedVolumeGroup(storage, parentGroupName, childGroupPath, null, false);
                    } else {
                        _log.warn(
                                "Child Group {} is not part of a parent {} created through ViPR, hence child Group will not be disassociated ",
                                childGroupName, parentGroupName);
                    }
                }

                // delete the child Group explicitly
                _log.info("Deleting Child Group {}", childGroupName);
                helper.deleteMaskingGroup(storage, childGroupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
            }
        } catch (Exception e) {
            _log.warn("Removal of empty SGs failed, but it doesnt impact the export in any way.", e);
        }
    }

}