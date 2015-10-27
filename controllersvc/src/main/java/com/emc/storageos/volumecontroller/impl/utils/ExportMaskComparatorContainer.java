/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.utils;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;

/**
 * Wrapper class for ExportMask to be used for comparison with other ExportMasks
 *
 */
class ExportMaskComparatorContainer {
    public ExportMask mask;
    public ExportMaskPolicy policy;
    public ExportGroup exportGroup;

    public ExportMaskComparatorContainer(ExportMask inMask, ExportMaskPolicy inPolicy, ExportGroup egp) {
        mask = inMask;
        policy = inPolicy;
        exportGroup = egp;
    }
}