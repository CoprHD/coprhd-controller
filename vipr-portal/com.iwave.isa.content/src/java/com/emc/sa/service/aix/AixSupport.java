/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.emc.aix.AixSystem;
import com.emc.aix.model.MountPoint;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.service.aix.UnmountBlockVolumeHelper.VolumeSpec;
import com.emc.sa.service.aix.tasks.AddToFilesystemsConfig;
import com.emc.sa.service.aix.tasks.AixExecutionTask;
import com.emc.sa.service.aix.tasks.CheckForPowerPath;
import com.emc.sa.service.aix.tasks.CreateDirectory;
import com.emc.sa.service.aix.tasks.DeleteDirectory;
import com.emc.sa.service.aix.tasks.FindHDiskForVolume;
import com.emc.sa.service.aix.tasks.FindMountPointsForVolumes;
import com.emc.sa.service.aix.tasks.FindMultiPathEntriesForMountPoint;
import com.emc.sa.service.aix.tasks.FindPowerPathEntriesForMountPoint;
import com.emc.sa.service.aix.tasks.GetDirectoryContents;
import com.emc.sa.service.aix.tasks.ListMountPoints;
import com.emc.sa.service.aix.tasks.MountPath;
import com.emc.sa.service.aix.tasks.RemoveFromFilesystemsConfig;
import com.emc.sa.service.aix.tasks.RemovePowerPathDevice;
import com.emc.sa.service.aix.tasks.RescanDevices;
import com.emc.sa.service.aix.tasks.UnmountPath;
import com.emc.sa.service.aix.tasks.UpdatePowerPathEntries;
import com.emc.sa.service.aix.tasks.VerifyMountPoint;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.tasks.RemoveBlockVolumeMachineTag;
import com.emc.sa.service.vipr.block.tasks.SetBlockVolumeMachineTag;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.iwave.ext.linux.model.PowerPathDevice;

public class AixSupport {

    private final AixSystem targetSystem;

