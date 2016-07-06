package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.DUPreventionValidator;

/**
 * Abstract super-class for implementations of {@link DUPreventionValidator}.
 */
public abstract class AbstractVmaxDUPValidator implements DUPreventionValidator {

    private VmaxDUPValidationFactory factory;

    public void setFactory(VmaxDUPValidationFactory factory) {
        this.factory = factory;
    }

    /**
     * This method should contain the validation code.
     *
     * @return True, if the validation passed, false otherwise.
     * @throws Exception
     */
    protected abstract boolean execute() throws Exception;

    /**
     * Perform the validation.  If an Exception is raised, it will be caught here,
     * logged in a standard manner and rethrown for proper handling.
     *
     * @return True, if the validation passed, false otherwise.
     * @throws Exception
     */
    @Override
    public boolean validate() throws Exception {
        try {
            return execute();
        } catch (Exception e) {
            // TODO Generic logging for DU exceptions here.
            throw e;
        }
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
