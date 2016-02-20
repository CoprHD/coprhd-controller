/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.command.AddToFSTabCommand;
import com.iwave.ext.linux.command.FindMaxLunIdCommand;
import com.iwave.ext.linux.command.FindMountPointCommand;
import com.iwave.ext.linux.command.GetDeviceLunMappingCommand;
import com.iwave.ext.linux.command.GetMachineIdCommand;
import com.iwave.ext.linux.command.LinuxCommand;
import com.iwave.ext.linux.command.ListHBAInfoCommand;
import com.iwave.ext.linux.command.ListIPInterfacesCommand;
import com.iwave.ext.linux.command.ListMPathNamesCommand;
import com.iwave.ext.linux.command.ListMountPointsCommand;
import com.iwave.ext.linux.command.ListMultiPathEntriesCommand;
import com.iwave.ext.linux.command.ListWWNsCommand;
import com.iwave.ext.linux.command.MkdirCommand;
import com.iwave.ext.linux.command.Mke2fsCommand;
import com.iwave.ext.linux.command.MountCommand;
import com.iwave.ext.linux.command.RescanDevicesCommand;
import com.iwave.ext.linux.command.fdisk.FdiskListCommand;
import com.iwave.ext.linux.command.iscsi.ListIQNsCommand;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.ext.linux.model.MountPoint;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.utility.ssh.SSHCommandExecutor;

/**
 * Linux host CLI. Many of these operations require root access.
 * 
 * @author Chris Dail
 */
public class LinuxSystemCLI {
    /** The SSH host address. */
    private String host;
    /** The SSH port (defaults to 22). */
    private int port = 22;
    /** The SSH username. */
    private String username;
    /** The SSH password. */
    private String password;
    /** The ID of the host to which this CLI connects */
    private URI hostId;

    public LinuxSystemCLI() {
    }

    public LinuxSystemCLI(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public LinuxSystemCLI(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    public void executeCommand(Command command) {
        SSHCommandExecutor executor = new SSHCommandExecutor(host, port, username, password);
        command.setCommandExecutor(executor);
        command.execute();
    }

    public Set<String> getAllDiskDevices() {
        FdiskListCommand command = new FdiskListCommand();
        executeCommand(command);
        return command.getResults();
    }

    public Set<String> getNonMappedDiskDevices() {
        FdiskListCommand command = new FdiskListCommand();
        command.setIncludeMapper(false);
        command.setIncludeRegular(true);
        executeCommand(command);
        return command.getResults();
    }

    public Set<String> getMappedDiskDevices() {
        FdiskListCommand command = new FdiskListCommand();
        command.setIncludeMapper(true);
        command.setIncludeRegular(false);
        executeCommand(command);
        return command.getResults();
    }

    /**
     * Given a name modify it if necessary to make it unique within a list of names
     * 
     * @param name to make unique
     * @param names list of existing names
     * @return a name that will be unique within list of names
     */
    static String createUniqueName(String name, List<String> names) {
        String result = name;
        if (name != null && names != null && !names.isEmpty()) {
            boolean found = false;
            for (String currentName : names) {
                if (name.equals(currentName)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                result = createUniqueName(name + "1", names);
            }
        }
        return result;
    }

    public void formatExt4(String device) {
        Mke2fsCommand command = new Mke2fsCommand();
        command.setDevice(device);
        command.setJournaling();
        command.setType("ext4");
        executeCommand(command);
    }

    public void mkdir(String dir) {
        MkdirCommand command = new MkdirCommand(true);
        command.setDir(dir);
        executeCommand(command);
    }

    public void addFSTabMount(String device, String mountPt, String fsType) {
        AddToFSTabCommand command = new AddToFSTabCommand();
        command.setOptions(device, mountPt, fsType);
        executeCommand(command);
    }

    public void mountAll() {
        MountCommand command = new MountCommand();
        command.setMountAll();
        executeCommand(command);
    }

    public Set<String> listWWNs() {
        ListWWNsCommand command = new ListWWNsCommand();
        executeCommand(command);
        return command.getResults();
    }

    public List<HBAInfo> listHBAs() {
        ListHBAInfoCommand command = new ListHBAInfoCommand();
        executeCommand(command);
        return command.getResults();
    }

    public List<IPInterface> listIPInterfaces() {
        ListIPInterfacesCommand command = new ListIPInterfacesCommand();
        executeCommand(command);
        return command.getResults();
    }

    public List<MultiPathEntry> listMultiPathEntries() {
        ListMultiPathEntriesCommand command = new ListMultiPathEntriesCommand();
        executeCommand(command);
        return command.getResults();
    }

    public void rescanDevices() {
        RescanDevicesCommand command = new RescanDevicesCommand();
        executeCommand(command);
    }

    public Map<String, Integer> getDeviceToLunMapping(String mpathName) {
        GetDeviceLunMappingCommand command = new GetDeviceLunMappingCommand();
        command.setMpathName(mpathName);
        executeCommand(command);
        return command.getResults();
    }

    public String findMountPoint(String device) {
        FindMountPointCommand command = new FindMountPointCommand();
        command.setDevice(device);
        executeCommand(command);
        return command.getResults();
    }

    public List<MountPoint> listMountPoints() {
        ListMountPointsCommand command = new ListMountPointsCommand();
        executeCommand(command);
        return Lists.newArrayList(command.getResults().values());
    }

    public List<String> listMPaths() {
        ListMPathNamesCommand command = new ListMPathNamesCommand();
        executeCommand(command);
        return command.getResults();
    }

    public Integer findMaxLunId() {
        FindMaxLunIdCommand command = new FindMaxLunIdCommand();
        executeCommand(command);
        return command.getResults();
    }

    public Set<String> listIQNs() {
        ListIQNsCommand command = new ListIQNsCommand();
        executeCommand(command);
        return command.getResults();
    }

    public String getMachineId() {
        GetMachineIdCommand command = new GetMachineIdCommand();
        executeCommand(command);
        return command.getResults();
    }

    public CommandOutput executeCommand(String commandString) {
        LinuxCommand command = new LinuxCommand();
        command.setCommand(commandString);
        executeCommand(command);
        return command.getOutput();
    }
}
