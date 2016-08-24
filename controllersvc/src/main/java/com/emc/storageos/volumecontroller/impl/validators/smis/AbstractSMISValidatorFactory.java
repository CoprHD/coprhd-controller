/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
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
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.smis.vmax.ValidateVolumeIdentity;
import com.google.common.collect.Lists;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Abstract factory class for creating SMI-S related validators. The sub-classes should create the {@link ValidatorLogger} and
 * {@link AbstractSMISValidator} instances.
 * The theme for each factory method is to use the {@link ValidatorLogger} instance to share with the {@link Validator}
 * instances. Each validator can use this logger to report validation failures.
 * {@link DefaultValidator} and {@link ChainingValidator} will throw an exception if the logger
 * holds any errors.
 *
 **/
public abstract class AbstractSMISValidatorFactory implements StorageSystemValidatorFactory {

    private ValidatorConfig config;
    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;

    public static final AbstractSMISValidator truthyValidator = new AbstractSMISValidator() {
        @Override
        public boolean validate() throws Exception {
            return true;
        }
    };

    public ValidatorConfig getConfig() {
        return config;
    }

    public void setConfig(ValidatorConfig config) {
        this.config = config;
    }

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

    /**
     *
     * @param storage
     * @param exportMask
     * @param volumeURIList
     * @return
     */
    public abstract AbstractSMISValidator createExportMaskVolumesValidator(StorageSystem storage, ExportMask exportMask,
            Collection<URI> volumeURIList);

    /**
     *
     * @param storage
     * @param exportMask
     * @param initiatorList
     * @return
     */
    public abstract AbstractSMISValidator createExportMaskInitiatorValidator(StorageSystem storage, ExportMask exportMask,
            Collection<Initiator> initiatorList);

    public AbstractSMISValidator createMultipleExportMasksForBlockObjectsValidator(StorageSystem storage, ExportMask exportMask,
                                                                             Collection<? extends BlockObject> blockObjects) {
        return truthyValidator;
    }

    public AbstractSMISValidator createMultipleExportMasksForInitiatorsValidator(StorageSystem storage, ExportMask exportMask,
                                                                                   Collection<Initiator> initiators) {
        return truthyValidator;
    }

    /**
     *
     * @return
     */
    public abstract ValidatorLogger createValidatorLogger();

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
            Collection<URI> volumeURIList,
            Collection<Initiator> initiatorList) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator volumes = createExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        AbstractSMISValidator initiators = createExportMaskInitiatorValidator(storage, exportMask, initiatorList);
        AbstractSMISValidator multiMaskBlockObjects =
                createMultipleExportMasksForBlockObjectsValidator(storage, exportMask, null);
        AbstractSMISValidator multiMaskInitiators =
                createMultipleExportMasksForInitiatorsValidator(storage, exportMask, null);

        configureValidators(sharedLogger, volumes, initiators, multiMaskBlockObjects, multiMaskInitiators);

        ChainingValidator chain = new ChainingValidator(sharedLogger, config, "Export Mask");
        chain.addValidator(volumes);
        chain.addValidator(initiators);
        chain.addValidator(multiMaskBlockObjects);
        chain.addValidator(multiMaskInitiators);

        return chain;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI,
            Collection<Initiator> initiators) {
        return removeVolumes(storage, exportMaskURI, initiators, null);
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators,
                                   Collection<? extends BlockObject> volumes) {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);  // FIXME

        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator initiatorValidator = createExportMaskInitiatorValidator(storage, exportMask, initiators);
        AbstractSMISValidator maskValidator = createMultipleExportMasksForBlockObjectsValidator(storage, exportMask, volumes);

        configureValidators(sharedLogger, initiatorValidator, maskValidator);

        ChainingValidator chain = new ChainingValidator(sharedLogger, config, "Export Mask");
        chain.addValidator(initiatorValidator);
        chain.addValidator(maskValidator);

        return chain;
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        return removeInitiators(storage, exportMask, volumeURIList, null);
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList,
                                      Collection<Initiator> initiators) {

        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator volValidator = createExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        AbstractSMISValidator initsValidator =
                createMultipleExportMasksForInitiatorsValidator(storage, exportMask, initiators);

        configureValidators(sharedLogger, volValidator, initsValidator);

        ChainingValidator chain = new ChainingValidator(sharedLogger, config, "Export Mask");
        chain.addValidator(volValidator);
        chain.addValidator(initsValidator);

        return chain;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, volumes);
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, config, sharedLogger, "Volume");
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        return null;
    }

    @Override
    public Validator expandVolumes(StorageSystem storage, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, config, sharedLogger, "Volume");
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, config, sharedLogger, "Volume");
    }

    /**
     * Common configuration for VMAX validators to keep things DRY.
     *
     * @param logger
     * @param validators
     */
    private void configureValidators(ValidatorLogger logger, AbstractSMISValidator... validators) {
        for (AbstractSMISValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(logger);
        }
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
