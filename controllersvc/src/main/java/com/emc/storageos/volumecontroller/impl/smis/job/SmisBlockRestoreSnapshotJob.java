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

import java.net.URI;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

public class SmisBlockRestoreSnapshotJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockRestoreSnapshotJob.class);

    public SmisBlockRestoreSnapshotJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "RestoreBlockSnapshot");
    }

    /**
     * Called to update the job status when the restore snapshot job completes.
     *
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        _log.info("Updating status of SmisBlockRestoreSnapshotJob");
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        URI snapshotId = getTaskCompleter().getId();

        if (jobStatus == JobStatus.SUCCESS && snapshotId != null && URIUtil.isType(snapshotId, BlockSnapshot.class)) {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotId);
            if (snapshot != null && !NullColumnValueGetter.isNullNamedURI(snapshot.getParent())) {
                Volume parentVolume = dbClient.queryObject(Volume.class, snapshot.getParent());
                if (parentVolume != null) {
                    StorageSystem storageSystem = dbClient.queryObject(
                            StorageSystem.class, parentVolume.getStorageController());
                    if (parentVolume.checkForRp()
                            && !NullColumnValueGetter.isNullURI(parentVolume.getProtectionController())
                            && storageSystem.getSystemType() != null
                            && storageSystem.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString())) {
                        // Now re-enable the RP tag on the volume. The tag was removed initially to perform the
                        // restore so it must be tagged again now that the restore is complete.
                        SmisCommandHelper helper = jobContext.getSmisCommandHelper();
                        _log.info(String.format("Enabling the RecoverPoint tag on volume %s", parentVolume.getId().toString()));
                        helper.doApplyRecoverPointTag(storageSystem, parentVolume, true);
                    }
                }
            }
        }

        super.updateStatus(jobContext);
    }
}
