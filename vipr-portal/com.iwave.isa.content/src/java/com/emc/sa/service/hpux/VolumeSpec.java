/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import java.util.List;

import com.emc.aix.model.MultiPathDevice;
import com.emc.hpux.model.MountPoint;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.model.PowerPathDevice;

public class VolumeSpec {
    public BlockObjectRestRep viprVolume;
    public MountPoint mountPoint;
    public List<MultiPathDevice> multipathEntries;
    public List<PowerPathDevice> powerpathDevices;
    public List<BlockObjectRestRep> relatedVolumes;

    public VolumeSpec(BlockObjectRestRep volume) {
        this.viprVolume = volume;
    }

    @Override
    public String toString() {
        return "VolumeSpec [viprVolume=" + viprVolume + ", mountPoint="
                + mountPoint + ", multipathEntries=" + multipathEntries
                + ", powerpathDevices=" + powerpathDevices
                + ", relatedVolumes=" + relatedVolumes + "]";
    }
}