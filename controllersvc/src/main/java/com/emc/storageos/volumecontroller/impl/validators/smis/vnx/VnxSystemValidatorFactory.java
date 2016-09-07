/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vnx;

import java.net.URI;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskInitiatorsValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskVolumesValidator;

public class VnxSystemValidatorFactory extends AbstractSMISValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VnxSystemValidatorFactory.class);

    @Override
    public ValidatorLogger createValidatorLogger(String validatedObjectName, String storageSystemName) {
        return new ValidatorLogger(log, validatedObjectName, storageSystemName);
    }

    @Override
    public AbstractSMISValidator createExportMaskVolumesValidator(StorageSystem storage, ExportMask exportMask,
            Collection<URI> volumeURIList) {
        return new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
    }

    @Override
    public AbstractSMISValidator createExportMaskInitiatorValidator(StorageSystem storage, ExportMask exportMask,
            Collection<Initiator> initiatorList) {
        // Check to see if validation makes sense to perform for this export mask
        if (performInitiatorValidation(exportMask)) {
            return new ExportMaskInitiatorsValidator(storage, exportMask, initiatorList);
        }
        return truthyValidator;
    }

}
