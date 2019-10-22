/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import static com.emc.sa.service.ServiceParams.BASE_NAME;
import static com.emc.sa.service.ServiceParams.BLOCK_SIZE;
import static com.emc.sa.service.ServiceParams.DO_FORMAT;
import static com.emc.sa.service.ServiceParams.FILE_SYSTEM_TYPE;
import static com.emc.sa.service.ServiceParams.MOUNT_POINT;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.LinuxSystemCLI;

public class MountBlockVolumeHelper {

    private static final String DEFAULT_BLOCK_SIZE = "DEFAULT";

    private final LinuxSupport linuxSupport;
    private final String hostname;

    @Param(value = BASE_NAME, required = false)
    protected String baseName;

    @Param(MOUNT_POINT)
    protected String mountPoint;

    @Param(FILE_SYSTEM_TYPE)
    protected String fsType;

    @Param(value = BLOCK_SIZE, required = false)
    protected String blockSize;

    @Param(value = DO_FORMAT, required = false)
    protected boolean doFormat = true;

    /** The flag which indicates whether we're using EMC PowerPath for multipathing or not. */
    protected boolean usePowerPath;

    public static MountBlockVolumeHelper createHelper(LinuxSystemCLI linuxSystem, List<Initiator> hostPorts) {
        LinuxSupport linuxSupport = new LinuxSupport(linuxSystem, hostPorts);
        MountBlockVolumeHelper mountBlockVolumeHelper = new MountBlockVolumeHelper(linuxSupport);
        BindingUtils.bind(mountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
        return mountBlockVolumeHelper;
    }

    private MountBlockVolumeHelper(LinuxSupport linuxSupport) {
        this.linuxSupport = linuxSupport;
        this.hostname = linuxSupport.getHostName();
    }

    public void precheck() {
        linuxSupport.verifyMountPoint(mountPoint);
        usePowerPath = linuxSupport.checkForMultipathingSoftware();
        linuxSupport.checkForFileSystemCompatibility(fsType);
        linuxSupport.checkDirectoryDoesNotExist(mountPoint);
    }

    public void verifyMountConfiguration(BlockObjectRestRep volume) {
        if (BlockStorageUtils.isVolumeMounted(volume) && doFormat) {
            ExecutionUtils.fail("failTask.verifyMountConfiguration", volume.getName(), volume.getName());
        }
    }

    public void refreshStorage(List<? extends BlockObjectRestRep> volumes) {
        linuxSupport.refreshStorage(volumes, usePowerPath);
    }

    public void mountVolume(BlockObjectRestRep volume) {
        String device = linuxSupport.getDevice(volume, usePowerPath);

        if (doFormat) {
        	logInfo("linux.mount.block.volume.create.partition", hostname, device);
        	linuxSupport.resizePartition(device);
        }

        String partitionDevice = linuxSupport.getPrimaryPartitionDevice(volume, mountPoint, device, usePowerPath);
        logInfo("linux.mount.block.volume.partition.name", partitionDevice);

        if (doFormat) {
            logInfo("linux.mount.block.volume.format", hostname, partitionDevice, fsType);
            linuxSupport.formatVolume(partitionDevice, fsType, getBlockSize());
        }

        logInfo("linux.mount.block.volume.mount", hostname, partitionDevice, mountPoint, fsType, volume.getWwn());
        linuxSupport.createDirectory(mountPoint);
        linuxSupport.addToFSTab(partitionDevice, mountPoint, fsType, null);
        linuxSupport.mountPath(mountPoint);

        linuxSupport.setVolumeMountPointTag(volume, mountPoint);
    }

    private String getBlockSize() {
        if (StringUtils.equalsIgnoreCase(DEFAULT_BLOCK_SIZE, blockSize)) {
            return StringUtils.EMPTY;
        }
        return blockSize;
    }

}
