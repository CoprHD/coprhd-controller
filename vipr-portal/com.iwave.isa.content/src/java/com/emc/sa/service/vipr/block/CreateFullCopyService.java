/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COUNT;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

@Service("CreateFullCopy")
public class CreateFullCopyService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected URI volumeId;

    @Param(NAME)
    protected String name;

    @Param(COUNT)
    protected Integer count;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            BlockStorageUtils.getVolume(volumeId);
        }
    }

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks;
        checkAndPurgeObsoleteCopies(volumeId.toString());
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            tasks = BlockStorageUtils.createFullCopy(volumeId, name, count);
            addAffectedResources(tasks);
        } else {
            tasks = ConsistencyUtils.createFullCopy(volumeId, name, count);
            addAffectedResources(tasks);
        }
        addRetainedReplicas(volumeId, tasks.getTasks());
        for (Task<? extends DataObjectRestRep> copy : tasks.getTasks()) {
            logInfo("create.full.copy.service", copy.getResource().getName(), copy.getResource().getId());
        }
    }
    
    /**
     * Check retention policy and delete obsolete full copies if necessary
     * 
     * @param volumeOrCgId - volume id or CG id 
     */
    private void checkAndPurgeObsoleteCopies(String volumeOrCgId) {
        if (!isRetentionRequired()) {
            return;
        }
        List<RetainedReplica> replicas = findObsoleteReplica(volumeOrCgId);
        for (RetainedReplica replica : replicas) {
            for (String obsoleteCopyId : replica.getAssociatedReplicaIds()) {
                BlockObjectRestRep obsoleteCopy = BlockStorageUtils.getVolume(uri(obsoleteCopyId));
                info("Delete full copy %s (%s) since it exceeds max number of copies allowed", obsoleteCopyId, obsoleteCopy.getName());
                
                if (ConsistencyUtils.isVolumeStorageType(storageType)) {
                    BlockStorageUtils.removeFullCopy(uri(obsoleteCopyId), VolumeDeleteTypeEnum.FULL);
                } else {
                    ConsistencyUtils.removeFullCopy(uri(volumeOrCgId), uri(obsoleteCopyId));
                }
            }
            getModelClient().delete(replica);
        }
    }
}
