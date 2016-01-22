/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import java.net.URI;
import java.util.List;

import com.emc.hpux.model.MountPoint;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.storageos.model.block.BlockObjectRestRep;

final class HpuxUtils {

    private HpuxUtils() {
    }

    static MountPoint getMountPoint(URI hostId, List<MountPoint> results, BlockObjectRestRep volume) {
        String volumeMountPoint = KnownMachineTags.getBlockVolumeMountPoint(hostId, volume);

        for (MountPoint mp : results) {
            if (mp.getPath().equals(volumeMountPoint)) {
                return mp;
            }
        }

        throw new IllegalStateException(String.format("Mount point %s for volume %s (%s) could not be found", volumeMountPoint,
                volume.getName(), volume.getId()));

    }

}
