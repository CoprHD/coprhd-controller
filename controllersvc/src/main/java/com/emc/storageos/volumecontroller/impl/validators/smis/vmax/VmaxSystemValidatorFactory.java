/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.volumecontroller.impl.validators.ChainingValidator;
import com.emc.storageos.volumecontroller.impl.validators.DefaultValidator;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskInitiatorsValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskVolumesValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating Vmax-specific validators. The theme for each factory method is
 * to create a {@link ValidatorLogger} instance to share with any new {@link Validator}
 * instances. Each validator can use this logger to report validation failures.
 * {@link DefaultValidator} and {@link ChainingValidator} will throw an exception if the logger
 * holds any errors.
 */
public class VmaxSystemValidatorFactory extends AbstractSMISValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VmaxSystemValidatorFactory.class);

    @Override
    public AbstractSMISValidator createExportMaskVolumesValidator(ExportMaskValidationContext ctx) {
        return new ExportMaskVolumesValidator(ctx.getStorage(), ctx.getExportMask(), ctx.getBlockObjects());
    }

    @Override
    public AbstractSMISValidator createExportMaskInitiatorValidator(ExportMaskValidationContext ctx) {
        // Check to see if validation makes sense to perform for this export mask
        if (performInitiatorValidation(ctx.getExportMask())) {
            return new ExportMaskInitiatorsValidator(ctx.getStorage(), ctx.getExportMask(), ctx.getInitiators());
        }
        return truthyValidator;
    }

    @Override
    public AbstractSMISValidator createMultipleExportMasksForBlockObjectsValidator(ExportMaskValidationContext ctx) {
        return new MultipleVmaxMaskForVolumesValidator(ctx.getStorage(), ctx.getExportMask(), ctx.getBlockObjects());
    }

    @Override
    public AbstractSMISValidator createMultipleExportMasksForInitiatorsValidator(ExportMaskValidationContext ctx) {
        return new MultipleVmaxMaskForInitiatorsValidator(ctx.getStorage(), ctx.getExportMask(), ctx.getInitiators());
    }

    @Override
    public ValidatorLogger createValidatorLogger(String validatedObjectName, String storageSystemName) {
        return new ValidatorLogger(log, validatedObjectName, storageSystemName);
    }

}
