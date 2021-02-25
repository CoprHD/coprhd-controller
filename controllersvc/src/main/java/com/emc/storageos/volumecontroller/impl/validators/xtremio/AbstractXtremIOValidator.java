/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

/**
 * Abstract super-class for XtremIO validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractXtremIOValidator implements Validator {

    public static final String NO_MATCH = "<no match>";
    private XtremioSystemValidatorFactory factory;
    private ValidatorLogger logger;

    protected final StorageSystem storage;
    protected final ExportMask exportMask;
    protected boolean errorOnMismatch = true;
    protected String id = null; // identifying string for ExportMask
    private ExceptionContext exceptionContext;

    public AbstractXtremIOValidator(StorageSystem storage, ExportMask exportMask) {
        this.storage = storage;
        this.exportMask = exportMask;
        id = String.format("%s (%s)(%s)", exportMask.getMaskName(), exportMask.getNativeId(), exportMask.getId().toString());
    }

    public void setFactory(XtremioSystemValidatorFactory factory) {
        this.factory = factory;
    }

    public ValidatorLogger getLogger() {
        return logger;
    }

    public void setLogger(ValidatorLogger logger) {
        this.logger = logger;
    }

    public DbClient getDbClient() {
        return factory.getDbClient();
    }

    public ValidatorConfig getConfig() {
        return factory.getConfig();
    }

    public XtremIOClientFactory getClientFactory() {
        return factory.getClientFactory();
    }

    public CoordinatorClient getCoordinatorClient() {
        return factory.getCoordinator();
    }

    public void setErrorOnMismatch(boolean errorOnMismatch) {
        this.errorOnMismatch = errorOnMismatch;
    }

    public void setExceptionContext(ExceptionContext exceptionContext) {
        this.exceptionContext = exceptionContext;
    }

    public void checkForErrors() {
        if (errorOnMismatch && getLogger().hasErrors() && shouldThrowException()) {
            throw DeviceControllerException.exceptions.validationError(
                    "Export Mask", getLogger().getMsgs().toString(), ValidatorLogger.CONTACT_EMC_SUPPORT);
        }
    }

    private boolean shouldThrowException() {
        return getConfig().isValidationEnabled() && (exceptionContext == null || exceptionContext.isAllowExceptions());
    }

}
