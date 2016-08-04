/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

public class AbstractVplexValidator {
    protected DbClient dbClient;
    protected ValidatorConfig config;
    protected ValidatorLogger logger;

    public AbstractVplexValidator(DbClient dbClient, ValidatorConfig config, ValidatorLogger logger) {
        this.dbClient = dbClient;
        this.config = config;
        this.logger = logger;
    }
}
