/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.hds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;

public class HDSExportMaskInitiatorsValidator extends AbstractHDSValidator {

    private static final Logger log = LoggerFactory.getLogger(HDSExportMaskInitiatorsValidator.class);

    public HDSExportMaskInitiatorsValidator(StorageSystem storage, ExportMask exportMask, HDSSystemValidatorFactory factory,
            ExceptionContext exceptionContext) {
        super(storage, exportMask, factory, exceptionContext);
    }

    @Override
    public boolean validate() throws Exception {
        return true;
    }

}
