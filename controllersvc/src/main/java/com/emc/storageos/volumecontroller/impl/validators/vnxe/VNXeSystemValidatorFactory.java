/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

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
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;

/**
 * Factory class for creating VNXe specific validators for DU prevention.
 */
public class VNXeSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VNXeSystemValidatorFactory.class);

    private DbClient dbClient;
    private VNXeApiClientFactory clientFactory;
    private ValidatorConfig config;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public VNXeApiClientFactory getVnxeApiClientFactory() {
        return clientFactory;
    }

    public void setVnxeApiClientFactory(VNXeApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public ValidatorConfig getConfig() {
        return config;
    }

    public void setConfig(ValidatorConfig config) {
        this.config = config;
    }

    /**
     * Common configuration for VNXe validators.
     *
     * @param logger ValidatorLogger
     * @param validators List of AbstractVNXeValidator instances
     */
    private void configureValidators(ValidatorLogger logger, AbstractVNXeValidator... validators) {
        for (AbstractVNXeValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(logger);
        }
    }

    @Override
    public Validator exportMaskDelete(ExportMaskValidationContext ctx) {
        // removing initiators from mask will be ViPR DB only operation if there is unknown volume, hence no volume validation
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        VNXeExportMaskInitiatorsValidator initiatorValidator = new VNXeExportMaskInitiatorsValidator(ctx.getStorage(),
                ctx.getExportMask());
        initiatorValidator.setExceptionContext(ctx);
        configureValidators(logger, initiatorValidator);

        return initiatorValidator;
    }

    @Override
    public Validator removeVolumes(ExportMaskValidationContext ctx) {
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        VNXeExportMaskInitiatorsValidator validator = new VNXeExportMaskInitiatorsValidator(ctx.getStorage(),
                ctx.getExportMask());
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
        VNXeExportMaskVolumesValidator volumeValidator = new VNXeExportMaskVolumesValidator(ctx.getStorage(),
                ctx.getExportMask(), ctx.getBlockObjects());
        volumeValidator.setExceptionContext(ctx);
        configureValidators(logger, volumeValidator);
        return volumeValidator;
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        return null;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        return null;
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
