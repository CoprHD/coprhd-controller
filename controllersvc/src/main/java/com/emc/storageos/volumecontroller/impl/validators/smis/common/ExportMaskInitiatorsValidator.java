/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.common;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnInitiatorToPortName;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_SE_STORAGE_HARDWARE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_STORAGE_ID;

import java.util.Collection;
import java.util.Set;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

/**
 * This subclass of {@link AbstractExportMaskValidator} will:
 * 1) Query expected {@link Initiator} instances and transform them into their respective port names.
 * 2) Query SMI-S for SE_StorageHardwareID.StorageID instance properties associated with the given export mask
 * and normalize them.
 */
public class ExportMaskInitiatorsValidator extends AbstractExportMaskValidator {

    private final Collection<Initiator> expectedInitiators;

    public ExportMaskInitiatorsValidator(StorageSystem storage, ExportMask exportMask, Collection<Initiator> expectedInitiators) {
        super(storage, exportMask, FIELD_INITIATORS);
        this.expectedInitiators = expectedInitiators;
    }

    @Override
    protected String getAssociatorProperty() {
        return CP_STORAGE_ID;
    }

    @Override
    protected String getAssociatorClass() {
        return CP_SE_STORAGE_HARDWARE_ID;
    }

    @Override
    protected Function<? super String, String> getHardwareTransformer() {

        return new Function<String, String>() {

            @Override
            public String apply(String input) {
                return Initiator.normalizePort(input);
            }
        };
    }

    @Override
    protected Set<String> getDatabaseResources() {
        if (expectedInitiators == null || expectedInitiators.isEmpty()) {
            return Sets.newHashSet();
        }
        Collection<String> transformed = Collections2.transform(expectedInitiators, fctnInitiatorToPortName());
        return Sets.newHashSet(transformed);
    }
}
