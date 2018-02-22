package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;

import java.net.URI;

public class MigrationCutover extends WaitForTask<BlockConsistencyGroupRestRep> {
    private URI storageGroup;

    public MigrationCutover(String storageGroup) {
        this.storageGroup = URI.create(storageGroup);
    }

    @Override
    protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().migrationCutover(storageGroup);
    }
}
