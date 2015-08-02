/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.tasks.RemoveBlockVolumeMachineTag;
import com.emc.sa.service.vipr.block.tasks.SetBlockVolumeMachineTag;
import com.emc.sa.service.windows.tasks.AddDiskToCluster;
import com.emc.sa.service.windows.tasks.AssignLabel;
import com.emc.sa.service.windows.tasks.DeleteClusterResource;
import com.emc.sa.service.windows.tasks.DeleteDirectory;
import com.emc.sa.service.windows.tasks.DiscoverDisksForVolumes;
import com.emc.sa.service.windows.tasks.ExtendVolume;
import com.emc.sa.service.windows.tasks.FetchDiskDetail;
import com.emc.sa.service.windows.tasks.FindDisksForVolumes;
import com.emc.sa.service.windows.tasks.FormatAndMountDisk;
import com.emc.sa.service.windows.tasks.GetAssignedDriveLetters;
import com.emc.sa.service.windows.tasks.GetDirectoryContents;
import com.emc.sa.service.windows.tasks.GetDiskToResourceMap;
import com.emc.sa.service.windows.tasks.MakeDirectory;
import com.emc.sa.service.windows.tasks.MountVolume;
import com.emc.sa.service.windows.tasks.OfflineClusterResource;
import com.emc.sa.service.windows.tasks.OfflineDisk;
import com.emc.sa.service.windows.tasks.OnlineDisk;
import com.emc.sa.service.windows.tasks.CheckPartitionRestriction;
import com.emc.sa.service.windows.tasks.RescanDisks;
import com.emc.sa.service.windows.tasks.UnmountVolume;
import com.emc.sa.service.windows.tasks.VerifyActiveCluster;
import com.emc.sa.service.windows.tasks.VerifyClusterConfiguration;
import com.emc.sa.service.windows.tasks.VerifyDriveLetterIsAvailable;
import com.emc.sa.service.windows.tasks.VerifyFailoverClusterInstalled;
import com.emc.sa.service.windows.tasks.VerifyMountPointHostDriveIsMounted;
import com.emc.sa.service.windows.tasks.VerifyMountPointHostDriveIsNotShared;
import com.emc.sa.service.windows.tasks.VerifyMountPointIsDriveLetter;
import com.emc.sa.service.windows.tasks.VerifyWinRM;
import com.emc.sa.service.windows.tasks.WindowsExecutionTask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.model.Volume;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class WindowsSupport {

    private WindowsSystemWinRM targetSystem;

    public WindowsSupport(WindowsSystemWinRM targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getHostName() {
        return targetSystem.getTarget().getHost();
    }

    public void verifyWinRM() {
        execute(new VerifyWinRM());
    }

    public void verifyClusterSupport() {
        execute(new VerifyFailoverClusterInstalled());
        execute(new VerifyActiveCluster());
    }

    public void verifyClusterHosts(List<Host> hosts) {
        execute(new VerifyClusterConfiguration(hosts));
    }

    public void rescanDisks() {
        execute(new RescanDisks());
    }

    public Disk getDiskDetail(DiskDrive disk) {
        return execute(new FetchDiskDetail(disk.getNumber()));
    }

    public Set<String> getAssignedDriveLetters() {
        return execute(new GetAssignedDriveLetters());
    }

    public String getAssignedMountPoint(DiskDrive disk) {
        Disk detail = getDiskDetail(disk);
        return getAssignedMountPoint(disk, detail);
    }

    public String getAssignedMountPoint(DiskDrive disk, Disk detail) {
        if ((detail != null) && (detail.getVolumes() != null) && (!detail.getVolumes().isEmpty())) {
            return detail.getVolumes().get(0).getMountPoint();
        }
        ExecutionUtils.fail("failTask.WindowsSupport.noMountPoint", String.valueOf(disk.getNumber()), disk.getName(),
                String.valueOf(disk.getNumber()));
        return null; // fail() will throw an exception - we should never get here
    }

    public void assignLabel(Volume volume, String label) {
        execute(new AssignLabel(volume, label));
    }

    public Map<? extends BlockObjectRestRep, DiskDrive> discoverDisks(
            Collection<? extends BlockObjectRestRep> volumes, int attempts, long delay) {
        return execute(new DiscoverDisksForVolumes(volumes, attempts, delay));
    }

    public Map<? extends BlockObjectRestRep, DiskDrive> findDisks(Collection<? extends BlockObjectRestRep> volumes) {
        return execute(new FindDisksForVolumes(volumes));
    }

    public boolean isReadOnly(Disk disk) {
        return Boolean.TRUE.equals(disk.getCurrentReadOnlyState())
                || Boolean.TRUE.equals(disk.getReadOnly());
    }

    public void onlineDisk(Disk disk) {
        boolean isReadOnly = isReadOnly(disk);
        execute(new OnlineDisk(disk.getNumber(), isReadOnly));
        addRollback(new OfflineDisk(disk.getNumber()));
    }

    public void offlineDisk(DiskDrive disk) {
        execute(new OfflineDisk(disk.getNumber()));
    }

    public void checkPartitionRestriction(DiskDrive disk, long volumeSizeInBytes) {
        execute(new CheckPartitionRestriction(disk.getNumber(), volumeSizeInBytes));
    }

    public void formatAndMount(DiskDrive disk, String fsType,
            String allocationUnitSize, String label, String mountpoint, String partitionType) {
        execute(new FormatAndMountDisk(disk, fsType, allocationUnitSize, label,
                mountpoint, partitionType));
    }

    public void mountVolume(int volumeNumber, String mountpoint) {
        execute(new MountVolume(volumeNumber, mountpoint));
    }

    public void unmountVolume(int volumeNumber, String mountpoint) {
        execute(new UnmountVolume(volumeNumber, mountpoint));
    }

    public void makeDirectory(String directory) {
        execute(new MakeDirectory(directory));
    }

    public void extendDrive(VolumeRestRep volume) {
        String mountPoint = getMountPoint(volume);
        extendDrive(volume, mountPoint);
    }

    public void extendDrive(BlockObjectRestRep volume, String mountPoint) {
        execute(new ExtendVolume(mountPoint));
        addAffectedResource(volume.getId());
    }

    public <T extends BlockObjectRestRep> void removeVolumeMountPoint(T volume) {
        ExecutionUtils.execute(new RemoveBlockVolumeMachineTag(volume.getId(), getMountPointTagName()));
        addAffectedResource(volume);
    }

    public <T extends BlockObjectRestRep> void addVolumeMountPoint(T volume, String mountpoint) {
        ExecutionUtils.execute(new SetBlockVolumeMachineTag(volume.getId(), getMountPointTagName(), mountpoint));
        ExecutionUtils.addRollback(new RemoveBlockVolumeMachineTag(volume.getId(), getMountPointTagName()));
        addAffectedResource(volume);
    }

    public <T extends BlockObjectRestRep> String getMountPoint(T volume) {
        if (targetSystem.getClusterId() != null) {
            return KnownMachineTags.getBlockVolumeMountPoint(targetSystem.getClusterId(), volume);
        }
        else {
            return KnownMachineTags.getBlockVolumeMountPoint(targetSystem.getHostId(), volume);
        }
    }

    public <T extends BlockObjectRestRep> void verifyVolumesMounted(Collection<T> volumes) {
        for (T volume : volumes) {
            verifyVolumeMounted(volume);
        }
    }

    public <T extends BlockObjectRestRep> void verifyVolumeMounted(T volume) {
        String driveLetter = WindowsUtils.getDriveLetterFromMountPath(getMountPoint(volume));
        if (StringUtils.isBlank(driveLetter)) {
            ExecutionUtils.fail("failTask.WindowsSupport.notMounted", driveLetter, volume.getDeviceLabel());
        }
    }

    public String addDiskToCluster(String diskId) {
        String resourceName = execute(new AddDiskToCluster(diskId));
        addRollback(new DeleteClusterResource(resourceName));
        addRollback(new OfflineClusterResource(resourceName));

        return resourceName;
    }

    public void deleteClusterResource(String resourceName) {
        execute(new DeleteClusterResource(resourceName));
    }

    public void offlineClusterResource(String resourceName) {
        execute(new OfflineClusterResource(resourceName));
    }

    public Map<String, String> getDiskToResourceMap() {
        return execute(new GetDiskToResourceMap());
    }

    protected <T> T execute(WindowsExecutionTask<T> task) {
        task.setTargetSystem(targetSystem);

        return ViPRExecutionUtils.execute(task);
    }

    public void addRollback(WindowsExecutionTask<?> rollbackTask) {
        rollbackTask.setTargetSystem(targetSystem);

        ExecutionUtils.addRollback(rollbackTask);
    }

    public boolean isClustered() {
        return targetSystem.getClusterId() != null;
    }

    private String getMountPointTagName() {
        if (targetSystem.getClusterId() != null) {
            return KnownMachineTags.getHostMountPointTagName(targetSystem.getClusterId());
        }
        else {
            return KnownMachineTags.getHostMountPointTagName(targetSystem.getHostId());
        }
    }

    public void deleteDirectory(String mountPoint) {
        execute(new DeleteDirectory(mountPoint));
    }

    public boolean isDirectoryEmpty(String mountPoint) {
        return execute(new GetDirectoryContents(mountPoint)).isEmpty();
    }

    public void verifyMountPointIsDriveLetter(String mountPoint) {
        execute(new VerifyMountPointIsDriveLetter(mountPoint));
    }

    public void verifyMountPointHostDriveIsMounted(String mountPoint, Collection<String> assignedMountPoints) {
        execute(new VerifyMountPointHostDriveIsMounted(mountPoint, assignedMountPoints));
    }

    public void verifyMountPointIsNotShared(String mountPoint) {
        execute(new VerifyMountPointHostDriveIsNotShared(mountPoint));
    }

    public void verifyMountPointLetterIsAvailable(String mountPointLetter, Set<String> usedDriveLetters) {
        execute(new VerifyDriveLetterIsAvailable(mountPointLetter, usedDriveLetters));
    }

    public boolean isGuid(String expression)
    {
        if (expression != null) {
            Pattern guidRegEx = Pattern
                    .compile("^(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})$");

            Matcher matcher = guidRegEx.matcher(expression);
            return matcher.find();
        }
        return false;
    }

}
