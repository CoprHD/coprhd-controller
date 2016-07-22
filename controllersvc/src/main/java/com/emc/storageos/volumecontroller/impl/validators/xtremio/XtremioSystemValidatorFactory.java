/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

/**
 * Factory class for creating XtremIO specific validators for DU prevention.
 */
public class XtremioSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(XtremioSystemValidatorFactory.class);

    private DbClient dbClient;
    private XtremIOClientFactory clientFactory;
    private ValidatorLogger logger;
    private CoordinatorClient coordinator;

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

    public XtremIOClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(XtremIOClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * Common configuration for XtremIO validators.
     *
     * @param logger
     * @param validators
     */
    private void configureValidators(ValidatorLogger logger, AbstractXtremIOValidator... validators) {
        for (AbstractXtremIOValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(logger);
        }
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
            Collection<URI> volumeURIList, Collection<Initiator> initiatorList) {
        logger = new ValidatorLogger(log);
        XtremIOExportMaskInitiatorsValidator initiatorsValidator = new XtremIOExportMaskInitiatorsValidator(storage, exportMask,
                initiatorList);
        XtremIOExportMaskVolumesValidator volumesValidator = new XtremIOExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        configureValidators(logger, initiatorsValidator, volumesValidator);

        XtremIOExportMaskValidator exportMaskvalidator = new XtremIOExportMaskValidator(initiatorsValidator, volumesValidator, logger);

        return exportMaskvalidator;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        logger = new ValidatorLogger(log);
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        XtremIOExportMaskInitiatorsValidator validator = new XtremIOExportMaskInitiatorsValidator(storage, exportMask, initiators);
        configureValidators(logger, validator);
        return validator;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        return null;
    }

    @Override
    public Validator expandVolumes(StorageSystem storageSystem, Volume volume) {
        return null;
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        return null;
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        logger = new ValidatorLogger(log);
        XtremIOExportMaskVolumesValidator validator = new XtremIOExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        configureValidators(logger, validator);
        return validator;
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        logger = new ValidatorLogger(log);
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        XtremIOExportMaskInitiatorsValidator validator = new XtremIOExportMaskInitiatorsValidator(storage, exportMask, initiators);
        configureValidators(logger, validator);
        validator.setErrorOnMismatch(false);
        return validator;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        logger = new ValidatorLogger(log);
        XtremIOExportMaskVolumesValidator validator = new XtremIOExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        configureValidators(logger, validator);
        validator.setErrorOnMismatch(false);
        return validator;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        return null;
    }
}
