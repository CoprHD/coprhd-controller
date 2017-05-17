/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.hds;

import org.slf4j.Logger;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;

public abstract class AbstractHDSValidator implements Validator {
    private HDSSystemValidatorFactory factory;
    private final StorageSystem storage;
    private final ExportMask exportMask;
    private ExceptionContext exceptionContext;
    protected boolean errorOnMismatch = true;
    protected String id = null; // identifying string for ExportMask
    private ValidatorLogger validatorLogger;

    public AbstractHDSValidator(Logger logger, StorageSystem storage, ExportMask exportMask, HDSSystemValidatorFactory factory,
            ExceptionContext exceptionContext) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.factory = factory;
        this.exceptionContext = exceptionContext;
        id = exportMask.forDisplay();
        validatorLogger = new ValidatorLogger(logger, exportMask.forDisplay(), storage.forDisplay());
    }

    /**
     * Returns the database client.
     * 
     * @return the database client
     */
    public DbClient getDbClient() {
        return factory.getDbClient();
    }

    /**
     * Returns the ValidatorConfig.
     * 
     * @return the validator config object
     */
    public ValidatorConfig getValidatorConfig() {
        return factory.getConfig();
    }

    /**
     * Returns the ValidatorLogger.
     * 
     * @return the validator logger object
     */
    public ValidatorLogger getValidatorLogger() {
        return validatorLogger;
    }



    public HDSApiFactory getClientFactory() {
        return factory.getClientFactory();
    }

    public HDSSystemValidatorFactory getFactory() {
        return factory;
    }

    public StorageSystem getStorage() {
        return storage;
    }

    public ExportMask getExportMask() {
        return exportMask;
    }

    public void checkForErrors() {
        if (errorOnMismatch && getValidatorLogger().hasErrors() && shouldThrowException()) {
            throw DeviceControllerException.exceptions.validationError(
                    "Export Mask", getValidatorLogger().getMsgs().toString(), ValidatorLogger.CONTACT_EMC_SUPPORT);
        }
    }

    private boolean shouldThrowException() {
        return getValidatorConfig().isValidationEnabled() && (exceptionContext == null || exceptionContext.isAllowExceptions());
    }

    public void setErrorOnMismatch(boolean errorOnMismatch) {
        this.errorOnMismatch = errorOnMismatch;
    }

}
