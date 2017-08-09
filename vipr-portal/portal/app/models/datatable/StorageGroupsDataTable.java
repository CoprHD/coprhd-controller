/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.vipr.client.ViPRCoreClient;

import util.datatable.DataTable;

public class StorageGroupsDataTable extends DataTable {

    public StorageGroupsDataTable() {
        setupTable();
    }

    private void setupTable() {
        addColumn("blockConsistencyGroup").setRenderFunction("render.storageGroup");
        addColumn("blockConsistencyGroupId").hidden();
        addColumn("sourceSystem");
        addColumn("targetSystem");
        addColumn("migrationStatus");
        addColumn("percentageDone");
        addColumn("id").hidden();
        addColumn("startTime").setRenderFunction("render.localDate").sortable();
        addColumn("endTime").setRenderFunction("render.localDate").sortable();
        sortAllExcept("startTime");
        setRowCallback("createRowLink");
    }

    public static class StorageGroup {
        public String name;
        public URI id;
        public String migrationStatus;
        public String targetSystem;
        public String sourceSystem;
        public Long startTime;
        public Long endTime;
        public String blockConsistencyGroup;
        public URI blockConsistencyGroupId;
        public String percentageDone;
        public URI migrationId;

        public StorageGroup(MigrationRestRep migration, ViPRCoreClient client) {
            load(migration, client);
        }

        private void load(MigrationRestRep migration, ViPRCoreClient client) {
            this.name = migration.getName();
            this.migrationStatus = migration.getStatus();
            this.targetSystem = client.storageSystems().get(migration.getSourceSystem()).getName();
            this.sourceSystem = client.storageSystems().get(migration.getTargetSystem()).getName();
            this.migrationId = migration.getId();

            if (!StringUtils.isEmpty(migration.getStartTime())) {
                this.startTime = Long.valueOf(migration.getStartTime());
            }

            if (!StringUtils.isEmpty(migration.getEndTime())) {
                this.endTime = Long.valueOf(migration.getEndTime());
            }

            this.blockConsistencyGroupId = migration.getConsistencyGroup().getId();
            this.blockConsistencyGroup = client.blockConsistencyGroups().get(migration.getConsistencyGroup()).getName();
            this.id = blockConsistencyGroupId;
            this.percentageDone = migration.getPercentageDone();
        }
    }
}