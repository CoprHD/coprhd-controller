package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.AbstractValidator;
import com.emc.storageos.volumecontroller.impl.validators.AbstractValidatorFactory;

/**
 * Abstract super-class for Vmax validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractVmaxValidator extends AbstractValidator {

    public void setFactory(AbstractValidatorFactory factory) {
        super.setFactory(factory);
    }

    /*
     * Convenience delegation methods for external dependencies.
     */
    public CIMObjectPathFactory getCimPath() {
        return ((VmaxSystemValidatorFactory) getFactory()).getCimPath();
    }

    public SmisCommandHelper getHelper() {
        return ((VmaxSystemValidatorFactory) getFactory()).getHelper();
    }

    public DbClient getDbClient() {
        return getFactory().getDbClient();
    }

    public CoordinatorClient getCoordinatorClient() {
        return getFactory().getCoordinator();
    }

}
