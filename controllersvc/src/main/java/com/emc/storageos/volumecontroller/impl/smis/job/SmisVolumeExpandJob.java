/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MetaVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.workflow.WorkflowService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Job for volumeExpand operation.
 * 
 */
public class SmisVolumeExpandJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisVolumeExpandJob.class);

    URI _storagePoolURI;
    private MetaVolumeTaskCompleter _metaVolumeTaskCompleter;

    public SmisVolumeExpandJob(CIMObjectPath cimJob, URI storageSystem, URI storagePool,
            MetaVolumeTaskCompleter metaVolumeTaskCompleter, String jobName) {
        super(cimJob, storageSystem, metaVolumeTaskCompleter.getVolumeTaskCompleter(), "VolumeExpand");

        _storagePoolURI = storagePool;
        _metaVolumeTaskCompleter = metaVolumeTaskCompleter;
    }

    /**
     * Called to update the job status when the volume expand job completes.
     * 
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> associatorIterator = null;
        CloseableIterator<CIMInstance> instanceIterator = null;
        JobStatus jobStatus = getJobStatus();

        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            DbClient dbClient = jobContext.getDbClient();
            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

            // If terminal state update storage pool capacity and remove reservation for volume capacity
            // from pool's reserved capacity map.
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                SmisUtils.updateStoragePoolCapacity(dbClient, client, _storagePoolURI);

                StoragePool pool = dbClient.queryObject(StoragePool.class, _storagePoolURI);
                StringMap reservationMap = pool.getReservedCapacityMap();
                URI volumeId = getTaskCompleter().getId();
                // remove from reservation map
                reservationMap.remove(volumeId.toString());
                dbClient.persistObject(pool);
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder =
                    new StringBuilder(String.format("Updating status of job %s to %s, task: %s", this.getJobName(), jobStatus.name(), opId));

            if (jobStatus == JobStatus.SUCCESS) {
                VolumeExpandCompleter taskCompleter = (VolumeExpandCompleter) getTaskCompleter();
                Volume volume = dbClient.queryObject(Volume.class,
                        taskCompleter.getId());
                // set requested capacity
                volume.setCapacity(taskCompleter.getSize());
                // set meta related properties
                volume.setTotalMetaMemberCapacity(taskCompleter.getTotalMetaMembersSize());
                volume.setMetaMemberCount(taskCompleter.getMetaMemberCount());
                volume.setMetaMemberSize(taskCompleter.getMetaMemberSize());
                volume.setIsComposite(taskCompleter.isComposite());
                volume.setCompositionType(taskCompleter.getMetaVolumeType());

                // set provisioned capacity
                associatorIterator = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                if (associatorIterator.hasNext()) {
                    CIMObjectPath volumePath = associatorIterator.next();
                    CIMInstance volumeInstance = client.getInstance(volumePath, true, false, null);
                    if (volumeInstance != null) {
                        CIMProperty consumableBlocks = volumeInstance.getProperty(SmisConstants.CP_CONSUMABLE_BLOCKS);
                        CIMProperty blockSize = volumeInstance.getProperty(SmisConstants.CP_BLOCK_SIZE);
                        // calculate provisionedCapacity = consumableBlocks * block size
                        Long provisionedCapacity =
                                Long.valueOf(consumableBlocks.getValue().toString()) * Long.valueOf(blockSize.getValue().toString());
                        volume.setProvisionedCapacity(provisionedCapacity);
                    }

                    // set allocated capacity
                    instanceIterator = client.referenceInstances(volumePath, SmisConstants.CIM_ALLOCATED_FROM_STORAGEPOOL, null,
                            false, SmisConstants.PS_SPACE_CONSUMED);
                    if (instanceIterator.hasNext()) {
                        CIMInstance allocatedFromStoragePoolPath = instanceIterator.next();
                        CIMProperty spaceConsumed = allocatedFromStoragePoolPath.getProperty(SmisConstants.CP_SPACE_CONSUMED);
                        if (null != spaceConsumed) {
                            volume.setAllocatedCapacity(Long.valueOf(spaceConsumed.getValue().toString()));
                        }
                    }
                }
                logMsgBuilder.append(String.format("%n   Capacity: %s, Provisioned capacity: %s, Allocated Capacity: %s",
                        volume.getCapacity(), volume.getProvisionedCapacity(),
                        volume.getAllocatedCapacity()));
                if (volume.getIsComposite()) {
                    logMsgBuilder.append(String.format(
                            "%n   Is Meta: %s, Total meta member capacity: %s, Meta member count %s, Meta member size: %s",
                            volume.getIsComposite(), volume.getTotalMetaMemberCapacity(), volume.getMetaMemberCount(),
                            volume.getMetaMemberSize()));
                }

                _log.info(logMsgBuilder.toString());

                // Reset list of meta member volumes in the volume
                if (volume.getMetaVolumeMembers() != null) {
                    volume.getMetaVolumeMembers().clear();
                }

                StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                // set the RP tag on the volume if the volume is RP protected
                if (volume.checkForRp()) {
                    SmisCommandHelper helper = jobContext.getSmisCommandHelper();                                        
                    boolean tagSet = helper.doApplyRecoverPointTag(storageSystem, volume, true);
                	if (!tagSet) {
                		_log.error("Encountered an error while trying to enable the RecoverPoint tag.");
                        jobStatus = JobStatus.FAILED;
                	}                                        
                }

                dbClient.persistObject(volume);
                // Reset list of meta members native ids in WF data (when meta is created meta members are removed from array)
                WorkflowService.getInstance().storeStepData(opId, new ArrayList<String>());
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for SmisVolumeExpandJob", e);
            setPostProcessingErrorStatus("Encountered an internal error during volume expand job status processing : " + e.getMessage());
        } finally {
            _metaVolumeTaskCompleter.setLastStepStatus(jobStatus);
            if (associatorIterator != null) {
                associatorIterator.close();
            }
            if (instanceIterator != null) {
                instanceIterator.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
