/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.google.common.collect.Sets;

public class CreateMobilityGroup extends ViPRExecutionTask<VolumeGroupRestRep> {
    private final String name;
    private final String description;
    private final String migrationGroupBy;
    private final String migrationType;

    public CreateMobilityGroup(String name, String description, String migrationType, String migrationGroupBy) {
        this.name = name;
        this.description = description;
        this.migrationType = migrationType;
        this.migrationGroupBy = migrationGroupBy;
        provideDetailArgs(name);
    }

    @Override
    public VolumeGroupRestRep executeTask() throws Exception {
        VolumeGroupCreateParam input = new VolumeGroupCreateParam();
        input.setRoles(Sets.newHashSet(VolumeGroup.VolumeGroupRole.MOBILITY.name()));
        if (description != null) {
            input.setDescription(description);
        }
        input.setMigrationGroupBy(migrationGroupBy);
        input.setMigrationType(migrationType);
        input.setName(name);

        return getClient().application().createApplication(input);
    }
}