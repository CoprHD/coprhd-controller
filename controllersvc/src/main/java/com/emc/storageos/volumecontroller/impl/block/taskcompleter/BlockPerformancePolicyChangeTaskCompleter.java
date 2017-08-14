/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Task completer invoked when changing the performance policy for one or more volumes.
 */
@SuppressWarnings("serial")
public class BlockPerformancePolicyChangeTaskCompleter extends VolumeWorkflowCompleter {

    // Used to restore old policy information in the event of an error.
    private Map<URI, URI> oldVolumeToPerfPolicyMap;

    // Reference to a logger.
    private static final Logger logger = LoggerFactory.getLogger(BlockPerformancePolicyChangeTaskCompleter.class);

    /**
     * 
     * @param volumeURIs
     * @param oldVolumeToPerfPolicyMap
     * @param taskId
     */
    public BlockPerformancePolicyChangeTaskCompleter(List<URI> volumeURIs,
            Map<URI, URI> oldVolumeToPerfPolicyMap, String taskId) {
        super(volumeURIs, taskId);
        this.oldVolumeToPerfPolicyMap = oldVolumeToPerfPolicyMap;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        List<URI> volumeURIs = getIds();
        List<Volume> volumesToUpdate = new ArrayList<Volume>();
        switch (status) {
            case error:
                logger.info("An error occurred during performance policy change, restoring old policy for volumes {}", volumeURIs);
                for (URI volumeURI : volumeURIs) {
                    // Restore the performance policy.
                    Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                    logger.info("Rolling back performance policy on volume {}({})", volumeURI, volume.getLabel());
                    URI oldPerfPolicyURI = oldVolumeToPerfPolicyMap.get(volumeURI);
                    if (oldPerfPolicyURI == null) {
                        oldPerfPolicyURI = NullColumnValueGetter.getNullURI();
                    }
                    volume.setPerformancePolicy(oldPerfPolicyURI);
                    
                    // Also restore the auto tiering policy set on the volume
                    // to the value specified in the old performance policy.
                    if (!NullColumnValueGetter.isNullURI(oldPerfPolicyURI)) {
                        URI oldATPURI = ControllerUtils.getAutoTieringPolicyURIFromPerfPolicy(oldPerfPolicyURI, volume, dbClient);
                        if (oldATPURI == null) {
                            oldATPURI = NullColumnValueGetter.getNullURI();
                        }
                        volume.setAutoTieringPolicyUri(oldATPURI);
                    } else {
                        // The volume may not have had a performance policy, but still 
                        // may have had an auto tiering policy from the volume's virtual
                        // pool.
                        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                        URI oldATPURI = ControllerUtils.getAutoTieringPolicyURIFromVirtualPool(vpool, storageSystem, dbClient) ;
                        if (!NullColumnValueGetter.isNullURI(oldATPURI)) {
                            volume.setAutoTieringPolicyUri(oldATPURI);
                        }
                    }
                    volumesToUpdate.add(volume);                    
                }
                dbClient.updateObject(volumesToUpdate);
                break;
            case ready:
                break;
            default:
                break;
        }
        
        // Record the event and audit log message.
        OperationTypeEnum opType = OperationTypeEnum.CHANGE_VOLUME_PERFORMANCE_POLICY;
        try {
            boolean opStatus = Operation.Status.ready == status ? true : false;
            String evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            for (Volume volume : volumesToUpdate) {
                recordBourneVolumeEvent(dbClient, volume.getId(), evType, status, evDesc);
                AuditBlockUtil.auditBlock(dbClient, opType, opStatus, AuditLogManager.AUDITOP_END, volume.getLabel());
            }
            
        } catch (Exception ex) {
            logger.error("Failed to record block volume operation {}: {}", opType.toString(), ex);
        }

        super.complete(dbClient, status, serviceCoded);
    }    
}
