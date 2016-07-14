package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ChainingValidator;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Factory class for creating Vmax-specific validators.
 */
public class VmaxSystemValidatorFactory implements StorageSystemValidatorFactory {

    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public CIMObjectPathFactory getCimPath() {
        return cimPath;
    }

    public void setCimPath(CIMObjectPathFactory cimPath) {
        this.cimPath = cimPath;
    }

    public SmisCommandHelper getHelper() {
        return helper;
    }

    public void setHelper(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
                                      Collection<URI> volumeURIList,
                                      Collection<Initiator> initiatorList) {
        AbstractVmaxValidator volumes = new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        AbstractVmaxValidator initiators = new InitiatorsValidator(storage, exportMask.getId(), initiatorList);

        configureValidators(volumes, initiators);

        ChainingValidator chain = new ChainingValidator();
        chain.addValidator(volumes);
        chain.addValidator(initiators);

        return chain;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI,
                                   Collection<Initiator> initiators) {
        AbstractVmaxValidator validator = new InitiatorsValidator(storage, exportMaskURI, initiators);
        configureValidators(validator);

        return validator;
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        AbstractVmaxValidator volumes = new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        configureValidators(volumes);

        return volumes;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
                                 ValCk[] checks) {
        return null;
    }

    /**
     * Common configuration for VMAX validators to keep things DRY.
     *
     * @param validators
     */
    private void configureValidators(AbstractVmaxValidator... validators) {
        for (AbstractVmaxValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(new ValidatorLogger());
        }
    }
}
