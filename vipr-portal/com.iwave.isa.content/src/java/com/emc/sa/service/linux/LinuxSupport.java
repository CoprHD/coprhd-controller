/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.linux.UnmountBlockVolumeHelper.VolumeSpec;
import com.emc.sa.service.linux.tasks.AddToFSTab;
import com.emc.sa.service.linux.tasks.CheckFileSystem;
import com.emc.sa.service.linux.tasks.CheckForFileSystemCompatibility;
import com.emc.sa.service.linux.tasks.CheckForMultipath;
import com.emc.sa.service.linux.tasks.CheckForPowerPath;
import com.emc.sa.service.linux.tasks.CreateDirectory;
import com.emc.sa.service.linux.tasks.DeleteDirectory;
import com.emc.sa.service.linux.tasks.FindHBAs;
import com.emc.sa.service.linux.tasks.FindIScsiInitiators;
import com.emc.sa.service.linux.tasks.FindIScsiSessions;
import com.emc.sa.service.linux.tasks.FindLunz;
import com.emc.sa.service.linux.tasks.FindMountPointsForVolumes;
import com.emc.sa.service.linux.tasks.FindMultiPathEntriesForMountPoint;
import com.emc.sa.service.linux.tasks.FindMultiPathEntryForDmName;
import com.emc.sa.service.linux.tasks.FindMultiPathEntryForVolume;
import com.emc.sa.service.linux.tasks.FindPowerPathEntriesForMountPoint;
import com.emc.sa.service.linux.tasks.FindPowerPathEntryForVolume;
import com.emc.sa.service.linux.tasks.FormatVolume;
import com.emc.sa.service.linux.tasks.GetDirectoryContents;
import com.emc.sa.service.linux.tasks.GetDirectoryContentsNoFail;
import com.emc.sa.service.linux.tasks.GetFilesystemBlockSize;
import com.emc.sa.service.linux.tasks.GetMountedFilesystem;
import com.emc.sa.service.linux.tasks.GetMultipathBlockDevices;
import com.emc.sa.service.linux.tasks.GetMultipathPrimaryPartitionDeviceParentDmName;
import com.emc.sa.service.linux.tasks.GetPowerpathBlockDevices;
import com.emc.sa.service.linux.tasks.GetPowerpathPrimaryPartitionDeviceParent;
import com.emc.sa.service.linux.tasks.GetPrimaryPartitionDeviceMultiPath;
import com.emc.sa.service.linux.tasks.LinuxExecutionTask;
import com.emc.sa.service.linux.tasks.ListMountPoints;
import com.emc.sa.service.linux.tasks.MountPath;
import com.emc.sa.service.linux.tasks.RemoveFromFSTab;
import com.emc.sa.service.linux.tasks.RemoveLunz;
import com.emc.sa.service.linux.tasks.RemoveMultipathEntry;
import com.emc.sa.service.linux.tasks.RemovePowerPathDevice;
import com.emc.sa.service.linux.tasks.RescanBlockDevices;
import com.emc.sa.service.linux.tasks.RescanHBAs;
import com.emc.sa.service.linux.tasks.RescanIScsiInitiators;
import com.emc.sa.service.linux.tasks.RescanPartitionMap;
import com.emc.sa.service.linux.tasks.ResizeFileSystem;
import com.emc.sa.service.linux.tasks.ResizeMultipathPath;
import com.emc.sa.service.linux.tasks.ResizePartition;
import com.emc.sa.service.linux.tasks.UnmountPath;
import com.emc.sa.service.linux.tasks.UpdateMultiPathEntries;
import com.emc.sa.service.linux.tasks.UpdatePowerPathEntries;
import com.emc.sa.service.linux.tasks.VerifyMountPoint;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.tasks.RemoveBlockVolumeMachineTag;
import com.emc.sa.service.vipr.block.tasks.SetBlockVolumeMachineTag;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IScsiHost;
import com.iwave.ext.linux.model.IScsiSession;
import com.iwave.ext.linux.model.LunInfo;
import com.iwave.ext.linux.model.MountPoint;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PowerPathDevice;

