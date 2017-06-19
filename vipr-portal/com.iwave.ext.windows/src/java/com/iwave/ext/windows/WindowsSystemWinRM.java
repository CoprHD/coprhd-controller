/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.wmi.*;
import com.iwave.ext.windows.scsi.DeviceIdentification;
import com.iwave.ext.windows.winrm.WinRMSoapException;
import com.iwave.ext.windows.winrm.wmi.*;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.apache.log4j.Logger;

import com.google.common.collect.Maps;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.command.HostRescanAdapter;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.parser.DiskParser;
import com.iwave.ext.windows.winrm.WinRMException;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.windows.winrm.winrs.WinRS;
public class WindowsSystemWinRM implements HostRescanAdapter {
    private static final String LUN_KEY_FORMAT = "HARDWARE\\DEVICEMAP\\Scsi\\Scsi Port %d\\Scsi Bus %d\\Target Id %d\\Logical Unit Id %d";
    private static final String DEVICE_ID_PAGE = "DeviceIdentifierPage";
    private static final Logger LOG = Logger.getLogger(WindowsSystemWinRM.class);
    private static final int MAX_SCAN_RETRIES = 2;
    private static final int SLEEP_BETWEEN_SCAN_ATTEMPTS = 10;
    private WinRMTarget target;
    private URI hostId;
    private URI clusterId;

    public WindowsSystemWinRM(WinRMTarget target) {
        this.target = target;
    }

    public WindowsSystemWinRM(String host, int port, boolean secure, String username, String password) {
        this.target = new WinRMTarget(host, port, secure, username, password);
    }

    public WinRMTarget getTarget() {
        return target;
    }

    public CommandOutput executeCommand(String commandLine) throws WinRMException {
        WinRS winrs = new WinRS(target);
        debug("Running: %s", commandLine);
        CommandOutput output = winrs.executeCommandLine(commandLine);
        if (output.getExitValue() != 0) {
            debug("Exit Value: %d", output.getExitValue());
        }
        if (StringUtils.isNotBlank(output.getStdout())) {
            debug("STDOUT: \n%s", output.getStdout());
        }
        if (StringUtils.isNotBlank(output.getStderr())) {
            debug("STDERR: \n%s", output.getStderr());
        }
        return output;
    }

    public WindowsVersion getVersion() throws WinRMException {
        List<WindowsVersion> versions = new GetWindowsVersionQuery(target).execute();
        return !versions.isEmpty() ? versions.get(0) : null;
    }

    public String rescanDisks() throws WinRMException {
        String output = "";
        int scanAttempt = 1;
        while (scanAttempt <= MAX_SCAN_RETRIES) {
            try {
                info(String.format("Rescan attempt %s/%s", scanAttempt, MAX_SCAN_RETRIES));
                output = diskPart(WindowsUtils.getRescanCommands());
                break;
            } catch (WinRMException wrme) {
                if (scanAttempt == MAX_SCAN_RETRIES) {
                    throw wrme;
                } else {
                    scanAttempt++;
                    error(String.format("Encountered exception during rescan. "
                            + "Another rescan attempt will be made in %s seconds. Exception: %s", 
                            SLEEP_BETWEEN_SCAN_ATTEMPTS, wrme.getMessage()));
                    try {
                        // Sleep between rescan attempts
                        Thread.sleep(SLEEP_BETWEEN_SCAN_ATTEMPTS * 1000);
                    } catch (InterruptedException e) {
                        throw new WinRMException(e);
                    }
                }
            }
        }
        info("Rescan complete.");
        return output;
    }

    @Override
    public void rescan() throws WinRMException {
        rescanDisks();
    }

    public String formatAndMountDisk(int diskNumber, String fsType, String allocationUnitSize, String label, String mountpoint,
            String partitionType)
            throws WinRMException {
        return diskPart(WindowsUtils
                .getFormatAndMountDiskCommands(diskNumber, fsType, allocationUnitSize, label, mountpoint, partitionType));
    }

