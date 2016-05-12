/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ChangeBlockVolumeVirtualArray;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("ChangeVolumeVirtualArray")
public class ChangeVolumeVirtualArrayService extends ViPRService {
    @Param(ServiceParams.PROJECT)
    private URI projectId;

    @Param(ServiceParams.VOLUMES)
    private List<String> volumeIds;

    @Param(ServiceParams.TARGET_VIRTUAL_ARRAY)
    private URI targetVirtualArray;

    @Param(ServiceParams.MIGRATION_TYPE)
    private String migrationType;

    @Param(ServiceParams.LINUX_HOST)
    private URI migrationHost;

    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> tasks = execute(new ChangeBlockVolumeVirtualArray(volumeIds, targetVirtualArray.toString(),
                migrationType, migrationHost.toString()));
        addAffectedResources(tasks);
    }

    public URI getProjectId() {
        return projectId;
    }

    public void setProjectId(URI projectId) {
        this.projectId = projectId;
    }

    public List<String> getVolumeIds() {
        return volumeIds;
    }

    public void setVolumeId(List<String> volumeIds) {
        this.volumeIds = volumeIds;
    }

    public URI getTargetVirtualArray() {
        return targetVirtualArray;
    }

    public void setTargetVirtualArray(URI targetVirtualArray) {
        this.targetVirtualArray = targetVirtualArray;
    }

    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
    }

    public URI getMigrationHost() {
        return migrationHost;
    }

    public void setMigrationHost(URI migrationHost) {
        this.migrationHost = migrationHost;
    }
}
