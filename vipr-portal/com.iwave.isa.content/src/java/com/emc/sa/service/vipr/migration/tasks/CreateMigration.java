package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.vipr.client.Task;

import java.net.URI;

public class CreateMigration extends WaitForTask<BlockConsistencyGroupRestRep> {
    private URI targetStorageSystems;
    private URI storageGroup;

    public CreateMigration(String targetStorageSystems, String storageGroup) {
        this.targetStorageSystems = URI.create(targetStorageSystems);
        this.storageGroup = URI.create(storageGroup);
    }

    @Override
    protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        MigrationCreateParam migrationCreateParam = new MigrationCreateParam(targetStorageSystems);
        return getClient().blockConsistencyGroups().migrationCreate(storageGroup, migrationCreateParam);
    }
}
