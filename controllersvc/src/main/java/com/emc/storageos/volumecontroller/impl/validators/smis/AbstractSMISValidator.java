/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

/**
 * Abstract super-class for SMIS validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractSMISValidator implements Validator {

    private AbstractSMISValidatorFactory factory;
    private ValidatorLogger logger;
    private EMCRefreshSystemInvoker emcRefreshSystemInvoker;

    public void setFactory(AbstractSMISValidatorFactory factory) {
        this.factory = factory;
    }

    public ValidatorLogger getLogger() {
        return logger;
    }

    public void setLogger(ValidatorLogger logger) {
        this.logger = logger;
    }

    public EMCRefreshSystemInvoker getEmcRefreshSystemInvoker() {
        return emcRefreshSystemInvoker;
    }

    public void setEmcRefreshSystemInvoker(EMCRefreshSystemInvoker emcRefreshSystemInvoker) {
        this.emcRefreshSystemInvoker = emcRefreshSystemInvoker;
    }

    /*
     * Convenience delegation methods for external dependencies.
     */
    public CIMObjectPathFactory getCimPath() {
        return factory.getCimPath();
    }

    public DbClient getDbClient() {
        return factory.getDbClient();
    }

    public ValidatorConfig getConfig() {
        return factory.getConfig();
    }

    public SmisCommandHelper getHelper() {
        return factory.getHelper();
    }
}
