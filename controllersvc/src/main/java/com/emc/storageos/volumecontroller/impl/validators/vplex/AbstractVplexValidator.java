package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

public class AbstractVplexValidator {
    protected DbClient dbClient;
    protected ValidatorLogger logger;

    public AbstractVplexValidator(DbClient dbClient, ValidatorLogger logger) {
        this.dbClient = dbClient;
        this.logger = logger;
    }

}
