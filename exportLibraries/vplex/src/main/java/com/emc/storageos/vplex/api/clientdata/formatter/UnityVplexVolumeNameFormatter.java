/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api.clientdata.formatter;

import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

/**
 * @{inheritDoc
 */
public class UnityVplexVolumeNameFormatter extends
        DefaultVplexVolumeNameFormatter {
    
    private static final String UNITY_NATIVE_ID_PREFIX = "sv_";

    /**
     * @{inheritDoc
     */
    public UnityVplexVolumeNameFormatter(VolumeInfo volumeInfo) {
        super(volumeInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String assembleDefaultName(String storageSystemSerialNumber, String volumeNativeId) {
        // Unity native ids have a prefix of "sv_". The "_" can cause problems as
        // the underscore is used as a separator character. See COP-25839. Therefore,
        // we simply eliminate these characters when generating the claimed name
        // for the volume.
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
        nameBuilder.append(storageSystemSerialNumber);
        nameBuilder.append(VPlexApiConstants.HYPHEN_OPERATOR);
        nameBuilder.append(volumeNativeId.replace(UNITY_NATIVE_ID_PREFIX,""));
        return nameBuilder.toString();
    }
}
