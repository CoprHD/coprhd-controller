/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;
import java.util.Calendar;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.ObjectLabel;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

public class HDSBlockCreateSnapshotJob extends HDSJob {
    private static final Logger log = LoggerFactory.getLogger(HDSBlockCreateSnapshotJob.class);
    // These atomic references are for use in the volume rename step in processVolume
    private static final AtomicReference<NameGenerator> _nameGeneratorRef = new AtomicReference<NameGenerator>();

    public HDSBlockCreateSnapshotJob(String messageId, URI storageSystem,
            TaskCompleter taskCompleter) {
        super(messageId, storageSystem, taskCompleter, "CreateBlockSnapshot");
        // Keep a reference to these singletons
        _nameGeneratorRef.compareAndSet(null,
                (NameGenerator) ControllerServiceImpl.getBean("defaultNameGenerator"));
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception
    {
        DbClient dbClient = jobContext.getDbClient();
        try {
            // Do nothing if the job is not completed yet
            if (_status == JobStatus.IN_PROGRESS)
            {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(
                    String.format("Updating status of job %s to %s", opId, _status.name()));
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient
                    (HDSUtils.getHDSServerManagementServerInfo(storageSystem), storageSystem.getSmisUserName(),
                            storageSystem.getSmisPassword());
            URI snapshotId = getTaskCompleter().getId(0);
            log.info("snapshotId :{}", snapshotId);
            if (_status == JobStatus.SUCCESS)
            {
                LogicalUnit logicalUnit = (LogicalUnit) _javaResult.getBean("virtualVolume");
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotId);
                snapshot.setNativeId(String.valueOf(logicalUnit.getDevNum()));
                snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storageSystem, snapshot));
                snapshot.setInactive(false);
                snapshot.setCreationTime(Calendar.getInstance());
                long capacityInBytes = Long.valueOf(logicalUnit.getCapacityInKB()) * 1024L;
                snapshot.setProvisionedCapacity(capacityInBytes);
                snapshot.setAllocatedCapacity(capacityInBytes);
                snapshot.setWWN(HDSUtils.generateHitachiWWN(logicalUnit.getObjectID(), String.valueOf(logicalUnit.getDevNum())));
                snapshot.setIsSyncActive(true);
                dbClient.persistObject(snapshot);
                changeSnapshotName(dbClient, hdsApiClient, snapshot);
                if (logMsgBuilder.length() != 0)
                {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Created Snapshot successfully .. NativeId: %s, URI: %s", snapshot.getNativeId(),
                        getTaskCompleter().getId()));
            }
            else if (_status == JobStatus.FAILED)
            {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create volume: %s", opId, getTaskCompleter().getId().toString()));
                Snapshot snapshot = dbClient.queryObject(Snapshot.class, snapshotId);
                if (snapshot != null) {
                    snapshot.setInactive(true);
                    dbClient.persistObject(snapshot);
                }
            }
            log.info(logMsgBuilder.toString());
        } catch (Exception e) {
            log.error("Caught an exception while trying to updateStatus for HDSBlockCreateSnapshotJob", e);
            setErrorStatus("Encountered an internal error during snapshot create job status processing : " + e.getMessage());
        } finally {
            _postProcessingStatus = JobStatus.SUCCESS;
            super.updateStatus(jobContext);
        }
    }

    /**
     * Method will modify the name of a given volume to a generate name.
     * 
     * @param dbClient [in] - Client instance for reading/writing from/to DB
     * @param client [in] - HDSApiClient used for reading/writing from/to HiCommand DM.
     * @param snapshotObj [in] - Volume object
     */
    private void changeSnapshotName(DbClient dbClient, HDSApiClient client, BlockSnapshot snapshotObj) {
        try {
            Volume source = dbClient.queryObject(Volume.class, snapshotObj.getParent());
            // Get the tenant name from the volume
            TenantOrg tenant = dbClient.queryObject(TenantOrg.class, source.getTenant().getURI());
            String tenantName = tenant.getLabel();
            // Generate the name, then modify the volume instance
            // that was successfully created
            if (_nameGeneratorRef.get() == null) {
                _nameGeneratorRef.compareAndSet(null,
                        (NameGenerator) ControllerServiceImpl.getBean("defaultNameGenerator"));
            }
            String generatedName = _nameGeneratorRef.get().generate(tenantName, snapshotObj.getLabel(),
                    snapshotObj.getId().toString(), '-', HDSConstants.MAX_VOLUME_NAME_LENGTH);
            log.info(String.format("Attempting to add snapshot label %s to %s", generatedName, snapshotObj.getNativeId()));
            StorageSystem system = dbClient.queryObject(StorageSystem.class, snapshotObj.getStorageController());
            String systemObjectId = HDSUtils.getSystemObjectID(system);
            LogicalUnit logicalUnit = client.getLogicalUnitInfo(systemObjectId,
                    HDSUtils.getLogicalUnitObjectId(snapshotObj.getNativeId(), system));
            if (null != logicalUnit && null != logicalUnit.getLdevList() && !logicalUnit.getLdevList().isEmpty()) {
                Iterator<LDEV> ldevItr = logicalUnit.getLdevList().iterator();
                if (ldevItr.hasNext()) {
                    LDEV ldev = ldevItr.next();
                    ObjectLabel objectLabel = client.addVolumeLabel(ldev.getObjectID(), generatedName);
                    snapshotObj.setDeviceLabel(objectLabel.getLabel());
                    dbClient.persistObject(snapshotObj);
                }
            } else {
                log.info("No LDEV's found on volume: {}", snapshotObj.getNativeId());
            }
            log.info(String.format("snapshot label has been added to snapshot %s", snapshotObj.getNativeId()));
        } catch (Exception e) {
            log.error("Encountered an error while trying to set the snapshot name", e);
        }
    }

}
