/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.MetaVolumeOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.Job.JobStatus;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSCreateMetaVolumeMembersJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

public class HDSMetaVolumeOperations implements MetaVolumeOperations {

    private static Logger log = LoggerFactory.getLogger(HDSMetaVolumeOperations.class);

    private static final int SYNC_WRAPPER_WAIT = 5000;
    private static final int SYNC_WRAPPER_TIME_OUT = 1200000;
    private static final String VOLUME_FORMAT_TYPE = "noformat";
    private DbClient dbClient;

    private HDSApiFactory hdsApiFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    /**
     * Create meta volume member devices. These devices provide capacity to meta volume.
     * 
     * @param storageSystem
     * @param storagePool
     * @param metaHead
     * @param memberCount
     * @param memberCapacity
     * @param metaVolumeTaskCompleter
     * @return list of native ids of meta member devices
     * @throws Exception
     */
    @Override
    public List<String> createMetaVolumeMembers(StorageSystem storageSystem,
            StoragePool storagePool, Volume metaHead, int memberCount,
            long memberCapacity, MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception {
        log.info(String
                .format("Create Meta Volume Members Start - Array: %s, Pool: %s, %n Volume: %s, Count:%s, Member capacity: %s",
                        storageSystem.getSerialNumber(), storagePool.getNativeId(),
                        metaHead.getLabel(), memberCount, memberCapacity));
        try {
            String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);
            String poolObjectID = HDSUtils.getPoolObjectID(storagePool);

            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            // commenting this to rever the fix.
            // Integer ldevIdToUse = getLDEVNumberToCreateMetaMembers(hdsApiClient, systemObjectID);
            String asyncTaskMessageId = hdsApiClient.createThickVolumes(systemObjectID,
                    poolObjectID, memberCapacity, memberCount, "", VOLUME_FORMAT_TYPE, storageSystem.getModel(), null);
            HDSCreateMetaVolumeMembersJob metaVolumeMembersJob = new HDSCreateMetaVolumeMembersJob(
                    asyncTaskMessageId, storageSystem.getId(), metaHead, memberCount,
                    metaVolumeTaskCompleter);

            invokeMethodSynchronously(hdsApiFactory, asyncTaskMessageId, metaVolumeMembersJob);

            return metaVolumeMembersJob.getMetaMembers();
        } catch (Exception e) {
            log.error("Problem in createMetaVolumeMembers: ", e);
            ServiceError error = DeviceControllerErrors.hds.methodFailed(
                    "createMetaVolumeMemebers", e.getMessage());
            metaVolumeTaskCompleter.getVolumeTaskCompleter().error(dbClient, error);
            throw e;
        } finally {
            log.info(String
                    .format("Create Meta Volume Members End - Array: %s, Pool: %s, %n Volume: %s",
                            storageSystem.getSerialNumber(), storagePool.getNativeId(),
                            metaHead.getLabel()));
        }
    }

    /**
     * Utility finds the highest ldev Id which is used on HiCommand DM.
     * 
     * @param hdsApiClient
     * @param systemObjectID
     * @return
     * @throws Exception
     */
    private Integer getLDEVNumberToCreateMetaMembers(HDSApiClient hdsApiClient, String systemObjectID) throws Exception {
        List<LogicalUnit> allVolumes = hdsApiClient.getHDSApiVolumeManager().getAllLogicalUnits(systemObjectID);
        Integer highestLDEVId = 1;
        if (null != allVolumes && !allVolumes.isEmpty()) {
            List<Integer> allVolumeLDEVIds = new ArrayList<Integer>(Collections2.transform(allVolumes,
                    fctnLogicalUnitToVolumeIDs()));
            Collections.sort(allVolumeLDEVIds);
            highestLDEVId = allVolumeLDEVIds.get(allVolumeLDEVIds.size() - 1);
        }
        // Incrementing the highest
        return (highestLDEVId + 1);
    }

    public Function<LogicalUnit, Integer> fctnLogicalUnitToVolumeIDs() {
        return new Function<LogicalUnit, Integer>() {

            @Override
            public Integer apply(LogicalUnit logicalUnit) {
                return logicalUnit.getDevNum();
            }
        };
    }

