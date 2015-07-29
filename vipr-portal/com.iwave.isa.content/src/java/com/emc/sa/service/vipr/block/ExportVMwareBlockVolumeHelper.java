/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 */
public class ExportVMwareBlockVolumeHelper extends ExportBlockVolumeHelper {

    public static final Integer USE_EXISTING_HLU = -2;

    @Override
    protected Map<URI, Integer> getVolumeHLUs(List<URI> volumeIds) {
        return BlockStorageUtils.findBlockVolumeHLUs(volumeIds);
    }
}
