/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.MigrationTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualArrayChangeParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class ChangeBlockVolumeVirtualArray extends WaitForTasks<VolumeRestRep> {
    private List<URI> volumeIds;
    private URI targetVirtualArrayId;
    private String migrationType;
    private URI migrationHost;

    public ChangeBlockVolumeVirtualArray(List<String> volumeIds, String targetVirtualArrayId,
            String migrationType, String migrationHost) {
        this(uris(volumeIds), uri(targetVirtualArrayId), migrationType, uri(migrationHost));
    }

    public ChangeBlockVolumeVirtualArray(List<URI> volumeIds, URI targetVirtualArrayId,
            String migrationType, URI migrationHost) {
        this.volumeIds = volumeIds;
        this.targetVirtualArrayId = targetVirtualArrayId;
        this.migrationType = migrationType;
        this.migrationHost = migrationHost;
        provideDetailArgs(targetVirtualArrayId, getVolumesDisplayString(),
                migrationType, migrationHost);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeVirtualArrayChangeParam param = new VolumeVirtualArrayChangeParam();
        param.setVirtualArray(targetVirtualArrayId);
        param.setVolumes(volumeIds);
        if (migrationType.equals(MigrationTypeEnum.HOST.toString())) {
            param.setIsHostMigration(True);
           param.setMigrationHost(migrationHost);
        } else if (migrationType.equals(MigrationTypeEnum.DRIVER.toString())) {
            param.setIsHostMigration(False);
        }
        return getClient().blockVolumes().changeVirtualArrayForVolumes(param);
    }

    private String getVolumesDisplayString() {
        List<String> volumes = Lists.newArrayList();
        for (URI volumeId : volumeIds) {
            volumes.add(volumeId.toString());
        }
        return StringUtils.join(volumes, ",");
    }

}
