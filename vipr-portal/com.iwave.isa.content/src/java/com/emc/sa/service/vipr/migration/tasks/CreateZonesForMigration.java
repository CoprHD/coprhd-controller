package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.MigrationZoneCreateParam;
import com.emc.vipr.client.Tasks;

import java.net.URI;

public class CreateZonesForMigration extends WaitForTasks<BlockConsistencyGroupRestRep> {
    private URI consistencyGroup;
    private MigrationZoneCreateParam migrationZoneCreateParam;

    public CreateZonesForMigration(URI consistencyGroup, MigrationZoneCreateParam migrationZoneCreateParam) {
        this.consistencyGroup = consistencyGroup;
        this.migrationZoneCreateParam = migrationZoneCreateParam;
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().createZonesForMigration(consistencyGroup, migrationZoneCreateParam);
    }
}
