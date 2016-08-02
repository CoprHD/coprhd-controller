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
import com.emc.storageos.volumecontroller.impl.validators.DefaultValidator;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

/**
 * Abstract super-class for XtremIO validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractXtremIOValidator implements Validator {

    private XtremioSystemValidatorFactory factory;
    private ValidatorLogger logger;
    static final String NO_MATCH = "<no match>";

    final StorageSystem storage;
    final ExportMask exportMask;
    boolean errorOnMismatch = true;
    String id = null; // identifying string for ExportMask

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

    public CoordinatorClient getCoordinator() {
        return factory.getCoordinator();
    }

    public XtremIOClientFactory getClientFactory() {
        return factory.getClientFactory();
    }

    public void setErrorOnMismatch(boolean errorOnMismatch) {
        this.errorOnMismatch = errorOnMismatch;
    }

    public void checkForErrors() {
        if (getLogger().hasErrors() && errorOnMismatch) {
            if (DefaultValidator.validationEnabled(getCoordinator())) {
                throw DeviceControllerException.exceptions.validationError(
                        "Export Mask", getLogger().getMsgs().toString(), ValidatorLogger.CONTACT_EMC_SUPPORT);
            }
        }
    }

}