    @Override
    public void createMetaVolumeHead(StorageSystem storageSystem,
            StoragePool storagePool, Volume metaHead, long capacity,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception {
        throw new DeviceControllerException("Unsupported operation");

    }

    @Override
    public void createMetaVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume metaHead, List<String> metaMembers, String metaType,
            VirtualPoolCapabilityValuesWrapper capabilities,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception {
        throw new DeviceControllerException("Unsupported operation");
    }

    @Override
    public void createMetaVolumes(StorageSystem storageSystem, StoragePool storagePool, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities, TaskCompleter taskCompleter) throws Exception {
        throw new DeviceControllerException("Unsupported operation");
    }

    /**
     * Meta volume expansion is similar to the way expand Volume as meta.
     * Hence we are calling expandVolumeAsMetaVolume inside this.
     */
    @Override
    public void expandMetaVolume(StorageSystem storageSystem, StoragePool storagePool,
            Volume metaHead, List<String> newMetaMembers, MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception {
        expandVolumeAsMetaVolume(storageSystem, storagePool, metaHead, newMetaMembers,
                null, metaVolumeTaskCompleter);
    }

    @Override
    public void expandVolumeAsMetaVolume(StorageSystem storageSystem,
            StoragePool storagePool, Volume metaHead, List<String> newMetaMembers,
            String metaType, MetaVolumeTaskCompleter metaVolumeTaskCompleter) throws Exception {

        HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
        String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);
        LogicalUnit metaHeadVolume = hdsApiClient.getLogicalUnitInfo(
                systemObjectID, HDSUtils.getLogicalUnitObjectId(metaHead.getNativeId(), storageSystem));
        String metaHeadLdevId = null;
        List<String> metaMembersLdevObjectIds = new ArrayList<String>();
        // Step 1: Get LDEV id's of the meta members and format them
        if (null != newMetaMembers && !newMetaMembers.isEmpty()) {
            for (String metaMember : newMetaMembers) {
                if (null != metaMember) {
                    String asyncTaskMessageId = hdsApiClient.formatLogicalUnit(systemObjectID, metaMember);
                    HDSJob formatLUJob = new HDSJob(asyncTaskMessageId, storageSystem.getId(),
                            metaVolumeTaskCompleter.getVolumeTaskCompleter(), "formatLogicalUnit");
                    invokeMethodSynchronously(hdsApiFactory, asyncTaskMessageId, formatLUJob);
                }
                LogicalUnit metaMemberVolume = hdsApiClient.getLogicalUnitInfo(
                        systemObjectID, metaMember);
                if (null != metaMemberVolume
                        && !metaMemberVolume.getLdevList().isEmpty()) {
                    for (LDEV ldev : metaMemberVolume.getLdevList()) {
                        // Format the logical unit. This is synchronous operation
                        // should wait it the operation completes.
                        metaMembersLdevObjectIds.add(ldev.getObjectID());
                    }
                }
            }

        }
        log.info("New Meta member LDEV ids: {}", metaMembersLdevObjectIds);
        // Step 2: Get LDEV id of the meta volume head.
        if (null != metaHeadVolume && null != metaHeadVolume.getLdevList()) {
            for (LDEV ldev : metaHeadVolume.getLdevList()) {
                // LUSE volumes operate at LDEV level, So, metaHead will
                // always be the least LDEV Id.
                // Since the volume is already created thru ViPR and it
                // will be the lowest LDEV Id as meta members
                // will be created during expansion of volume.
                if (getLDEVID(ldev.getObjectID()).equalsIgnoreCase(
                        metaHead.getNativeId())) {
                    metaHeadLdevId = ldev.getObjectID();
                    break;
                }
            }
        }

        // Step 3: Create LUSE Volume using metaHead LDEV & meta
        // members LDEV Ids.
        LogicalUnit logicalUnit = hdsApiClient.createLUSEVolume(systemObjectID,
                metaHeadLdevId, metaMembersLdevObjectIds);
        if (null != logicalUnit) {
            long capacityInBytes = Long.valueOf(logicalUnit.getCapacityInKB()) * 1024L;
            metaHead.setProvisionedCapacity(capacityInBytes);
            metaHead.setAllocatedCapacity(capacityInBytes);
            dbClient.persistObject(metaHead);
        }

    }

    /**
     * Makes a call to Hicommand DM and returns the response back to the caller. If the HDS call
     * is a asynchronous, it waits for the HDS job to complete before returning. This is done to
     * allow callers to make consecutive asynchronous calls without the need for a workflow.
     * <em>This function should be used for asynchronous HDS calls only and will throw an exception
     * if the call did not return a job path.</em>
     * 
     * @param hdsApiFactory: HDSApiFactory
     * @param asyncMessageId : Async task id.
     * @param job
     *            for handling special cases of intermediate and final hds job results. Null should
     *            be used when no special handling is needed.
     */
    public void invokeMethodSynchronously(HDSApiFactory hdsApiFactory,
            String asyncMessageId, HDSJob job) throws Exception {

        // if this is an async call, wait for the job to complete
        if (asyncMessageId != null) {
            try {
                waitForAsyncHDSJob(job.getStorageSystemURI(),
                        asyncMessageId, job, hdsApiFactory);
            } catch (Exception ex) {
                log.error("Exception occurred while waiting on async job {} to complete",
                        asyncMessageId);
                HDSException.exceptions
                        .asyncTaskFailed(ex.getMessage());
            }
        } else {
            HDSException.exceptions.asyncTaskFailedForMetaVolume(
                    job.getStorageSystemURI());
        }
    }

    /**
     * Waits the thread to till the operation completes.
     * 
     * @param storageDeviceURI
     * @param messageId
     * @param job
     * @param hdsApiFactory
     * @return
     * @throws HDSException
     */
    private JobStatus waitForAsyncHDSJob(URI storageDeviceURI, String messageId,
            HDSJob job, HDSApiFactory hdsApiFactory) throws HDSException {
        JobStatus status = JobStatus.IN_PROGRESS;
        if (job == null) {
            TaskCompleter taskCompleter = new TaskCompleter() {
                @Override
                public void ready(DbClient dbClient) throws DeviceControllerException {
                }

                @Override
                public void error(DbClient dbClient, ServiceCoded serviceCoded)
                        throws DeviceControllerException {
                }

                @Override
                protected void complete(DbClient dbClient, Operation.Status status,
                        ServiceCoded coded) throws DeviceControllerException {
                }
            };
            job = new HDSJob(messageId, storageDeviceURI, taskCompleter, "");
        } else {
            job.setHDSJob(messageId);
        }
        JobContext jobContext = new JobContext(dbClient, null, null, hdsApiFactory, null, null, null, null);
        long startTime = System.currentTimeMillis();
        while (true) {
            JobPollResult result = job.poll(jobContext, SYNC_WRAPPER_WAIT);
            if (result.getJobStatus().equals(JobStatus.IN_PROGRESS)
                    || result.getJobStatus().equals(JobStatus.ERROR)) {
                if (System.currentTimeMillis() - startTime > SYNC_WRAPPER_TIME_OUT) {
                    HDSException.exceptions
                            .asyncTaskFailedTimeout(System.currentTimeMillis() - startTime);
                } else {
                    try {
                        Thread.sleep(SYNC_WRAPPER_WAIT);
                    } catch (InterruptedException e) {
                        log.error("Thread waiting for hds job to complete was interrupted and "
                                + "will be resumed");
                    }
                }
            } else {
                status = result.getJobStatus();
                if (!status.equals(JobStatus.SUCCESS)) {
                    HDSException.exceptions
                            .asyncTaskFailedWithErrorResponseWithoutErrorCode(messageId,
                                    result.getErrorDescription());
                }
                break;
            }
        }
        return status;
    }

    /**
     * Return the LDEVID of the metaHead.
     * 
     * @param ldevObjectID
     * @return
     */
    private String getLDEVID(String ldevObjectID) {
        Iterable<String> splitter = Splitter.on(HDSConstants.DOT_OPERATOR).limit(4)
                .split(ldevObjectID);
        return Iterables.getLast(splitter);
    }

    @Override
    public String defineExpansionType(StorageSystem storageSystem, Volume volume,
            String metaVolumeType, MetaVolumeTaskCompleter metaVolumeTaskCompleter)
            throws Exception {
        throw new DeviceControllerException("Unsupported operation");
    }

    @Override
    public void deleteBCVHelperVolume(StorageSystem storageSystem, Volume volume) throws Exception {
        throw new DeviceControllerException("Unsupported operation");
    }
}
