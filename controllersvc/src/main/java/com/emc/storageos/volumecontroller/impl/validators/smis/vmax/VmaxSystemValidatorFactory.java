/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.validators.ChainingValidator;
import com.emc.storageos.volumecontroller.impl.validators.DefaultValidator;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskInitiatorsValidator;
import com.emc.storageos.volumecontroller.impl.validators.smis.common.ExportMaskVolumesValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;

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
    public AbstractSMISValidator createExportMaskVolumesValidator(StorageSystem storage, ExportMask exportMask,
            Collection<URI> volumeURIList) {
        return new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
    }

    @Override
    public AbstractSMISValidator createExportMaskInitiatorValidator(StorageSystem storage, ExportMask exportMask,
            Collection<Initiator> initiatorList) {
        return new ExportMaskInitiatorsValidator(storage, exportMask, initiatorList);
    }

    @Override
    public AbstractSMISValidator createMultipleExportMasksForBlockObjectsValidator(StorageSystem storage,
                                                                                   ExportMask exportMask,
                                                                                   Collection<? extends BlockObject> blockObjects) {
        return new MultipleVmaxMaskForVolumesValidator(storage, exportMask, blockObjects);
    }

    @Override
    public AbstractSMISValidator createMultipleExportMasksForInitiatorsValidator(StorageSystem storage,
                                                                                 ExportMask exportMask,
                                                                                 Collection<Initiator> initiators) {
        return new MultipleVmaxMaskForInitiatorsValidator(storage, exportMask, initiators);
    }

    @Override
    public ValidatorLogger createValidatorLogger() {
        return new ValidatorLogger(log);
    }

}
