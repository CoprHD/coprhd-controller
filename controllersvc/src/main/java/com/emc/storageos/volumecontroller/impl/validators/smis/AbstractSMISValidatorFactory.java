/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
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
import com.emc.storageos.volumecontroller.impl.validators.smis.vmax.ValidateVolumeIdentity;
import com.google.common.collect.Lists;

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

    private CoordinatorClient coordinator;
    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
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
        configureValidators(sharedLogger, volumes, initiators);

        ChainingValidator chain = new ChainingValidator(sharedLogger, getCoordinator(), "Export Mask");
        chain.addValidator(volumes);
        chain.addValidator(initiators);
        return chain;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI,
            Collection<Initiator> initiators) {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);  // FIXME

        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator validator = createExportMaskInitiatorValidator(storage, exportMask, initiators);
        configureValidators(sharedLogger, validator);

        return new DefaultValidator(validator, coordinator, sharedLogger, "Export Mask");
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator validator = createExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        configureValidators(sharedLogger, validator);

        return new DefaultValidator(validator, coordinator, sharedLogger, "Export Mask");
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, volumes);
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, coordinator, sharedLogger, "Volume");
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

        return new DefaultValidator(identity, coordinator, sharedLogger, "Volume");
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger();
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, coordinator, sharedLogger, "Volume");
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
