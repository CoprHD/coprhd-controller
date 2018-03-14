/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vplex;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToForDisplay;
import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.net.URISyntaxException;
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
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.validators.DefaultValidator;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * Factory for creating Vplex-specific validator instances.
 */
public class VplexSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VplexSystemValidatorFactory.class);
    private DbClient dbClient;
    private ValidatorConfig config;

    private final List<Volume> remediatedVolumes = Lists.newArrayList();

    /**
     * Sets the database client.
     * 
     * @param dbClient the database client
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Set the ValidatorConfig
     * @param config the validator config object
     */
    public void setConfig(ValidatorConfig config) {
        this.config = config;
    }

    /**
     * Verify storage system connectivity
     * 
     * @param storageSystem the VPLEX storage system to check
     */
    private void checkVplexConnectivity(StorageSystem storageSystem) {
        try {
            VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), storageSystem, dbClient);
        } catch (URISyntaxException ex) {
            log.error("Couldn't connect to VPLEX: " + storageSystem.getLabel(), ex);
        } catch (Exception ex) {
            log.error("Couldn't connect to VPLEX: " + storageSystem.getLabel(), ex);
            throw ex;
        }
    }

    @Override
    public Validator exportMaskDelete(ExportMaskValidationContext ctx) {
        checkVplexConnectivity(ctx.getStorage());
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        VplexExportMaskValidator validator = new VplexExportMaskValidator(dbClient, config, logger, ctx.getStorage(),
                ctx.getExportMask());
        Collection<URI> volURIs = Collections2.transform(ctx.getBlockObjects(), CommonTransformerFunctions.fctnDataObjectToID());
        validator.setVolumesToValidate(volURIs);
        validator.setInitiatorsToValidate(ctx.getInitiators());

        DefaultValidator defaultValidator = new DefaultValidator(validator, config, logger, "Export Mask");
        defaultValidator.setExceptionContext(ctx);
        return defaultValidator;
    }

    @Override
    public Validator removeVolumes(ExportMaskValidationContext ctx) {
        checkVplexConnectivity(ctx.getStorage());
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, ctx.getExportMask().getId());
        ValidatorLogger logger = new ValidatorLogger(log, exportMask.forDisplay(), ctx.getStorage().forDisplay());
        VplexExportMaskValidator validator = new VplexExportMaskValidator(dbClient, config, logger, ctx.getStorage(), exportMask);
        validator.setInitiatorsToValidate(ctx.getInitiators());

        DefaultValidator defaultValidator = new DefaultValidator(validator, config, logger, "Export Mask");
        defaultValidator.setExceptionContext(ctx);
        return defaultValidator;
    }

    @Override
    public Validator removeInitiators(ExportMaskValidationContext ctx) {
        checkVplexConnectivity(ctx.getStorage());
        ValidatorLogger logger = new ValidatorLogger(log, ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        VplexExportMaskValidator validator = new VplexExportMaskValidator(dbClient, config, logger, ctx.getStorage(),
                ctx.getExportMask());

        Collection<? extends BlockObject> blockObjects = ctx.getBlockObjects();
        Collection<URI> uris = Collections2.transform(blockObjects, CommonTransformerFunctions.fctnDataObjectToID());
        // FIXME setVolumesToValidate should accept Collection<Volume> (or <? extends BlockObject>)
        validator.setVolumesToValidate(uris);

        DefaultValidator defaultValidator = new DefaultValidator(validator, config, logger, "Export Mask");
        defaultValidator.setExceptionContext(ctx);
        return defaultValidator;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        return null;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        checkVplexConnectivity(storageSystem);
        try {
            // Generate a friendly volume list for volume validation
            Collection<String> volNames = transform(volumes, fctnDataObjectToForDisplay());
            ValidatorLogger logger = new ValidatorLogger(log, Joiner.on(",").join(volNames), storageSystem.forDisplay());
            VplexVolumeValidator vplexVolumeValidator = new VplexVolumeValidator(dbClient, config, logger);
            vplexVolumeValidator.validateVolumes(storageSystem, volumes, delete, remediate, checks);
            if (logger.hasErrors() && config.isValidationEnabled()) {
                throw DeviceControllerException.exceptions.validationVolumeError(logger.getValidatedObjectName(),
                        logger.getStorageSystemName(), logger.getMsgs().toString());
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating VPLEX: " + storageSystem.getId(), ex);
            throw ex;
        }
        return remediatedVolumes;
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
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator changePortGroupAddPaths(ExportMaskValidationContext ctx) {
        return null;
    }

    @Override
    public Validator ExportPathAdjustment(ExportMaskValidationContext ctx) {
        return null;
    }
}