    public String mountVolume(int volumeNumber, String mountpoint) throws WinRMException {
        return diskPart(WindowsUtils.getMountVolumeCommands(volumeNumber, mountpoint));
    }

    public String unmountVolume(int volumeNumber, String mountpoint) throws WinRMException {
        return diskPart(WindowsUtils.getUnmountVolumeCommands(volumeNumber, mountpoint));
    }

    public Disk detailDisk(int diskNumber) throws WinRMException {
        String output = diskPart(WindowsUtils.getDetailDiskCommands(diskNumber));
        DiskParser parser = new DiskParser();
        List<Disk> disks = parser.parseDisks(output);
        if (disks.isEmpty()) {
            return null;
        }
        else {
            return disks.get(0);
        }
    }

    public String onlineDisk(int diskNumber, boolean currentReadOnlyState) throws WinRMException {
        return diskPart(WindowsUtils.getOnlineDiskCommands(diskNumber, currentReadOnlyState));
    }

    public String offlineDisk(int diskNumber) throws WinRMException {
        return diskPart(WindowsUtils.getOfflineDiskCommands(diskNumber));
    }

    public String extendVolume(String mountpoint) throws WinRMException {
        return diskPart(WindowsUtils.getExtendVolumeCommands(mountpoint));
    }

    public String listDisk() throws WinRMException {
        return diskPart(WindowsUtils.getListDiskCommands());
    }

    public String diskPart(String... commands) throws WinRMException {
        return diskPart(Arrays.asList(commands));
    }

    public String diskPart(List<String> commands) throws WinRMException {
        StrBuilder sb = new StrBuilder();
        sb.append("(");
        for (int i = 0; i < commands.size(); i++) {
            sb.appendSeparator(" && ", i);
            sb.append("echo ").append(commands.get(i));
        }
        sb.append(" && echo EXIT) | (CHCP 437 & DISKPART)");

        CommandOutput output = executeCommand(sb.toString());
        String error = WindowsUtils.getDiskPartError(output);
        if (StringUtils.isNotBlank(error)) {
            error("DiskPart Error: %s", error);
            throw new WinRMException(String.format("DiskPart Error: %s", error));
        }
        return output.getStdout();
    }

    public void makeDirectory(String directory) throws WinRMException {
        executeCommand("mkdir " + directory);
    }

    public CommandOutput getDirectoryContents(String directory) throws WinRMException {
        return executeCommand("dir " + directory);
    }

    public void deleteDirectory(String directory) throws WinRMException {
        executeCommand("rmdir " + directory + " /s /q");
    }

    public void assignLabel(String disk, String label) throws WinRMException {
        if (StringUtils.isNotBlank(disk)) {
            if (disk.length() == 1 && Character.isLetter(disk.charAt(0))) {
                executeCommand("label " + disk + ": " + label);
            }
            else {
                // Windows mount points reported in diskpart have a trailing slash, but you can't provide it
                // in that form to the label command
                String mountPoint = StringUtils.removeEnd(disk, "\\");
                executeCommand("label /mp " + mountPoint + " " + label);
            }
        }
    }

    public List<FibreChannelHBA> listFibreChannelHBAs() throws WinRMException {
        return new ListFibreChannelHBAsQuery(target).execute();
    }

    public List<NetworkAdapter> listNetworkAdapters() throws WinRMException {
        return new ListNetworkAdaptersQuery(target).execute();
    }

    public List<String> listIScsiInitiators() throws WinRMException {
        return new ListIScsiInitiatorsQuery(target).execute();
    }

    public List<IScsiSession> listIScsiSessions() throws WinRMException {
        return new ListIScsiSessionsQuery(target).execute();
    }

    public List<Win32Service> listServices() throws WinRMException {
        return new ListWin32ServicesQuery(target).execute();
    }

