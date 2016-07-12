package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

/**
 * Abstract super-class for Vmax validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractVmaxValidator implements Validator {

    private VmaxSystemValidatorFactory factory;
    private ValidatorLogger logger;

    public void setFactory(VmaxSystemValidatorFactory factory) {
        this.factory = factory;
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

    public CIMObjectPathFactory getCimPath() {
        return factory.getCimPath();
    }

    public DbClient getDbClient() {
        return factory.getDbClient();
    }

    public SmisCommandHelper getHelper() {
        return factory.getHelper();
    }
}
