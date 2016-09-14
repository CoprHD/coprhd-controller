/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.common;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;

/**
 * This subclass of {@link AbstractExportMaskValidator} will:
 * 1) Query expected {@link BlockObject} instances and transform them into their respective native IDs
 * 2) Query SMI-S for CIM_StorageVolume.DeviceID instance properties associated to the given export mask.
 */
public class ExportMaskVolumesValidator extends AbstractExportMaskValidator {

    private final Collection<? extends BlockObject> blockObjects;

    public ExportMaskVolumesValidator(StorageSystem storage, ExportMask exportMask,
                                      Collection<? extends BlockObject> blockObjects) {
        super(storage, exportMask, FIELD_VOLUMES);
        this.blockObjects = blockObjects;
    }

    @Override
    protected String getAssociatorProperty() {
        return SmisConstants.CP_DEVICE_ID;
    }

    @Override
    protected String getAssociatorClass() {
        return SmisConstants.STORAGE_VOLUME_CLASS;
    }

    @Override
    protected Function<? super String, String> getHardwareTransformer() {
        return null;
    }

    @Override
    protected Set<String> getDatabaseResources() {
        if (blockObjects == null || blockObjects.isEmpty()) {
            return Sets.newHashSet();
        }

        Collection<String> transformed = Collections2.transform(blockObjects, fctnBlockObjectToNativeID());
        return Sets.newHashSet(transformed);
    }

}
