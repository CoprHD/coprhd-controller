/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Calendar;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * ViPR Job created when an underlying CIM job is created to create
 * and link a new target volume to an array array snapshot point-in-time
 * copy represented in ViPR by a BlockSnapshotSession instance.
 * 
 * TBD - Maybe use or inherit from SmisCreateBlockSnapshotJob
 * Issues
 * - Different completer.
 */
@SuppressWarnings("serial")
public class SmisBlockLinkSnapshotSessionTargetJob extends SmisSnapShotJob {

    // The unique job name.
    private static final String JOB_NAME = "SmisBlockLinkSnapshotSessionTargetJob";

    // The copy mode in which the target is linked to the snapshot.
    @SuppressWarnings("unused")
    private final String _copyMode;

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockLinkSnapshotSessionTargetJob.class);

    /**
     * Constructor.
     * 
     * @param cimJob The CIM object path of the underlying CIM Job.
     * @param systemURI The URI of the storage system.
     * @param copyMode The copy mode in which the target is linked to the snapshot.
     * @param taskCompleter A reference to the task completer.
     */
    public SmisBlockLinkSnapshotSessionTargetJob(CIMObjectPath cimJob, URI systemURI, String copyMode,
            TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
        _copyMode = copyMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        CloseableIterator<CIMObjectPath> targetVolumeIter = null;
        try {
            DbClient dbClient = jobContext.getDbClient();
            TaskCompleter completer = getTaskCompleter();
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, completer.getId());
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                s_logger.info("Post-processing successful link snapshot session target {} for task {}", snapshot.getId(),
                        completer.getOpId());
                // Get the snapshot device ID and set it against the BlockSnapshot object
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                targetVolumeIter = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                if (targetVolumeIter.hasNext()) {
                    // Get the sync volume native device id
                    CIMObjectPath targetVolumePath = targetVolumeIter.next();
                    CIMInstance targetVolume = client.getInstance(targetVolumePath, false, false, null);
                    String targetVolumeDeviceId = targetVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    String targetVolumeElementName = CIMPropertyFactory.getPropertyValue(targetVolume, SmisConstants.CP_ELEMENT_NAME);
                    String targetVolumeWWN = CIMPropertyFactory.getPropertyValue(targetVolume, SmisConstants.CP_WWN_NAME);
                    String targetVolumeAltName = CIMPropertyFactory.getPropertyValue(targetVolume, SmisConstants.CP_NAME);
                    BlockObject sourceObj = BlockObject.fetch(dbClient, snapshot.getParent().getURI());
                    StorageSystem system = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                    snapshot.setNativeId(targetVolumeDeviceId);
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(system, snapshot));
                    snapshot.setDeviceLabel(targetVolumeElementName);
                    snapshot.setInactive(false);
                    snapshot.setIsSyncActive(Boolean.TRUE);
                    snapshot.setCreationTime(Calendar.getInstance());
                    snapshot.setWWN(targetVolumeWWN.toUpperCase());
                    snapshot.setAlternateName(targetVolumeAltName);
                    commonSnapshotUpdate(snapshot, targetVolume, client, system, sourceObj.getNativeId(), targetVolumeDeviceId);
                    s_logger.info(String
                            .format("For target volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s (%4$s). Associated volume is %5$s (%6$s)",
                                    targetVolumePath.toString(), snapshot.getId().toString(), targetVolumeDeviceId,
                                    targetVolumeElementName,
                                    sourceObj.getNativeId(), sourceObj.getDeviceLabel()));
                    dbClient.persistObject(snapshot);
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to link snapshot session target {} for task {}", snapshot.getId(), completer.getOpId());
                snapshot.setInactive(true);
                dbClient.persistObject(snapshot);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in link snapshot session target job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in link snapshot session target job status processing", e);
        } finally {
            if (targetVolumeIter != null) {
                targetVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
