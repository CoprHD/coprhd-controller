/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.MigrationTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class ChangeBlockVolumeVirtualPool extends WaitForTasks<VolumeRestRep> {
    private List<URI> volumeIds;
    private URI targetVirtualPoolId;
    private URI consistencyGroup;
    private String migrationType;
    private URI migrationHost;

    public ChangeBlockVolumeVirtualPool(URI volumeId, URI targetVirtualPoolId, URI consistencyGroup,
            String migrationType, URI migrationHost) {
        this.volumeIds = Lists.newArrayList(volumeId);
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.consistencyGroup = consistencyGroup;
        this.migrationType = migrationType;
        this.migrationHost = migrationHost;
        provideDetailArgs(volumeId, targetVirtualPoolId, consistencyGroup, migrationType, migrationHost);
    }

    public ChangeBlockVolumeVirtualPool(List<URI> volumeIds, URI targetVirtualPoolId, URI consistencyGroup,
            String migrationType, URI migrationHost) {
        this.volumeIds = volumeIds;
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.consistencyGroup = consistencyGroup;
        this.migrationType = migrationType;
        this.migrationHost = migrationHost;
        provideDetailArgs(volumeIds, targetVirtualPoolId, consistencyGroup, migrationType, migrationHost);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeVirtualPoolChangeParam param = new VolumeVirtualPoolChangeParam();
        param.setVolumes(volumeIds);
        param.setVirtualPool(targetVirtualPoolId);
        if (!NullColumnValueGetter.isNullURI(consistencyGroup)) {
            param.setConsistencyGroup(consistencyGroup);
        }
        if (migrationType.equals(MigrationTypeEnum.HOST.toString())) {
            param.setIsHostMigration(true);
            param.setMigrationHost(migrationHost);
        } else if (migrationType.equals(MigrationTypeEnum.DRIVER.toString())) {
            param.setIsHostMigration(false);
        }
        return getClient().blockVolumes().changeVirtualPool(param);
    }
}
