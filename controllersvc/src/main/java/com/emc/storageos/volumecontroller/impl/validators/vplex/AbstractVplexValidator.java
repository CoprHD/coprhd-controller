/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

public abstract class AbstractVplexValidator {
    private DbClient dbClient;
    private ValidatorConfig config;
    private ValidatorLogger logger;

    public AbstractVplexValidator(DbClient dbClient, ValidatorConfig config, ValidatorLogger logger) {
        this.dbClient = dbClient;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Returns the database client.
     * 
     * @return the database client
     */
    public DbClient getDbClient() {
        return dbClient;
    }

    /**
     * Returns the ValidatorConfig.
     * 
     * @return the validator config object
     */
    public ValidatorConfig getValidatorConfig() {
        return config;
    }

    /**
     * Returns the ValidatorLogger.
     * 
     * @return the validator logger object
     */
    public ValidatorLogger getValidatorLogger() {
        return logger;
    }

}
