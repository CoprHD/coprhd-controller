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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

/**
 * Factory class for creating XtremIO specific validators for DU prevention.
 */
public class XtremioSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(XtremioSystemValidatorFactory.class);

    private DbClient dbClient;
    private XtremIOClientFactory clientFactory;
    private ValidatorConfig config;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public ValidatorConfig getConfig() {
        return config;
    }

    public void setConfig(ValidatorConfig config) {
        this.config = config;
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
     * @param logger        ValidatorLogger
     * @param validators    List of AbstractXtremIOValidator instances
     */
    private void configureValidators(ValidatorLogger logger, AbstractXtremIOValidator... validators) {
        for (AbstractXtremIOValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(logger);
        }
    }

    /**
     * In XtremIO, delete export mask is usually removing the volumes from the IG.
     * We do not delete the initiators and the IG.
     * We delete the initiators only in case of cluster exports, where we have single IG consisting of all the cluster's hosts' initiators.
     * In this case, we will validate for extra volumes by using the appropriate validator.
     */
    @Override
    public Validator exportMaskDelete(ExportMaskValidationContext ctx) {
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        XtremIOExportMaskInitiatorsValidator validator = new XtremIOExportMaskInitiatorsValidator(ctx.getStorage(),
                ctx.getExportMask());
        validator.setExceptionContext(ctx);
        configureValidators(logger, validator);
        return validator;
    }

    @Override
    public Validator removeVolumes(ExportMaskValidationContext ctx) {
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        XtremIOExportMaskInitiatorsValidator validator = new XtremIOExportMaskInitiatorsValidator(ctx.getStorage(), ctx.getExportMask());
        validator.setExceptionContext(ctx);
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
    public Validator removeInitiators(ExportMaskValidationContext ctx) {
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        XtremIOExportMaskVolumesValidator validator = new XtremIOExportMaskVolumesValidator(ctx.getStorage(),
                ctx.getExportMask(), ctx.getBlockObjects());
        validator.setExceptionContext(ctx);
        configureValidators(logger, validator);
        return validator;
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        ValidatorLogger logger = new ValidatorLogger(log, exportMask.forDisplay(), storage.forDisplay());
        XtremIOExportMaskInitiatorsValidator validator = new XtremIOExportMaskInitiatorsValidator(storage, exportMask);
        configureValidators(logger, validator);
        validator.setErrorOnMismatch(false);
        return validator;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        ValidatorLogger logger = new ValidatorLogger(log, exportMask.forDisplay(), storage.forDisplay());
        List<? extends BlockObject> blockObjects = BlockObject.fetchAll(dbClient, volumeURIList);
        XtremIOExportMaskVolumesValidator validator = new XtremIOExportMaskVolumesValidator(storage, exportMask, blockObjects);
        configureValidators(logger, validator);
        validator.setErrorOnMismatch(false);
        return validator;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        return null;
    }

    @Override
    public Validator changePortGroupAddPaths(ExportMaskValidationContext ctx) {
        return null;
    }

    @Override
    public Validator exportPathAdjustment(ExportMaskValidationContext ctx) {
        return null;
    }
}
