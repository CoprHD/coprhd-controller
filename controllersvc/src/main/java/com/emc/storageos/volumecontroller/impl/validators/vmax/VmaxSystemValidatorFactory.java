package com.emc.storageos.volumecontroller.impl.validators.vmax;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.ChainingValidator;
import com.emc.storageos.volumecontroller.impl.validators.DefaultValidator;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.google.common.collect.Lists;

/**
 * Factory class for creating Vmax-specific validators. The theme for each factory method is
 * to create a {@link ValidatorLogger} instance to share with any new {@link Validator}
 * instances. Each validator can use this logger to report validation failures.
 * {@link DefaultValidator} and {@link ChainingValidator} will throw an exception if the logger
 * holds any errors.
 */
public class VmaxSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VmaxSystemValidatorFactory.class);

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
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractVmaxValidator volumes = new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        AbstractVmaxValidator initiators = new InitiatorsValidator(storage, exportMask, initiatorList);
        configureValidators(sharedLogger, volumes, initiators);

        ChainingValidator chain = new ChainingValidator(sharedLogger, "Export Mask");
        chain.addValidator(volumes);
        chain.addValidator(initiators);
        return chain;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI,
            Collection<Initiator> initiators) {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);  // FIXME

        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractVmaxValidator validator = new InitiatorsValidator(storage, exportMask, initiators);
        configureValidators(sharedLogger, validator);

        return new DefaultValidator(validator, sharedLogger, "Export Mask");
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractVmaxValidator validator = new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        configureValidators(sharedLogger, validator);

        return new DefaultValidator(validator, sharedLogger, "Export Mask");
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractVmaxValidator identity = new ValidateVolumeIdentity(storage, volumes);
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, sharedLogger, "Volume");
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        return null;
    }

    @Override
    public Validator expandVolumes(StorageSystem storage, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractVmaxValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, sharedLogger, "Volume");
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractVmaxValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, sharedLogger, "Volume");
    }

    /**
     * Common configuration for VMAX validators to keep things DRY.
     *
     * @param logger
     * @param validators
     */
    private void configureValidators(ValidatorLogger logger, AbstractVmaxValidator... validators) {
        for (AbstractVmaxValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(logger);
        }
    }

    private ValidatorLogger createValidatorLogger() {
        return new ValidatorLogger(log);
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        // TODO Auto-generated method stub
        return null;
    }
}
