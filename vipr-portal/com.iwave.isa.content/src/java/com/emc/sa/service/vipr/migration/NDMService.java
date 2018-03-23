/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.migration;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.migration.tasks.*;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.MigrationEnvironmentParam;
import com.emc.storageos.model.block.MigrationZoneCreateParam;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.emc.sa.service.ServiceParams.*;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;

@Service("NDM")
public class NDMService extends ViPRService {
    private static Logger log = LoggerFactory.getLogger(NDMService.class);

    @Param(value = STORAGE_TYPE)
    private String storageType;

    @Param(value = HOST)
    private String host;

    @Param(value = SOURCE_STORAGE_SYSTEM)
    private String sourceStorageSystem;

    @Param(value = TARGET_STORAGE_SYSTEMS)
    private String targetStorageSystems;

    @Param(value = STORAGE_GROUP)
    private String storageGroups;

    @Param(value = MAXIMUM_PATHS)
    private Integer maxPaths;

    @Param(value = TARGET_STORAGE_PORT)
    private String targetStoragePorts;

    @Override
    public void precheck() throws Exception {
    }

    @Override
    public void execute() throws Exception {
        // create env
        MigrationEnvironmentParam migrationEnvironmentParam = new MigrationEnvironmentParam(URI.create(sourceStorageSystem), URI.create(targetStorageSystems));
        Task<StorageSystemRestRep> createEnv = execute(new CreateMigrationEnvironment(migrationEnvironmentParam));
        addAffectedResource(createEnv);

        // create zone
        ExportPathParameters pathParam = new ExportPathParameters();
        List<URI> targetPortURIs = new ArrayList<>();
        for (String port: targetStoragePorts.split(",")) {
            targetPortURIs.add(URI.create(port));
        }
        pathParam.setStoragePorts(targetPortURIs);
        pathParam.setMaxPaths(maxPaths);
        MigrationZoneCreateParam migrationZoneCreateParam = new MigrationZoneCreateParam(URI.create(targetStorageSystems), URI.create(host), pathParam);
        Tasks<BlockConsistencyGroupRestRep> createZone = execute(new CreateZonesForMigration(URI.create(storageGroups), migrationZoneCreateParam));
        addAffectedResources(createZone);

        // create migration
        Task<BlockConsistencyGroupRestRep> createMigration = execute(new CreateMigration(targetStorageSystems, storageGroups));
        addAffectedResource(createMigration);

        // rescan host
        Tasks<HostRestRep> rescanHost = execute(new RescanHost(storageGroups));
        addAffectedResources(rescanHost);

        logInfo("Migration created. Go to StorageGroup Resource page to do cutover");

        // Don't do cutover in catalog service as it might take very long time.
    }
}
