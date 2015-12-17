/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;

import java.util.Collection;
import java.util.List;

import com.emc.hpux.HpuxSystem;
import com.emc.hpux.model.MountPoint;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.service.hpux.tasks.CheckForPowerPath;
import com.emc.sa.service.hpux.tasks.CreateDirectory;
import com.emc.sa.service.hpux.tasks.DeleteDirectory;
import com.emc.sa.service.hpux.tasks.FindMountPointsForVolumes;
import com.emc.sa.service.hpux.tasks.FindRDiskForVolume;
import com.emc.sa.service.hpux.tasks.GetDirectoryContents;
import com.emc.sa.service.hpux.tasks.HpuxExecutionTask;
import com.emc.sa.service.hpux.tasks.ListMountPoints;
import com.emc.sa.service.hpux.tasks.MakeFilesystem;
import com.emc.sa.service.hpux.tasks.MountPath;
import com.emc.sa.service.hpux.tasks.Rescan;
import com.emc.sa.service.hpux.tasks.UnmountPath;
import com.emc.sa.service.hpux.tasks.UpdatePowerPathEntries;
import com.emc.sa.service.hpux.tasks.VerifyMountPoint;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.tasks.RemoveBlockVolumeMachineTag;
import com.emc.sa.service.vipr.block.tasks.SetBlockVolumeMachineTag;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;

public class HpuxSupport {

    private final HpuxSystem targetSystem;

    public HpuxSupport(HpuxSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getHostName() {
        return targetSystem.getHost();
    }

    public HpuxSystem getTargetSystem() {
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

    private String getMountPointTagName() {
        return KnownMachineTags.getHostMountPointTagName(targetSystem.getHostId());
    }

    public void updatePowerPathEntries() {
        execute(new UpdatePowerPathEntries());
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

    public void verifyMountPoint(String path) {
        execute(new VerifyMountPoint(path));
    }

    public void findMountPoints(List<VolumeSpec> volumes) {
        execute(new FindMountPointsForVolumes(targetSystem.getHostId(), volumes));
    }

    public List<MountPoint> getMountPoints() {
        return execute(new ListMountPoints());
    }

    protected <T> T execute(HpuxExecutionTask<T> task) {
        task.setTargetSystem(targetSystem);

        return ViPRExecutionUtils.execute(task);
    }

    public boolean checkForPowerPath() {
        return execute(new CheckForPowerPath());
    }

    public void makeFilesystem(String disk) {
        execute(new MakeFilesystem(disk));
    }

    public void mount(String source, String mountPoint) {
        execute(new MountPath(source, mountPoint));
        addRollback(new UnmountPath(mountPoint));
    }

    public void unmount(String mountPoint) {
        execute(new UnmountPath(mountPoint));
    }

    public void rescan() {
        execute(new Rescan());
    }

    public String findRDisk(BlockObjectRestRep volume, boolean usePowerPath) {
        String rhdiskDevice = execute(new FindRDiskForVolume(volume, usePowerPath));
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

    public String getDevice(BlockObjectRestRep volume, boolean usePowerPath) {
        try {
            // we will retry this up to 5 times
            int remainingAttempts = 5;
            while (remainingAttempts-- >= 0) {
                try {
                    return findRDisk(volume, usePowerPath);
                } catch (IllegalStateException e) {
                    String errorMessage = String.format("Unable to find device for WWN %s. %s more attempts will be made.",
                            volume.getWwn(), remainingAttempts);
                    if (remainingAttempts == 0) {
                        getDeviceFailed(volume, errorMessage, e);
                    }
                    logWarn(errorMessage);
                    Thread.sleep(5000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    public void addRollback(HpuxExecutionTask<?> rollbackTask) {
        rollbackTask.setTargetSystem(targetSystem);

        ExecutionUtils.addRollback(rollbackTask);
    }

}
