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
import com.emc.storageos.db.client.model.BlockSnapshotSession;
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
public class SmisBlockSnapshotSessionLinkTargetJob extends SmisSnapShotJob {

    // The unique job name.
    private static final String JOB_NAME = "SmisBlockSnapshotSessionLinkTargetJob";

    // The URI of the snapshot session to which the target is linked
    private final URI _snapSessionURI;

    // The copy mode in which the target is linked to the snapshot.
    @SuppressWarnings("unused")
    private final String _copyMode;

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockSnapshotSessionLinkTargetJob.class);

    /**
     * Constructor.
     * 
     * @param cimJob The CIM object path of the underlying CIM Job.
     * @param systemURI The URI of the storage system.
     * @param snapSessionURI The URI of the snapshot session to which the target is linked.
     * @param copyMode The copy mode in which the target is linked to the snapshot.
     * @param taskCompleter A reference to the task completer.
     */
    public SmisBlockSnapshotSessionLinkTargetJob(CIMObjectPath cimJob, URI systemURI, URI snapSessionURI, String copyMode,
            TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
        _snapSessionURI = snapSessionURI;
        _copyMode = copyMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        CloseableIterator<CIMObjectPath> volumeIter = null;
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
                // Get the snapshot session to which the target is being linked.
                BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, _snapSessionURI);

                // Get the snapshot device ID and set it against the BlockSnapshot object.
                BlockObject sourceObj = BlockObject.fetch(dbClient, snapshot.getParent().getURI());
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                volumeIter = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                while (volumeIter.hasNext()) {
                    // Get the sync volume native device id
                    CIMObjectPath volumePath = volumeIter.next();
                    s_logger.info("volumePath: {}", volumePath.toString());
                    CIMInstance volume = client.getInstance(volumePath, false, false, null);
                    String volumeDeviceId = volumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    s_logger.info("volumeDeviceId: {}", volumeDeviceId);
                    if (volumeDeviceId.equals(sourceObj.getNativeId())) {
                        // Don't want the source, we want the linked target.
                        continue;
                    }
                    String volumeElementName = CIMPropertyFactory.getPropertyValue(volume, SmisConstants.CP_ELEMENT_NAME);
                    s_logger.info("volumeElementName: {}", volumeElementName);
                    String volumeWWN = CIMPropertyFactory.getPropertyValue(volume, SmisConstants.CP_WWN_NAME);
                    s_logger.info("volumeWWN: {}", volumeWWN);
                    String volumeAltName = CIMPropertyFactory.getPropertyValue(volume, SmisConstants.CP_NAME);
                    s_logger.info("volumeAltName: {}", volumeAltName);
                    StorageSystem system = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                    snapshot.setNativeId(volumeDeviceId);
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(system, snapshot));
                    snapshot.setDeviceLabel(volumeElementName);
                    snapshot.setInactive(false);
                    snapshot.setIsSyncActive(Boolean.TRUE);
                    snapshot.setCreationTime(Calendar.getInstance());
                    snapshot.setWWN(volumeWWN.toUpperCase());
                    snapshot.setAlternateName(volumeAltName);
                    snapshot.setSettingsInstance(snapSession.getSessionInstance());
                    commonSnapshotUpdate(snapshot, volume, client, system, sourceObj.getNativeId(), volumeDeviceId, false, dbClient);
                    s_logger.info(String
                            .format("For target volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s (%4$s). Associated volume is %5$s (%6$s)",
                                    volumePath.toString(), snapshot.getId().toString(), volumeDeviceId,
                                    volumeElementName, sourceObj.getNativeId(), sourceObj.getDeviceLabel()));
                    dbClient.updateObject(snapshot);
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to link snapshot session target {} for task {}", snapshot.getId(), completer.getOpId());
                snapshot.setInactive(true);
                dbClient.updateObject(snapshot);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in link snapshot session target job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in link snapshot session target job status processing", e);
        } finally {
            if (volumeIter != null) {
                volumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