    public List<DiskDrive> listDiskDrives() throws WinRMException {
        return new ListDiskDrivesQuery(target).execute();
    }

    public List<Volume> listVolumes() throws WinRMException {
        return new ListVolumesQuery(target).execute();
    }

    public List<MSCluster> listClusters() throws WinRMException {
        return new ListClustersQuery(target).execute();
    }

    public boolean hasActiveClusters() throws WinRMException {
        try {
            listClusters();

            return true;
        } catch (WinRMSoapException e) {
            if (e.getMessage().equals("There are no more endpoints available from the endpoint mapper.")) {
                return false;
            }
            else {
                throw e;
            }
        }
    }

    public boolean isClustered() throws WinRMException {
        Win32Service clusterService = WindowsClusterUtils.findClusterService(listServices());
        if (clusterService != null) {
            if (clusterService.isStarted()) {
                if (hasActiveClusters()) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<MSClusterNetworkInterface> listClusterNetworkInterfaces() throws WinRMException {
        return new ListClusterNetworkInterfaceQuery(target).execute();
    }

    public List<FibreChannelTargetMapping> getTargetMapping(FibreChannelHBA hba) throws WinRMException {
        return getTargetMapping(hba.getInstanceName(), hba.getPortWWN());
    }

    public List<FibreChannelTargetMapping> getTargetMapping(String instanceName, String portWWN) throws WinRMException {
        return new GetFcpTargetMappingMethod(target, instanceName, portWWN).execute();
    }

    public List<String> getRegistryKeys(String subKey) throws WinRMException {
        return new EnumerateRegistryKeysMethod(target, subKey).execute();
    }

    public List<RegistryValueDef> getRegistryValues(String subKey) throws WinRMException {
        return new EnumerateRegistryValuesMethod(target, subKey).execute();
    }

    public Object getRegistryValue(String subKey, RegistryValueDef valueDef) throws WinRMException {
        return getRegistryValue(subKey, valueDef.getName(), valueDef.getType());
    }

    public Object getRegistryValue(String subKey, String valueName, RegistryValueType valueType) throws WinRMException {
        debug("Getting registry value: %s{%s} [%s]", subKey, valueName, valueType);
        GetRegistryValueMethod method = new GetRegistryValueMethod(target);
        method.setSubKeyName(subKey);
        method.setValueName(valueName);
        method.setValueType(valueType);
        Object result = method.execute();
        debug("%s {%s} = %s", subKey, valueName, result);
        return result;
    }

    public String getRegistryValueAsString(String subKey, String valueName) throws WinRMException {
        return (String) getRegistryValue(subKey, valueName, RegistryValueType.STRING);
    }

    public String getRegistryValueAsExpandedString(String subKey, String valueName) throws WinRMException {
        return (String) getRegistryValue(subKey, valueName, RegistryValueType.EXPANDED_STRING);
    }

    public String[] getRegistryValueAsMultiString(String subKey, String valueName) throws WinRMException {
        return (String[]) getRegistryValue(subKey, valueName, RegistryValueType.MULTI_STRING);
    }

    public byte[] getRegistryValueAsBinary(String subKey, String valueName) throws WinRMException {
        return (byte[]) getRegistryValue(subKey, valueName, RegistryValueType.BINARY);
    }

    public int getRegistryValueAsDWord(String subKey, String valueName) throws WinRMException {
        return (Integer) getRegistryValue(subKey, valueName, RegistryValueType.DWORD);
    }

    public byte[] getDeviceIdentifierPage(DiskDrive disk) throws WinRMException {
        String regKey = String.format(LUN_KEY_FORMAT, disk.getScsiPort(), disk.getScsiBus(), disk.getScsiTarget(),
                disk.getScsiLun());
        return getRegistryValueAsBinary(regKey, DEVICE_ID_PAGE);
    }

    public String getWwid(DiskDrive disk) throws WinRMException {
        byte[] deviceIdentifierPage = getDeviceIdentifierPage(disk);
        if (deviceIdentifierPage != null) {
            try {
                String wwid = DeviceIdentification.getWwid(deviceIdentifierPage);
                debug("Disk: %s, WWID: %s", disk.getDeviceId(), wwid);
                return wwid;
            } catch (IllegalArgumentException e) {
                info("Could not retrieve WWID from Disk: %s", disk.getDeviceId());
                return null;
            }
        }
        else {
            return null;
        }
    }

    public String addDiskToCluster(String diskId) throws WinRMException {
        AddDiskToClusterMethod method = new AddDiskToClusterMethod(target, diskId);
        return method.execute();
    }

    public void deleteClusterResource(String resourceName) throws WinRMException {
        DeleteClusterResourceMethod method = new DeleteClusterResourceMethod(target, resourceName);
        method.execute();
    }

    public void offlineClusterResource(String resourceName) throws WinRMException {
        OfflineClusteredResourceMethod method = new OfflineClusteredResourceMethod(target, resourceName);
        method.execute();
    }

    public Map<String, String> getDiskToClusterResourceMap() throws WinRMException {
        GetDiskToResourceMethod method = new GetDiskToResourceMethod(target);
        List<ResourceToDisk> resourceToDisks = method.execute();

        Map<String, String> diskToResource = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);
        for (ResourceToDisk resourceToDisk : resourceToDisks) {
            diskToResource.put(resourceToDisk.getDiskId(), resourceToDisk.getResourceName());
        }

        return diskToResource;
    }

    public Map<String, List<MSClusterNetworkInterface>> getClusterToNetworkInterfaces() throws WinRMException {
        List<MSClusterToNetworkInterface> clusterToNetworkInterfaces = new ListClusterToNetworkInterfaceQuery(target).execute();
        List<MSClusterNetworkInterface> networkInterfaces = new ListClusterNetworkInterfaceQuery(target).execute();

        Map<String, List<MSClusterNetworkInterface>> clusterNetworkInterfacesMap = Maps.newHashMap();
        for (MSClusterToNetworkInterface networkInterface : clusterToNetworkInterfaces) {
            if (!clusterNetworkInterfacesMap.containsKey(networkInterface.getClusterName())) {
                clusterNetworkInterfacesMap.put(networkInterface.getClusterName(), Lists.<MSClusterNetworkInterface> newArrayList());
            }

            clusterNetworkInterfacesMap.get(networkInterface.getClusterName()).add(
                    findNetworkInterface(networkInterface.getNetworkInterface(), networkInterfaces));
        }

        return clusterNetworkInterfacesMap;
    }

    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    public URI getClusterId() {
        return clusterId;
    }

    public void setClusterId(URI clusterId) {
        this.clusterId = clusterId;
    }

    protected void error(String message, Object... args) {
        if (args.length > 0) {
            LOG.error(String.format(message, args));
        }
        else {
            LOG.error(message);
        }
    }

    protected void info(String message, Object... args) {
        if (LOG.isInfoEnabled()) {
            if (args.length > 0) {
                LOG.info(String.format(message, args));
            }
            else {
                LOG.info(message);
            }
        }
    }

    protected void debug(String message, Object... args) {
        if (LOG.isDebugEnabled()) {
            if (args.length > 0) {
                LOG.debug(String.format(message, args));
            }
            else {
                LOG.debug(message);
            }
        }
    }

    private MSClusterNetworkInterface findNetworkInterface(String interfaceName, List<MSClusterNetworkInterface> networkInterfaces) {
        for (MSClusterNetworkInterface networkInterface : networkInterfaces) {
            if (networkInterface.getName().equals(interfaceName)) {
                return networkInterface;
            }
        }

        throw new IllegalStateException("Cluster Network Interface " + interfaceName + " Not Found");
    }
}
