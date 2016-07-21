package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

public class AbstractVplexValidator {
    protected DbClient dbClient;
    protected CoordinatorClient coordinator;
    protected ValidatorLogger logger;

    public AbstractVplexValidator(DbClient dbClient, CoordinatorClient coordinator, ValidatorLogger logger) {
        this.dbClient = dbClient;
        this.coordinator = coordinator;
        this.logger = logger;
    }
}