public class LinuxSupport {

    private final LinuxSystemCLI targetSystem;
    private final List<Initiator> hostPorts;

    public LinuxSupport(LinuxSystemCLI targetSystem, List<Initiator> hostPorts) {
        this.targetSystem = targetSystem;
        this.hostPorts = hostPorts;
    }

    public String getHostName() {
        return targetSystem.getHost();
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

    public void refreshStorage(Collection<? extends BlockObjectRestRep> volumes, boolean usePowerPath) {
        Set<URI> virtualArrays = BlockStorageUtils.getVolumeVirtualArrays(volumes);
        Set<Initiator> fcInitiators = BlockStorageUtils.findInitiatorsInVirtualArrays(virtualArrays, hostPorts, Protocol.FC);
        Set<Initiator> iscsiInitiators = BlockStorageUtils.findInitiatorsInVirtualArrays(virtualArrays, hostPorts, Protocol.iSCSI);
        if (!fcInitiators.isEmpty()) {
            List<HBAInfo> hbas = findHBAs(fcInitiators);
            removeLunz();
            rescanHBAs(hbas);
        }
        if (!iscsiInitiators.isEmpty()) {
            checkIScsiConnectivity(iscsiInitiators, volumes);
            rescanIScsiInitiators();
        }

        updateMultipathingSoftware(usePowerPath);
    }

    private void checkIScsiConnectivity(Set<Initiator> initiators, Collection<? extends BlockObjectRestRep> volumes) {
        for (BlockObjectRestRep volume : volumes) {
            checkIScsiConnectivity(initiators, volume);
        }
    }

    private void checkIScsiConnectivity(Set<Initiator> initiators, BlockObjectRestRep volume) {
        List<ITLRestRep> exports = BlockStorageUtils.getExportsForBlockObject(volume.getId());
        List<ITLRestRep> connectedExports = BlockStorageUtils.getExportsForInitiators(exports, initiators);
        // Ensure we have at least one connection to the volume
        Set<String> targetPorts = BlockStorageUtils.getTargetPortsForExports(connectedExports);

        String sourceIqns = StringUtils.join(BlockStorageUtils.getPortNames(initiators), ", ");
        String targetIqns = StringUtils.join(targetPorts, ", ");

        logInfo("linux.support.check.connectivity", sourceIqns, targetIqns);

        int connections = 0;
        List<IScsiHost> iScsiInitiators = findIScsiIInitiators(initiators);
        for (IScsiHost iScsiInitiator : iScsiInitiators) {
            for (IScsiSession session : iScsiInitiator.getSessions()) {
                String sourceIqn = session.getIfaceInitiatorName();
                if (session.getTarget() != null) {
                    String targetIqn = session.getTarget().getIqn();
                    if (targetPorts.contains(targetIqn)) {
                        logInfo("linux.support.connected", sourceIqn, targetIqn);
                        connections++;
                    }
                }
            }
        }

        if (connections == 0) {
            List<IScsiSession> iScsiSessions = findIScsiSessions(initiators);
            for (IScsiSession session : iScsiSessions) {
                String sourceIqn = session.getIfaceInitiatorName();
                if (session.getTarget() != null) {
                    String targetIqn = session.getTarget().getIqn();
                    if (targetPorts.contains(targetIqn)) {
                        logInfo("linux.support.connected", sourceIqn, targetIqn);
                        connections++;
                    }
                }
            }
        }

        if (connections == 0) {
            Object[] detailArgs = new Object[] { volume.getId(), buildInitiatorsString(initiators) };
            Object[] messageArgs = new Object[] { sourceIqns, targetIqns };
            ExecutionUtils.fail("failTask.LinuxSupport.iqnConnectivity", detailArgs, messageArgs);
        }
    }

    protected String buildInitiatorsString(Set<Initiator> initiators) {
        StringBuilder sb = new StringBuilder();
        for (Initiator i : initiators) {
            sb.append(i.getId());
            sb.append(",");
        }
        // remove the last comma
        return sb.toString().substring(0, sb.toString().lastIndexOf(","));
    }

    private void updateMultipathingSoftware(boolean usePowerPath) {
        if (usePowerPath) {
            updatePowerPathEntries();
        }
        else {
            updateMultipathEntries();
        }
    }

    public void removeLunz() {
        List<LunInfo> lunz = execute(new FindLunz());
        for (LunInfo lun : lunz) {
            execute(new RemoveLunz(lun.getHost(), lun.getChannel(), lun.getId()));
        }
    }

    protected boolean checkForMultipathingSoftware() {
        String powerPathError = checkForPowerPath();
        if (powerPathError == null) {
            return true;
        }

        String multipathError = checkForMultipath();
        if (multipathError == null) {
            return false;
        }

        ExecutionUtils.fail("failTask.LinuxSupport.noMultipath", new Object[] {}, powerPathError, multipathError);
        return false; // we'll never get here as .fail will throw an exception
    }

    public String checkForMultipath() {
        return execute(new CheckForMultipath());
    }

    public String checkForPowerPath() {
        return execute(new CheckForPowerPath());
    }

    public void updateMultipathEntries() {
        execute(new UpdateMultiPathEntries());
    }

    private void updatePowerPathEntries() {
        execute(new UpdatePowerPathEntries());
    }

    public List<HBAInfo> findHBAs(Collection<Initiator> ports) {
        return execute(new FindHBAs(BlockStorageUtils.getPortNames(ports)));
    }

    public List<IScsiHost> findIScsiIInitiators(Collection<Initiator> ports) {
        return execute(new FindIScsiInitiators(BlockStorageUtils.getPortNames(ports)));
    }

    public List<IScsiSession> findIScsiSessions(Collection<Initiator> ports) {
        return execute(new FindIScsiSessions(BlockStorageUtils.getPortNames(ports)));
    }

    public void rescanHBAs(List<HBAInfo> hbas) {
        execute(new RescanHBAs(hbas));
    }

    public void rescanIScsiInitiators() {
        execute(new RescanIScsiInitiators());
    }

    public MultiPathEntry findMultiPathEntry(BlockObjectRestRep volume) {
        return execute(new FindMultiPathEntryForVolume(volume));
    }

    public PowerPathDevice findPowerPathEntry(BlockObjectRestRep volume) {
        return execute(new FindPowerPathEntryForVolume(volume));
    }

    public void removeMultipathEntries(Collection<MultiPathEntry> entries) {
        for (MultiPathEntry entry : entries) {
            execute(new RemoveMultipathEntry(entry));
        }
    }

    public void removePowerPathDevices(Collection<PowerPathDevice> devices) {
        for (PowerPathDevice device : devices) {
            execute(new RemovePowerPathDevice(device));
        }
    }

    public void verifyMountPoint(String path) {
        execute(new VerifyMountPoint(path));
    }

    /**
     * Checks fstab to verify that the volumes are mounted on the expected paths on the host. If validation fails, the order is marked as
     * failed.
     * 
     * @param volumes list of volumes to verify
     * @param usePowerPath true if using powerpath, otherwise false for multipath
     */
    public void verifyVolumeMount(List<VolumeSpec> volumes, boolean usePowerPath) {
        for (VolumeSpec volume : volumes) {
            verifyVolumeMount(volume.viprVolume, volume.mountPoint.getPath(), usePowerPath);
        }
    }

    /**
     * Checks fstab to verify that the volume is mounted on the expected path on the host. If validation fails, the order is marked as
     * failed.
     * 
     * @param volume the volume to verify
     * @param mountPointPath the path where the volume should be mounted
     * @param usePowerPath true if using powerpath, otherwise false for multipath
     */
    public void verifyVolumeMount(BlockObjectRestRep volume, String mountPointPath, boolean usePowerPath) {
        Map<String, MountPoint> mountPoints = getMountPoints();
        String device = getDevice(volume, usePowerPath);
        String partitionDevice = getPrimaryPartitionDevice(volume, mountPointPath, device, usePowerPath);

        if (mountPoints == null) {
            ExecutionUtils.fail("failTask.verifyVolumeMount.noMountPointsFound", new Object[] {}, new Object[] {});
        } else if (!mountPoints.containsKey(mountPointPath)) {
            ExecutionUtils.fail("failTask.verifyVolumeMount.mountPointNotFound", new Object[] {}, mountPointPath);
        } else if (!mountPoints.get(mountPointPath).getDevice().equals(partitionDevice)) {
            ExecutionUtils.fail("failTask.verifyVolumeMount.mountDevicesDoNotMatch", new Object[] {},
                    mountPoints.get(mountPointPath).getDevice(), partitionDevice, volume.getWwn(), mountPointPath);
        }
    }

    /**
     * Check the current mounted filesystems on the host to verify that the volume is mounted on the correct path
     * 
     * @param volumes list of volumes
     * @param usePowerPath if true, using powerpath, else using multipath
     */
    public void verifyVolumeFilesystemMount(List<VolumeSpec> volumes, boolean usePowerPath) {
        for (VolumeSpec volume : volumes) {
            verifyVolumeFilesystemMount(volume.viprVolume, volume.mountPoint.getPath(), usePowerPath);
        }
    }

    /**
     * Checks the current mounted filesystems on the host to verify that the volume is mounted on the correct path
     * 
     * @param volume volume to verify
     * @param mountPointPath the path to verify
     * @param usePowerPath
     */
    public void verifyVolumeFilesystemMount(BlockObjectRestRep volume, String mountPointPath, boolean usePowerPath) {
        String device = getDevice(volume, usePowerPath);
        String partitionDevice = getPrimaryPartitionDevice(volume, mountPointPath, device, usePowerPath);
        String mountedDevice = getMountedFilesystem(mountPointPath);

        if (mountedDevice == null) {
            ExecutionUtils.fail("failTask.verifyVolumeFileSystemMount.noMountFound", mountPointPath, mountPointPath);
        } else if (!StringUtils.equalsIgnoreCase(partitionDevice, mountedDevice)) {
            ExecutionUtils.fail("failTask.verifyVolumeFileSystemMount.devicesDoNotMatch", new Object[] {}, partitionDevice, mountPointPath,
                    mountedDevice);
        }
    }

    public MountPoint findMountPoint(BlockObjectRestRep volume) {
        return LinuxUtils.getMountPoint(targetSystem.getHostId(), getMountPoints(), volume);
    }

    public String getMountedFilesystem(String path) {
        return execute(new GetMountedFilesystem(path));
    }

    public Map<String, MountPoint> getMountPoints() {
        return execute(new ListMountPoints());
    }

    public void findMountPoints(List<VolumeSpec> volumes) {
        execute(new FindMountPointsForVolumes(targetSystem.getHostId(), volumes));
    }

    public void findMultipathEntries(List<VolumeSpec> volumes) {
        execute(new FindMultiPathEntriesForMountPoint(volumes));
    }

    public void findPowerPathDevices(List<VolumeSpec> volumes) {
        execute(new FindPowerPathEntriesForMountPoint(volumes));
    }

    public void formatVolume(String device, String fsType, String blockSize) {
        boolean journaling = FormatVolume.EXT3.equalsIgnoreCase(fsType) || FormatVolume.EXT4.equalsIgnoreCase(fsType);
        execute(new FormatVolume(device, fsType, blockSize, journaling));
    }

    public void createDirectory(String path) {
        execute(new CreateDirectory(path));
        addRollback(new DeleteDirectory(path));
    }

    public void deleteDirectory(String path) {
        execute(new DeleteDirectory(path));
    }

    public boolean isDirectoryEmpty(String path) {
        return execute(new GetDirectoryContents(path)).isEmpty();
    }

    public void addToFSTab(String device, String path, String fsType, String options) {
        execute(new AddToFSTab(device, path, fsType, StringUtils.defaultIfEmpty(options, AddToFSTab.DEFAULT_OPTIONS)));
        addRollback(new RemoveFromFSTab(path));
    }

    public void removeFromFSTab(MountPoint mountPoint) {
        execute(new RemoveFromFSTab(mountPoint.getPath()));
        addRollback(new AddToFSTab(mountPoint.getDevice(), mountPoint.getPath(), mountPoint.getFsType(), mountPoint.getOptions()));
    }

    public void mountPath(String path) {
        execute(new MountPath(path));
        addRollback(new UnmountPath(path));
    }

    public void unmountPath(String path) {
        execute(new UnmountPath(path));
        addRollback(new MountPath(path));
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
    	String tagValue = MachineTagUtils.getBlockVolumeTag(volume, getMountPointTagName());
        ExecutionUtils.execute(new RemoveBlockVolumeMachineTag(volume.getId(), getMountPointTagName()));
        ExecutionUtils.addRollback(new SetBlockVolumeMachineTag(volume.getId(), getMountPointTagName(), tagValue));
        addAffectedResource(volume.getId());
    }

    public void ensureVolumesAreMounted(Collection<VolumeSpec> volumes) {
        for (VolumeSpec volume : volumes) {
            if (LinuxUtils.getMountPoint(targetSystem.getHostId(), getMountPoints(), volume.viprVolume) == null) {
                ExecutionUtils
                        .fail("failTask.LinuxSupport.ensureVolumesAreMounted", volume.viprVolume.getId(), volume.viprVolume.getName());
            }
        }
    }

    public void resizeFileSystem(String device) {
        // Force a check of the file system before and after
        execute(new CheckFileSystem(device, true));
        execute(new ResizeFileSystem(device));
        execute(new CheckFileSystem(device, true));
    }

    /**
     * Get the block size of the given filesystem device
     * 
     * @param device the device to check the partition size
     * @return block size of the device or null if not found
     */
    public String getFilesystemBlockSize(String device) {
        return execute(new GetFilesystemBlockSize(device));
    }

    public <T extends BlockObjectRestRep> String getDevice(T volume, boolean usePowerPath) {
        // TODO : this could probably be implemented using RetryableTask
        try {
            // we will retry this up to 5 times
            int remainingAttempts = 5;
            while (remainingAttempts-- >= 0) {
                try {
                    if (usePowerPath) {
                        return findPowerPathEntry(volume).getDevice();
                    }
                    else {
                        return LinuxUtils.getDeviceForEntry(findMultiPathEntry(volume));
                    }
                } catch (Exception e) {
                    String errorMessage = String.format("Unable to find device for WWN %s. %s more attempts will be made.",
                            volume.getWwn(), remainingAttempts);
                    if (remainingAttempts == 0) {
                        getDeviceFailed(volume, errorMessage, e);
                    }
                    logWarn("linux.support.device.not.found", volume.getWwn(), remainingAttempts);
                    Thread.sleep(10000);
                    refreshStorage(Collections.singleton(volume), usePowerPath);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    protected <T extends BlockObjectRestRep> void getDeviceFailed(T volume, String errorMessage, Exception exception) {
        ExecutionUtils.fail("failTask.getDeviceName", volume.getWwn(), errorMessage, exception.getMessage());
    }

    public void checkForFileSystemCompatibility(String fsType) {
        execute(new CheckForFileSystemCompatibility(fsType));
    }

    /**
     * Method to setup rollbacks for mounting a Linux volume
     * @param volume to be mounted
     * @param mountPoint of the original mount
     */
    public void addMountExpandRollback(BlockObjectRestRep volume, MountPoint mountPoint) {
        ExecutionUtils.addRollback(new SetBlockVolumeMachineTag(volume.getId(), getMountPointTagName(), mountPoint.getPath()));
        addRollback(new MountPath(mountPoint.getPath()));
        addRollback(new AddToFSTab(mountPoint.getDevice(), mountPoint.getPath(), mountPoint.getFsType(), mountPoint.getOptions()));
    }

    public void resizeVolume(BlockObjectRestRep volume, Double newSizeInGB) {
        BlockStorageUtils.expandVolume(volume.getId(), newSizeInGB);
    }

    public void resizePartition(String device) {
        execute(new ResizePartition(device));
        execute(new RescanPartitionMap(device));
    }

    public void resizeMultipathPath(String device) {
        execute(new ResizeMultipathPath(device));
    }

    protected <T> T execute(LinuxExecutionTask<T> task) {
        task.setTargetSystem(targetSystem);

        return ViPRExecutionUtils.execute(task);
    }

    public void addRollback(LinuxExecutionTask<?> rollbackTask) {
        rollbackTask.setTargetSystem(targetSystem);

        ExecutionUtils.addRollback(rollbackTask);
    }

    private String getMountPointTagName() {
        return KnownMachineTags.getHostMountPointTagName(targetSystem.getHostId());
    }

    public String getPrimaryPartitionDevice(BlockObjectRestRep volume, String mountPoint, String device, boolean usePowerPath) {
        if (usePowerPath) {
            String deviceName = StringUtils.substringAfterLast(device, "/");
            List<String> contents = execute(new GetDirectoryContents("/sys/block/" + deviceName));
            for (String content : contents) {
                if (content.startsWith(deviceName)) {
                    logInfo("linux.support.powerpath.name", deviceName);
                    return StringUtils.substringBeforeLast(device, "/") + "/" + content;
                }
            }
            ExecutionUtils.fail("failTask.LinuxSupport.getPrimaryPartitionDevice", device, device);
            return StringUtils.EMPTY; // we will never get here - .fail() will throw an exception
        }
        else {
            String dmname = findMultiPathEntry(volume).getDmName();
            logInfo("linux.support.multipath.name", dmname);
            return execute(new GetPrimaryPartitionDeviceMultiPath(device, dmname));
        }
    }

    public List<String> getBlockDevices(String parentDevice, BlockObjectRestRep volume, boolean usePowerPath) {
        if (usePowerPath) {
            return execute(new GetPowerpathBlockDevices(parentDevice));
        } else {
            String dmname = findMultiPathEntry(volume).getDmName();
            return execute(new GetMultipathBlockDevices(dmname));
        }
    }

    public void rescanBlockDevices(List<String> devices) {
        execute(new RescanBlockDevices(devices));
    }

    public String getParentDevice(String device, boolean usePowerPath) {
        if (usePowerPath) {
            return execute(new GetPowerpathPrimaryPartitionDeviceParent(device));
        }
        else {
            return getParentMultipathDevice(device);
        }
    }

    protected String getParentMultipathDevice(String device) {
        String parentDeviceDmName = execute(new GetMultipathPrimaryPartitionDeviceParentDmName(device));
        MultiPathEntry multipathEntry = execute(new FindMultiPathEntryForDmName(parentDeviceDmName));
        return StringUtils.substringBeforeLast(device, "/") + "/" + multipathEntry.getName();
    }

    public void checkDirectoryDoesNotExist(String path) {
        boolean isEmpty = execute(new GetDirectoryContentsNoFail(path)).isEmpty();

        if (!isEmpty) {
            ExecutionUtils.fail("failTask.LinuxSupport.checkDirectoryDoesNotExist", path, path);
        } else {
            logInfo("linux.support.directory.is.empty", path);
        }
    }

}
