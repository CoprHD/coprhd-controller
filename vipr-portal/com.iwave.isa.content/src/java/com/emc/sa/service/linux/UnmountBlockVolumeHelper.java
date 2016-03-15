/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.util.VolumeWWNUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.model.MountPoint;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PowerPathDevice;

public class UnmountBlockVolumeHelper {
    private final LinuxSupport linux;

    public static UnmountBlockVolumeHelper createHelper(LinuxSystemCLI linuxSystem, List<Initiator> hostPorts) {
        LinuxSupport linuxSupport = new LinuxSupport(linuxSystem, hostPorts);
        UnmountBlockVolumeHelper unmountBlockVolumeHelper = new UnmountBlockVolumeHelper(linuxSupport);
        BindingUtils.bind(unmountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
        return unmountBlockVolumeHelper;
    }

    private UnmountBlockVolumeHelper(LinuxSupport linuxSupport) {
        this.linux = linuxSupport;
    }

    /**
     * container class to hold all the specifications of a particular volume, including its mountpoint,
     * multipath/powerpath entries and related volumes
     */
    public class VolumeSpec {
        public BlockObjectRestRep viprVolume;
        public MountPoint mountPoint;
        public List<MultiPathEntry> multipathEntries;
        public List<PowerPathDevice> powerpathDevices;
        public List<BlockObjectRestRep> relatedVolumes;

        public VolumeSpec(BlockObjectRestRep volume) {
            this.viprVolume = volume;
        }
    }

    /** The list of VolumeSpec objects which represents the volumes to unmount and their associated metadata. */
    private List<VolumeSpec> volumes;

    /** The flag which indicates whether we're using EMC PowerPath for multipathing or not. */
    private boolean usePowerPath;

    public void setVolumes(List<? extends BlockObjectRestRep> volumes) {
        this.volumes = Lists.newArrayList();
        for (BlockObjectRestRep volume : volumes) {
            this.volumes.add(new VolumeSpec(volume));
        }
    }

    /** search through the volumes list to find the {@link VolumeSpec} which has the given wwn. */
    private VolumeSpec getVolumeSpecByWwn(String wwn) {
        for (VolumeSpec volume : volumes) {
            if (VolumeWWNUtils.wwnMatches(wwn, volume.viprVolume)) {
                return volume;
            }
        }
        return null;
    }

    /**
     * search through the volumes list to find the {@link VolumeSpec} which has the given wwn and return its
     * 'viprVolume' which is a {@link VolumeRestRep}
     */
    private BlockObjectRestRep findVolumeRestRepByWwn(String relatedWwn) {
        VolumeSpec relatedVolume = getVolumeSpecByWwn(relatedWwn);
        if (relatedVolume != null) {
            return relatedVolume.viprVolume;
        }
        return null;
    }

    public void precheck() {
        linux.findMountPoints(volumes);
        usePowerPath = linux.checkForMultipathingSoftware();
        if (usePowerPath) {
            linux.findPowerPathDevices(volumes);
        }
        else {
            linux.findMultipathEntries(volumes);
        }
        linux.ensureVolumesAreMounted(volumes);
        findRelatedVolumes();
    }

    /**
     * Finds the volumes related to the mount points selected. When a mount point is removed, all related volumes have
     * to have their mount point tags removed.
     */
    private void findRelatedVolumes() {
        for (VolumeSpec volume : volumes) {
            volume.relatedVolumes = Lists.newArrayList();
            Set<String> volumeWwns = getVolumeWwns(volume);
            for (String relatedWwn : volumeWwns) {
                BlockObjectRestRep related = findVolumeRestRepByWwn(relatedWwn);
                if (related == null) {
                    related = BlockStorageUtils.getVolumeByWWN(relatedWwn);
                }
                if (related != null) {
                    volume.relatedVolumes.add(related);
                }
            }
        }
    }

    public void unmountVolumes() {
        Set<URI> untaggedVolumeIds = Sets.newHashSet();
        for (VolumeSpec volume : volumes) {

            // unmount the volume
            linux.unmountPath(volume.mountPoint.getPath());

            // remove from fstab
            linux.removeFromFSTab(volume.mountPoint.getPath());

            // remove mount point tag from all volumes for this mount point
            for (BlockObjectRestRep mountedVolume : volume.relatedVolumes) {
                linux.removeVolumeMountPointTag(mountedVolume);
                untaggedVolumeIds.add(mountedVolume.getId());
            }

            // remove multipath/powerpath entries
            if (usePowerPath) {
                linux.removePowerPathDevices(volume.powerpathDevices);
            }
            else {
                linux.removeMultipathEntries(volume.multipathEntries);
            }

            // delete the directory entry if it's empty
            if (linux.isDirectoryEmpty(volume.mountPoint.getPath())) {
                linux.deleteDirectory(volume.mountPoint.getPath());
            }
        }

        // Ensure all volumes have had their mount point tag removed
        for (VolumeSpec volume : volumes) {
            if (untaggedVolumeIds.add(volume.viprVolume.getId())) {
                linux.removeVolumeMountPointTag(volume.viprVolume);
            }
        }

    }

    private Set<String> getVolumeWwns(VolumeSpec volume) {
        Set<String> volumeWwns = Sets.newHashSet();
        if (usePowerPath) {
            for (PowerPathDevice device : volume.powerpathDevices) {
                volumeWwns.add(getVolumeWwn(device));
            }
        }
        else {
            for (MultiPathEntry entry : volume.multipathEntries) {
                volumeWwns.add(getVolumeWwn(entry));
            }
        }
        return volumeWwns;
    }

    private String getVolumeWwn(PowerPathDevice device) {
        return StringUtils.upperCase(device.getWwn());
    }

    private String getVolumeWwn(MultiPathEntry entry) {
        String wwid = entry.getWwid();
        // Multipath WWIDs start with an extra digit ('3')
        String wwn = StringUtils.substring(wwid, 1);
        return StringUtils.upperCase(wwn);
    }

}
