/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;

public abstract class AbstractVplexValidator {
    private DbClient dbClient;
    private ValidatorConfig config;
    private ValidatorLogger logger;
    private ExceptionContext exceptionContext;

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
    
    public void setExceptionContext(ExceptionContext exceptionContext) {
        this.exceptionContext = exceptionContext;
    }

    
    public void checkForErrors() {
        if (getValidatorLogger().hasErrors() && shouldThrowException()) {
            throw DeviceControllerException.exceptions.validationError(
                    "Export Mask", getValidatorLogger().getMsgs().toString(), ValidatorLogger.CONTACT_EMC_SUPPORT);
        }
    }

    private boolean shouldThrowException() {
        return getValidatorConfig().isValidationEnabled() && (exceptionContext == null || exceptionContext.isAllowExceptions());
    }

}
