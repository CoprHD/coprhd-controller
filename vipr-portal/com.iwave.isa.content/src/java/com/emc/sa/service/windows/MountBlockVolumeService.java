/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.net.URI;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.ExportBlockVolumeHelper;
import com.emc.sa.service.windows.tasks.VerifyClusterConfiguration;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.iwave.ext.windows.model.wmi.DiskDrive;

@Service("Windows-MountBlockVolume")
public class MountBlockVolumeService extends WindowsService {

    @Param(ServiceParams.VOLUME)
    protected String volumeId;

    @Bindable
    protected ExportBlockVolumeHelper exportBlockVolumeHelper = new ExportBlockVolumeHelper();

    protected List<MountBlockVolumeHelper> mountBlockVolumeHelpers;

    private BlockObjectRestRep volume;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        exportBlockVolumeHelper.precheck();
        volume = BlockStorageUtils.getBlockResource(uri(volumeId));
        if (BlockStorageUtils.isVolumeVMFSDatastore(volume)) {
            ExecutionUtils.fail("failTask.verifyVMFSDatastore", volume.getName(), volume.getName());
        }
        ViPRService.checkForBootVolume(new URI(volumeId));
        mountBlockVolumeHelpers = MountBlockVolumeHelper.createHelpers(windowsSystems, extractCapacityInBytes(volume));
        verifyClusterConfiguration();
        for (MountBlockVolumeHelper mountBlockVolumeHelper : mountBlockVolumeHelpers) {
            mountBlockVolumeHelper.verifyMountConfiguration(volume);
            mountBlockVolumeHelper.precheck();
        }
    }

    protected void verifyClusterConfiguration() {
        if (isClustered()) {
            if (CollectionUtils.isEmpty(mountBlockVolumeHelpers)) {
                ExecutionUtils.fail("task.fail.verifyClusterConfiguration", VerifyClusterConfiguration.getHostsDisplay(hosts));
            }
            mountBlockVolumeHelpers.get(0).verifyClusterHosts(hosts);
        }
    }

    private long extractCapacityInBytes(BlockObjectRestRep blockObject) {

        long capacityInBytes = 0;
        VolumeRestRep volume = getVolume(blockObject);
        if (volume == null) {
            ExecutionUtils.fail("task.fail.extractCapacityInBytes", blockObject.getId(), blockObject.getId());
        }

        if (volume != null && StringUtils.isNotBlank(volume.getCapacity())) {
            try {
                long capacityInGB = Double.valueOf(volume.getCapacity()).longValue();
                capacityInBytes = capacityInGB * 1024 * 1024 * 1024;
            } catch (NumberFormatException e) {
                capacityInBytes = -1;
            }
        }

        return capacityInBytes;
    }

    protected VolumeRestRep getVolume(BlockObjectRestRep blockObject) {
        VolumeRestRep volume = null;
        if (blockObject instanceof VolumeRestRep) {
            volume = (VolumeRestRep) blockObject;
        }
        else if (blockObject instanceof BlockSnapshotRestRep) {
            BlockSnapshotRestRep snapshot = (BlockSnapshotRestRep) blockObject;
            volume = getClient().blockVolumes().get(snapshot.getParent());
        }
        return volume;
    }

    @Override
    public void execute() throws Exception {
        exportBlockVolumeHelper.exportVolumes();

        acquireHostAndClusterLock();
        volume = BlockStorageUtils.getBlockResource(uri(volumeId));
        // Only perform mounting/formatting on ONE host
        DiskDrive diskDrive = mountBlockVolumeHelpers.get(0).mountVolume(volume);

        if (isClustered()) {
            for (int i = 1; i < mountBlockVolumeHelpers.size(); i++) {
                mountBlockVolumeHelpers.get(i).rescanDisks();
            }

            mountBlockVolumeHelpers.get(0).addDiskToCluster(diskDrive);
        }
    }
}
