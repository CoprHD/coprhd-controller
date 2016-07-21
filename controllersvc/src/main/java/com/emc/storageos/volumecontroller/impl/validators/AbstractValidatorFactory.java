package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;

/**
 * Top-level factory class for building {@link Validator} instances.
 */
public abstract class AbstractValidatorFactory implements StorageSystemValidatorFactory {

    private DbClient dbClient;
    private CoordinatorClient coordinator;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }
}