    public AixSupport(AixSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getHostName() {
        return targetSystem.getHost();
    }

    public AixSystem getTargetSystem() {
        return this.targetSystem;
    }

    protected void logDebug(String message, Object... args) {
        ExecutionUtils.currentContext().logDebug(message, args);
    }

    protected void logInfo(String message, Object... args) {
        ExecutionUtils.currentContext().logInfo(message, args);
    }

    protected void logWarn(String message, Object... args) {
        ExecutionUtils.currentContext().logWarn(message, args);
    }

    protected void logError(String message, Object... args) {
        ExecutionUtils.currentContext().logError(message, args);
    }

    protected void logError(Throwable cause, String message, Object... args) {
        ExecutionUtils.currentContext().logError(message, args);
    }

    public void setVolumeMountPointTag(BlockObjectRestRep volume, String mountPoint) {
        ExecutionUtils.execute(new SetBlockVolumeMachineTag(volume.getId(), getMountPointTagName(), mountPoint));
        ExecutionUtils.addRollback(new RemoveBlockVolumeMachineTag(volume.getId(), getMountPointTagName()));
        addAffectedResource(volume.getId());
    }

    public void removeVolumesMountPointTag(Collection<? extends VolumeRestRep> volumes) {
        for (VolumeRestRep volume : volumes) {
            removeVolumeMountPointTag(volume);
        }
    }

    public void removeVolumeMountPointTag(BlockObjectRestRep volume) {
        ExecutionUtils.execute(new RemoveBlockVolumeMachineTag(volume.getId(), getMountPointTagName()));
        addAffectedResource(volume.getId());
    }

    public void findPowerPathDevices(List<VolumeSpec> volumes) {
        execute(new FindPowerPathEntriesForMountPoint(volumes));
    }

    public void findMultipathDevices(List<VolumeSpec> volumes) {
        execute(new FindMultiPathEntriesForMountPoint(volumes));
    }

    private String getMountPointTagName() {
        return KnownMachineTags.getHostMountPointTagName(targetSystem.getHostId());
    }

    public void updatePowerPathEntries() {
        execute(new UpdatePowerPathEntries());
    }

    public void removePowerPathDevices(Collection<PowerPathDevice> devices) {
        for (PowerPathDevice device : devices) {
            execute(new RemovePowerPathDevice(device));
        }
    }

    public void deleteDirectory(String path) {
        execute(new DeleteDirectory(path));
    }

    public void createDirectory(String path) {
        execute(new CreateDirectory(path));
        addRollback(new DeleteDirectory(path));
    }

    public boolean isDirectoryEmpty(String path) {
        return execute(new GetDirectoryContents(path)).isEmpty();
    }

    public MountPoint findMountPoint(String path) {
        return getMountPoints().get(path);
    }

    public void verifyMountPoint(String path) {
        execute(new VerifyMountPoint(path));
    }

    public void findMountPoints(List<VolumeSpec> volumes) {
        execute(new FindMountPointsForVolumes(targetSystem.getHostId(), volumes));
    }

    public Map<String, MountPoint> getMountPoints() {
        return execute(new ListMountPoints());
    }

    protected <T> T execute(AixExecutionTask<T> task) {
        task.setTargetSystem(targetSystem);

        return ViPRExecutionUtils.execute(task);
    }

    public boolean checkForPowerPath() {
        return execute(new CheckForPowerPath());
    }

    public void mount(String mountPoint) {
        execute(new MountPath(mountPoint));
        addRollback(new UnmountPath(mountPoint));
    }

    public void unmount(String mountPoint) {
        execute(new UnmountPath(mountPoint));
    }

    public String findHDisk(BlockObjectRestRep volume, boolean usePowerPath) {
        String rhdiskDevice = execute(new FindHDiskForVolume(volume, usePowerPath));
        if (rhdiskDevice == null) {
            throw new IllegalStateException(String.format(
                    "Could not find hdisk for Volume %s: - PowerPath/MPIO or SAN connectivity may need attention from an administrator. ",
                    volume.getWwn().toLowerCase()));
        }
        logInfo("aix.support.found.hdisk", rhdiskDevice, volume.getWwn());
        return rhdiskDevice.replaceAll("rhdisk", "hdisk");
    }

    protected <T extends BlockObjectRestRep> void getDeviceFailed(T volume, String errorMessage, IllegalStateException exception) {
        ExecutionUtils.fail("failTask.getDeviceName", volume.getWwn(), errorMessage, exception.getMessage());
    }

    public void rescanDevices() {
        execute(new RescanDevices());
    }

    public void addToFilesystemsConfig(String device, String mountPoint, String fsType) {
        execute(new AddToFilesystemsConfig(device, mountPoint, fsType));
        addRollback(new RemoveFromFilesystemsConfig(mountPoint));
    }

    public void removeFromFilesystemsConfig(String mountPoint) {
        execute(new RemoveFromFilesystemsConfig(mountPoint));
    }

    public String getDevice(BlockObjectRestRep volume, boolean usePowerPath) {
        try {
            // we will retry this up to 5 times
            int remainingAttempts = 5;
            while (remainingAttempts-- >= 0) {
                try {
                    return findHDisk(volume, usePowerPath);
                } catch (IllegalStateException e) {
                    String errorMessage = String.format("Unable to find device for WWN %s. %s more attempts will be made.",
                            volume.getWwn(), remainingAttempts);
                    if (remainingAttempts == 0) {
                        getDeviceFailed(volume, errorMessage, e);
                    }
                    logWarn(errorMessage);
                    Thread.sleep(5000);
                    rescanDevices();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    public void addRollback(AixExecutionTask<?> rollbackTask) {
        rollbackTask.setTargetSystem(targetSystem);

        ExecutionUtils.addRollback(rollbackTask);
    }

    /**
     * Verifies the mount point matches the given hdisk.
     * 
     * @param mountPoint the mount point
     * @param hdisk the hdisk
     */
    public void verifyMountedDevice(MountPoint mountPoint, String hdisk) {
        if (hdisk == null) {
            ExecutionUtils.fail("failTask.verifyVolumeFileSystemMount.noMountFound", mountPoint.getPath(), mountPoint.getPath());
        } else if (!hdisk.equalsIgnoreCase(mountPoint.getDevice())) {
            ExecutionUtils.fail("failTask.verifyVolumeFileSystemMount.devicesDoNotMatch", new Object[] {}, hdisk,
                    mountPoint.getPath(),
                    mountPoint.getDevice());
        }
    }    
}
