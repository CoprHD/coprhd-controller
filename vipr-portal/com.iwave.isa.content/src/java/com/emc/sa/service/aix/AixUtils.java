/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix;

import java.net.URI;
import java.util.Map;

import com.emc.aix.model.MountPoint;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.storageos.model.block.BlockObjectRestRep;

public final class AixUtils {

    public static MountPoint getMountPoint(URI hostId, Map<String, MountPoint> results, BlockObjectRestRep volume) {
        String volumeMountPoint = KnownMachineTags.getBlockVolumeMountPoint(hostId, volume);
        if (results.containsKey(volumeMountPoint)) {
            return results.get(volumeMountPoint);
        }
        else {
            throw new IllegalStateException(String.format("Mount point %s for volume %s (%s) could not be found", volumeMountPoint,
                    volume.getName(), volume.getId()));
        }
    }

}
