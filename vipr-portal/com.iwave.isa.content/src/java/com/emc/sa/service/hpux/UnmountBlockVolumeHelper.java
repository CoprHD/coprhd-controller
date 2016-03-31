/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.hpux.HpuxSystem;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class UnmountBlockVolumeHelper {

    private HpuxSupport hpuxSupport;

    public UnmountBlockVolumeHelper(HpuxSupport hpuxSupport) {
        this.hpuxSupport = hpuxSupport;
    }

    public static UnmountBlockVolumeHelper create(final HpuxSystem hpuxSystem, List<Initiator> hostPorts) {
        UnmountBlockVolumeHelper unmountBlockVolumeHelper = new UnmountBlockVolumeHelper(new HpuxSupport(hpuxSystem));
        BindingUtils.bind(unmountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
        return unmountBlockVolumeHelper;
    }

    /** The list of VolumeSpec objects which represents the volumes to unmount and their associated metadata. */
    private List<VolumeSpec> volumes;

    public void setVolumes(List<? extends BlockObjectRestRep> volumes) {
        this.volumes = Lists.newArrayList();
        for (BlockObjectRestRep volume : volumes) {
            this.volumes.add(new VolumeSpec(volume));
        }
    }

    public void precheck() {
        hpuxSupport.findMountPoints(volumes);
    }

    public void unmountVolumes() {

        hpuxSupport.rescan();

        Set<URI> untaggedVolumeIds = Sets.newHashSet();
        for (VolumeSpec volume : volumes) {

            hpuxSupport.unmount(volume.mountPoint.getPath());

            hpuxSupport.removeVolumeMountPointTag(volume.viprVolume);

            // delete the directory entry if it's empty
            if (hpuxSupport.isDirectoryEmpty(volume.mountPoint.getPath())) {
                hpuxSupport.deleteDirectory(volume.mountPoint.getPath());
            }

        }

        // Ensure all volumes have had their mount point tag removed
        for (VolumeSpec volume : volumes) {
            if (untaggedVolumeIds.add(volume.viprVolume.getId())) {
                hpuxSupport.removeVolumeMountPointTag(volume.viprVolume);
            }
        }

    }
}
