/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToForDisplay;
import static com.google.common.collect.Collections2.transform;

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
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.validators.ChainingValidator;
import com.emc.storageos.volumecontroller.impl.validators.DefaultValidator;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.impl.validators.smis.vmax.ValidateVolumeIdentity;
import com.google.common.base.Joiner;
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

    private static final Logger _log = LoggerFactory.getLogger(AbstractSMISValidatorFactory.class);

    private ValidatorConfig config;
    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;


    protected static final AbstractSMISValidator truthyValidator = new AbstractSMISValidator() {
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
     * Allow subclasses to provide a Validator for export mask volumes.
     *
     * @param ctx   ExportMaskValidationContext
     * @return      AbstractSMISValidator
     */
    public abstract AbstractSMISValidator createExportMaskVolumesValidator(ExportMaskValidationContext ctx);

    /**
     * Allow subclasses to provide a Validator for export mask initiators.
     *
     * @param ctx   ExportMaskValidationContext
     * @return      AbstractSMISValidator
     */
    public abstract AbstractSMISValidator createExportMaskInitiatorValidator(ExportMaskValidationContext ctx);

    /**
     * Default implementation returns a validator that always passes.
     *
     * @param ctx   ExportMaskValidationContext
     * @return      AbstractSMISValidator
     */
    public AbstractSMISValidator createMultipleExportMasksForBlockObjectsValidator(ExportMaskValidationContext ctx) {
        return truthyValidator;
    }

    /**
     * Default implementation returns a validator that always passes.
     *
     * @param ctx   ExportMaskValidationContext
     * @return      AbstractSMISValidator
     */
    public AbstractSMISValidator createMultipleExportMasksForInitiatorsValidator(ExportMaskValidationContext ctx) {
        return truthyValidator;
    }

    /**
     * Allow subclasses to provide a Validator for export mask port group.
     *
     * @param ctx ExportMaskValidationContext
     * @return AbstractSMISValidator
     */
    public abstract AbstractSMISValidator createExportMaskPortGroupValidator(ExportMaskValidationContext ctx);


    /**
     * Allow subclasses to return a {@link ValidatorLogger}
     *
     * @return  ValidatorLogger
     */
    public abstract ValidatorLogger createValidatorLogger(String validatedObjectName, String storageSystemName);

    @Override
    public Validator exportMaskDelete(ExportMaskValidationContext ctx) {
        ValidatorLogger sharedLogger = createValidatorLogger(ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        AbstractSMISValidator volumes = createExportMaskVolumesValidator(ctx);
        AbstractSMISValidator initiators = createExportMaskInitiatorValidator(ctx);
        AbstractSMISValidator multiMaskBlockObjects = createMultipleExportMasksForBlockObjectsValidator(ctx);

        configureValidators(sharedLogger, volumes, initiators, multiMaskBlockObjects);

        ChainingValidator chain = new ChainingValidator(sharedLogger, config, "Export Mask");
        chain.setExceptionContext(ctx);
        chain.addValidator(volumes);
        chain.addValidator(initiators);
        chain.addValidator(multiMaskBlockObjects);

        return chain;
    }

    @Override
    public Validator removeVolumes(ExportMaskValidationContext ctx) {
        ValidatorLogger sharedLogger = createValidatorLogger(ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());

        AbstractSMISValidator initiatorValidator = createExportMaskInitiatorValidator(ctx);
        AbstractSMISValidator maskValidator = createMultipleExportMasksForBlockObjectsValidator(ctx);

        configureValidators(sharedLogger, initiatorValidator, maskValidator);

        ChainingValidator chain = new ChainingValidator(sharedLogger, config, "Export Mask");
        chain.setExceptionContext(ctx);
        chain.addValidator(initiatorValidator);
        chain.addValidator(maskValidator);

        return chain;
    }

    @Override
    public Validator removeInitiators(ExportMaskValidationContext ctx) {
        ValidatorLogger sharedLogger = createValidatorLogger(ctx.getExportMask().forDisplay(), ctx.getStorage().forDisplay());
        AbstractSMISValidator volValidator = createExportMaskVolumesValidator(ctx);
        AbstractSMISValidator initsValidator = createMultipleExportMasksForInitiatorsValidator(ctx);

        configureValidators(sharedLogger, volValidator, initsValidator);

        ChainingValidator chain = new ChainingValidator(sharedLogger, config, "Export Mask");
        chain.setExceptionContext(ctx);
        chain.addValidator(volValidator);
        chain.addValidator(initsValidator);

        return chain;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        // Generate a friendly volume list for volume validation
        Collection<String> volNames = transform(volumes, fctnDataObjectToForDisplay());
        ValidatorLogger sharedLogger = createValidatorLogger(Joiner.on(",").join(volNames), storage.forDisplay());
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, volumes);
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, config, sharedLogger, ValidatorLogger.VOLUME_TYPE);
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        return null;
    }

    @Override
    public Validator expandVolumes(StorageSystem storage, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger(volume.forDisplay(), storage.forDisplay());
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, config, sharedLogger, ValidatorLogger.VOLUME_TYPE);
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        ValidatorLogger sharedLogger = createValidatorLogger(volume.forDisplay(), storage.forDisplay());
        AbstractSMISValidator identity = new ValidateVolumeIdentity(storage, Lists.newArrayList(volume));
        configureValidators(sharedLogger, identity);

        return new DefaultValidator(identity, config, sharedLogger, ValidatorLogger.VOLUME_TYPE);
    }

    /**
     * Common configuration for VMAX validators to keep things DRY.
     *
     * @param logger        ValidatorLogger
     * @param validators    List of AbstractSMISValidator instances
     */
    protected void configureValidators(ValidatorLogger logger, AbstractSMISValidator... validators) {
        EMCRefreshSystemInvoker emcRefreshSystem = new OneTimeEMCRefreshSystem(helper);

        for (AbstractSMISValidator validator : validators) {
            validator.setFactory(this);
            validator.setLogger(logger);
            validator.setEmcRefreshSystemInvoker(emcRefreshSystem);
        }
    }

    /**
     * Determines if it should perform initiator validation, given the export mask.
     * 
     * @param exportMask
     *            export mask
     * @return true if validation should be performed, false otherwise
     */
    protected boolean performInitiatorValidation(ExportMask exportMask) {
        // Don't validate against backing masks or RP if we're validating initiators.
        if (ExportMaskUtils.isBackendExportMask(getDbClient(), exportMask)) {
            _log.info("validation against backing mask for VPLEX or RP is disabled.");
            return false;
        }
        return true;
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
