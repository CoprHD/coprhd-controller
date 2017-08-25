/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class ChangeBlockVolumeVirtualPool extends WaitForTasks<VolumeRestRep> {
    private List<URI> volumeIds;
    private URI targetVirtualPoolId;
    private URI consistencyGroup;
    private URI exportPathPolicy;
    private Boolean suspendOnMigration;
    private boolean forceFlag = false;

    public ChangeBlockVolumeVirtualPool(URI volumeId, URI targetVirtualPoolId, 
            URI consistencyGroup, URI exportPathPolicy, Boolean suspendOnMigration) {
        this.volumeIds = Lists.newArrayList(volumeId);
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.consistencyGroup = consistencyGroup;
        this.exportPathPolicy = exportPathPolicy;
        this.suspendOnMigration = suspendOnMigration;
        provideDetailArgs(volumeId, targetVirtualPoolId, consistencyGroup);
    }

    public ChangeBlockVolumeVirtualPool(List<URI> volumeIds, URI targetVirtualPoolId, 
            URI consistencyGroup, URI exportPathPolicy, Boolean suspendOnMigration) {
        this.volumeIds = volumeIds;
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.consistencyGroup = consistencyGroup;
        this.exportPathPolicy = exportPathPolicy;
        this.suspendOnMigration = suspendOnMigration;
        provideDetailArgs(volumeIds, targetVirtualPoolId, consistencyGroup);
    }
    
    public ChangeBlockVolumeVirtualPool(List<URI> volumeIds, URI targetVirtualPoolId, 
            URI consistencyGroup, Boolean suspendOnMigration, boolean forceFlag) {
        this.volumeIds = volumeIds;
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.consistencyGroup = consistencyGroup;
        this.suspendOnMigration = suspendOnMigration;
        this.forceFlag = forceFlag;
        provideDetailArgs(volumeIds, targetVirtualPoolId, consistencyGroup);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeVirtualPoolChangeParam input = new VolumeVirtualPoolChangeParam();
        input.setVolumes(volumeIds);
        input.setVirtualPool(targetVirtualPoolId);
        input.setForceFlag(forceFlag);
        if (!NullColumnValueGetter.isNullURI(consistencyGroup)) {
            input.setConsistencyGroup(consistencyGroup);
        }
        if (!NullColumnValueGetter.isNullURI(exportPathPolicy)) {
            input.setExportPathPolicy(exportPathPolicy);
        }
        input.setMigrationSuspendBeforeCommit(suspendOnMigration);
        input.setMigrationSuspendBeforeDeleteSource(suspendOnMigration);
        return getClient().blockVolumes().changeVirtualPool(input);
    }
}
