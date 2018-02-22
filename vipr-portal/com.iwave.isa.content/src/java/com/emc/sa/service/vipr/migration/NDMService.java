package com.emc.sa.service.vipr.migration;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.migration.tasks.CreateMigration;
import com.emc.sa.service.vipr.migration.tasks.CreateMigrationEnvironment;
import com.emc.sa.service.vipr.migration.tasks.CreateZonesForMigration;
import com.emc.sa.service.vipr.migration.tasks.MigrationCutover;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.storageos.model.block.MigrationEnvironmentParam;
import com.emc.storageos.model.block.MigrationZoneCreateParam;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.emc.sa.service.ServiceParams.*;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;

@Service("NDM")
public class NDMService extends ViPRService {
    private static Logger log = LoggerFactory.getLogger(NDMService.class);

    @Param(value = STORAGE_TYPE)
    protected String storageType;

    @Param(value = HOST)
    protected String host;

    @Param(value = SOURCE_STORAGE_SYSTEM)
    protected String sourceStorageSystem;

    @Param(value = TARGET_STORAGE_SYSTEMS)
    protected String targetStorageSystems;

    @Param(value = STORAGE_GROUP)
    protected String storageGroup;

    @Param(value = MAXIMUM_PATHS)
    protected Integer maxPaths;

    @Param(value = TARGET_STORAGE_PORT)
    protected String targetStoragePorts;

    @Override
    public void precheck() throws Exception {
        log.info("======== precheck called");

    }

    @Override
    public void execute() throws Exception {
        log.info("======== execute called");
        log.info("parameters: {}, {}, {}, {}, {}, {}, {}", storageType, host, sourceStorageSystem, targetStorageSystems, storageGroup, maxPaths, targetStoragePorts);

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
        Tasks<BlockConsistencyGroupRestRep> createZone = execute(new CreateZonesForMigration(URI.create(storageGroup), migrationZoneCreateParam));
        addAffectedResources(createZone);

        // create migration
        Task<BlockConsistencyGroupRestRep> createMigration = execute(new CreateMigration(targetStorageSystems, storageGroup));
        addAffectedResource(createMigration);

        // cutover
        Task<BlockConsistencyGroupRestRep> migrationCutover = execute(new MigrationCutover(storageGroup));
        addAffectedResource(migrationCutover);

        //log.info("Migration done. Migration status: {}", migrationCutover.get().getMigrationStatus());
    }
}
