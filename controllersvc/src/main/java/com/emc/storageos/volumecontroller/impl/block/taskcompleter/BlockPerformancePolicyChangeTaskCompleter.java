/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Task completer invoked when changing the performance policy for one or more volumes.
 */
@SuppressWarnings("serial")
public class BlockPerformancePolicyChangeTaskCompleter extends VolumeWorkflowCompleter {
    
    private static final String EVENT_SERVICE_TYPE = "PERFORMANCEPOLICY";

    // Used to restore old policy information in the event of an error.
    private Map<URI, URI> oldVolumeToPerfPolicyMap;

    // Reference to a logger.
    private static final Logger logger = LoggerFactory.getLogger(BlockPerformancePolicyChangeTaskCompleter.class);

    /**
     * Constructor.
     * 
     * @param volumeURIs The URIs of the volumes whose performance policy is changed.
     * @param oldVolumeToPerfPolicyMap A map specifying the old policy for each volume.
     * @param taskId The using id for this change policy task.
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
                    if (!VPlexUtil.isVplexVolume(volume, dbClient)) {
                        URI oldPerfPolicyURI = oldVolumeToPerfPolicyMap.get(volumeURI);
                        restorePerformancePolicyForVolume(volume, oldPerfPolicyURI, true, dbClient);
                        volumesToUpdate.add(volume);
                    } else {
                        // See if we need to restore the primary side of the volume. We know we
                        // do if the old policy map contains an entry for the primary backend volume.
                        // Note that we cannot just get the value from the map and check for null. 
                        // Originally, the volume may not have had a performance policy in which case
                        // the value in the map would be null. However, if the map contains an entry
                        // we did try to assign it a policy in the change performance policy request,
                        // and the values were updated in the volume to reflect that new policy so we
                        // have to restore it back to no policy.
                        Volume primaryBackendVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient, false);
                        if (primaryBackendVolume != null &&  oldVolumeToPerfPolicyMap.containsKey(primaryBackendVolume.getId())) {
                            URI oldPerfPolicyURI = oldVolumeToPerfPolicyMap.get(primaryBackendVolume.getId());
                            restorePerformancePolicyForVolume(primaryBackendVolume, oldPerfPolicyURI, true, dbClient);
                            volumesToUpdate.add(primaryBackendVolume);
                            
                            // If we tried to change the performance policy of the primary
                            // side of the volume, then we need to restore the performance
                            // policy of the VPLEX volume itself. It will have had the same
                            // old performance policy as the primary backend volume. Note
                            // that we do not reflect the auto tiering policy in the VPLEX,
                            // so no need to restore that in the VPLEX volume itself.
                            restorePerformancePolicyForVolume(volume, oldPerfPolicyURI, false, dbClient);
                            volumesToUpdate.add(volume);
                        }
                        
                        // Now lets see if we need to restore the HA side of the VPLEX volume if
                        // the request included a change to the HA side of the VPLEX volume. Again,
                        // we must check if the map contains and entry for the HA backend volume, rather
                        // than just getting the value and checking for null, as described above.
                        Volume haBackendVolume = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient, false);
                        if (haBackendVolume != null &&  oldVolumeToPerfPolicyMap.containsKey(haBackendVolume.getId())) {
                            URI oldPerfPolicyURI = oldVolumeToPerfPolicyMap.get(haBackendVolume.getId());
                            restorePerformancePolicyForVolume(haBackendVolume, oldPerfPolicyURI, true, dbClient);
                            volumesToUpdate.add(haBackendVolume);
                        }
                    }
                }
                dbClient.updateObject(volumesToUpdate);
                break;
            case ready:
                break;
            default:
                break;
        }
        
        // Record the event and audit log message.
        boolean isCGOperation = !CollectionUtils.isEmpty(getConsistencyGroupIds());
        OperationTypeEnum opType = OperationTypeEnum.CHANGE_VOLUME_PERFORMANCE_POLICY;
        if (isCGOperation) {
            opType = OperationTypeEnum.CHANGE_CG_PERFORMANCE_POLICY;
        }
        try {
            boolean opStatus = Operation.Status.ready == status ? true : false;
            String evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            for (URI volumeURI : volumeURIs) {
                recordBourneVolumeEvent(dbClient, volumeURI, evType, status, evDesc);
                if (!isCGOperation) {
                    Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                    AuditBlockUtil.auditBlock(dbClient, EVENT_SERVICE_TYPE, opType, 
                            opStatus, AuditLogManager.AUDITOP_END, volume.getLabel());
                }
            }
            
            // If a CG operation the audit log message is on the CG.
            if (isCGOperation) {
                // Note that there will only be one.
                Set<URI> cgURIs = getConsistencyGroupIds();
                for (URI cgURI : cgURIs) {
                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
                    AuditBlockUtil.auditBlock(dbClient, EVENT_SERVICE_TYPE, opType,
                            opStatus, AuditLogManager.AUDITOP_END, cg.getLabel()); 
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to record block volume operation {}: {}", opType.toString(), ex);
        }
        
        // Update the consistency group task, if any.
        updateConsistencyGroupTasks(dbClient, status, serviceCoded);

        // Call super to update the volume tasks and notify the workflow of completion.
        super.complete(dbClient, status, serviceCoded);
    }

    /**
     * 
     * @param volume
     * @param oldPerfPolicyURI
     * @param restoreAutoTieringPolicy
     * @param dbClient
     */
    private void restorePerformancePolicyForVolume(Volume volume, URI oldPerfPolicyURI, boolean restoreAutoTieringPolicy, DbClient dbClient) {
        if (oldPerfPolicyURI == null) {
            oldPerfPolicyURI = NullColumnValueGetter.getNullURI();
        }
        volume.setPerformancePolicy(oldPerfPolicyURI);
        
        // Also restore the auto tiering policy set on the volume
        // to the value specified in the old performance policy
        // unless the flag indicates otherwise. This would be the
        // case for a VPLEX volume as we never reflect the ATP
        // in the VPLEX volume.
        if (restoreAutoTieringPolicy) {
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
        }
    }
}
