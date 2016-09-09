/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vnx;

import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskInitiatorsValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskVolumesValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VnxSystemValidatorFactory extends AbstractSMISValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VnxSystemValidatorFactory.class);

    @Override
    public ValidatorLogger createValidatorLogger(String validatedObjectName, String storageSystemName) {
        return new ValidatorLogger(log, validatedObjectName, storageSystemName);
    }

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

}
