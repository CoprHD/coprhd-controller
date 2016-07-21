/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;

/**
 * Abstract super-class for validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractValidator implements Validator {

    private AbstractValidatorFactory factory;
    private ValidatorLogger logger;

    public void setFactory(AbstractValidatorFactory factory) {
        this.factory = factory;
    }

    public AbstractValidatorFactory getFactory() {
        return factory;
    }

    public ValidatorLogger getLogger() {
        return logger;
    }

    public void setLogger(ValidatorLogger logger) {
        this.logger = logger;
    }

    /*
     * Convenience delegation methods for external dependencies.
     */
    public DbClient getDbClient() {
        return factory.getDbClient();
    }

    public CoordinatorClient getCoordinator() {
        return factory.getCoordinator();
    }
}
