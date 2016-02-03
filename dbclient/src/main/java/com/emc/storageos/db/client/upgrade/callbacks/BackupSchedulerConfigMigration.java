package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class BackupSchedulerConfigMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(BackupSchedulerConfigMigration.class);
    private static final String BACKUP_SCHEDULER_CONFIG = "backupschedulerconfig";
    private static final String GLOBAL_ID = "global";

    @Override
    public void process() throws MigrationCallbackException {
        try {
            Configuration config = coordinatorClient.queryConfiguration(BACKUP_SCHEDULER_CONFIG, GLOBAL_ID);
            if (config == null) {
                log.info("Backup scheduler config doesn't exist, no need to do migration");
                return;
            }
            coordinatorClient.persistServiceConfiguration(coordinatorClient.getSiteId(), config);
            log.info("Backup scheduler has been migrated to site specific area");
        } catch (Exception e) {
            log.error("Fail to migrate backup scheduler config to site specific area", e);
        }
    }

}
