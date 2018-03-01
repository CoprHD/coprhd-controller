/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.MigrationEnvironmentParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;

public class CreateMigrationEnvironment extends WaitForTask<StorageSystemRestRep> {

    private MigrationEnvironmentParam migrationEnvironmentParam;

    public CreateMigrationEnvironment(MigrationEnvironmentParam migrationEnvironmentParam) {
        this.migrationEnvironmentParam = migrationEnvironmentParam;
    }

    @Override
    protected Task<StorageSystemRestRep> doExecute() throws Exception {
        return getClient().blockMigrations().createMigrationEnvironment(migrationEnvironmentParam);
    }
}
