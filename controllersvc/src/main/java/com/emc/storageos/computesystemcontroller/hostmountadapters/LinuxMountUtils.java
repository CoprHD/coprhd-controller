/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.command.AddToFSTabCommand;
import com.iwave.ext.linux.command.DeleteDirectoryCommand;
import com.iwave.ext.linux.command.ListManualMountsCommand;
import com.iwave.ext.linux.command.ListMountPointsCommand;
import com.iwave.ext.linux.command.LsOnMountPointCommand;
import com.iwave.ext.linux.command.MkdirCommand;
import com.iwave.ext.linux.command.MountCommand;
import com.iwave.ext.linux.command.RemoveFromFSTabCommand;
import com.iwave.ext.linux.command.UnmountCommand;
import com.iwave.ext.linux.model.MountPoint;

/**
 * 
 * @author yelkaa
 * 
 */
public class LinuxMountUtils {
    private static final Log _log = LogFactory.getLog(LinuxMountUtils.class);

    private static final int STD_TIMEOUT = 300;

    private LinuxSystemCLI cli;
    private Host host;

    public LinuxMountUtils(Host host) {
        cli = createLinuxCLI(host);
        this.host = host;
    }

    public LinuxMountUtils() {

    }

    public LinuxSystemCLI getCli() {
        return cli;
    }

    public void setCli(LinuxSystemCLI cli) {
        this.cli = cli;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public void mountPath(String path) throws InternalException {
        MountCommand command = new MountCommand(STD_TIMEOUT);
        command.setPath(path);
        command.addArgument("; if [ $? -eq 124 ] ; then >&2 echo TIMEOUT ; else >&2 echo SUCCESSFUL ; fi");
        _log.info("mount command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
        CommandOutput output = command.getOutput();
        if (output.getStderr().contains("TIMEOUT")) {
            ComputeSystemControllerException exception = ComputeSystemControllerException.exceptions.commandTimedOut(host.getHostName());
            throw exception;
        } else {
            String errMessage = output.getStderr().replace("SUCCESSFUL", "").replace("\n", "");
            if (!errMessage.isEmpty()) {
                ComputeSystemControllerException exception = ComputeSystemControllerException.exceptions.unableToMount(host.getHostName(),
                        new Exception(errMessage));
                throw exception;
            }
        }
    }

    public void createDirectory(String path) throws InternalException {
        MkdirCommand command = new MkdirCommand(true);
        command.setDir(path);
        _log.info("Create dir command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
    }

    public void addToFSTab(String path, String device, String fsType, String options) throws InternalException {
        AddToFSTabCommand command = new AddToFSTabCommand();
        command.setOptions(device, path, fsType, options);
        _log.info("add to fstab command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
    }

    public void deleteDirectory(String directory) throws InternalException {
        DeleteDirectoryCommand command = new DeleteDirectoryCommand(directory, true);
        _log.info("delete dir command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
    }

    public Boolean isDirectoryEmpty(String directory) throws InternalException {
        String command = "ls " + directory;
        CommandOutput output = cli.executeCommand(command);
        if (StringUtils.isBlank(output.getStdout())) {
            return Collections.emptyList().isEmpty();
        } else {
            return Lists.newArrayList(output.getStdout().split("\n")).isEmpty();
        }
    }

    public Boolean isDirectoryExists(String directory) throws InternalException {
        String command = "[ -d \"" + directory + "\" ] &&echo \"exists\"||echo \"not exists\" ";
        CommandOutput output = cli.executeCommand(command);
        if ("exists".equalsIgnoreCase(output.getStdout())) {
            return true;
        }
        return false;
    }

    public void removeFromFSTab(String path) throws InternalException {
        RemoveFromFSTabCommand command = new RemoveFromFSTabCommand();
        command.setMountPoint(path);
        _log.info("remove from fstab command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
    }

    public void unmountPath(String path) throws InternalException {
        UnmountCommand command = new UnmountCommand(STD_TIMEOUT);
        command.setPath(path);
        command.addArgument("; if [ $? -eq 124 ] ; then >&2 echo TIMEOUT ; else >&2 echo SUCCESSFUL ; fi");
        _log.info("unmount command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
        CommandOutput output = command.getOutput();
        if (output.getStderr().contains("TIMEOUT")) {
            ComputeSystemControllerException exception = ComputeSystemControllerException.exceptions.commandTimedOut(host.getHostName());
            throw exception;
        } else {
            String errMessage = output.getStderr().replace("SUCCESSFUL", "").replace("\n", "");
            if (!errMessage.isEmpty()) {
                ComputeSystemControllerException exception = ComputeSystemControllerException.exceptions.unableToUnmount(host.getHostName(),
                        new Exception(errMessage));
                throw exception;
            }
        }
    }

    public void verifyMountPoint(String mountPoint) throws InternalException {
        if (!StringUtils.startsWith(mountPoint, "/")) {
            throw new IllegalStateException("Mount Point not absolute: " + mountPoint);
        }
        checkExistingMountPoints(mountPoint);
        checkLsOnMountPoint(mountPoint);
        checkManualMounts(mountPoint);
    }

    protected void checkExistingMountPoints(String mountPoint) throws InternalException {
        ListMountPointsCommand command = new ListMountPointsCommand();
        _log.info("check existing command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
        Map<String, MountPoint> mountPoints = command.getResults();
        for (MountPoint mp : mountPoints.values()) {
            if (StringUtils.equals(mp.getPath(), mountPoint)) {
                throw new IllegalStateException("Mount point already exists: " + mountPoint);
            }
        }
    }

    /**
     * Method to check files on the given mount point
     * 
     * @param mountPoint
     * @throws InternalException
     */
    protected void checkLsOnMountPoint(String mountPoint) throws InternalException {
        LsOnMountPointCommand command = new LsOnMountPointCommand(mountPoint);
        _log.info("Ls on Mount Point command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
        List<String> mountPointContents = command.getResults();
        if (!mountPointContents.isEmpty()) {
            throw new IllegalStateException("Mount point contains files or folders: " + mountPoint);
        }
    }

    /**
     * Method to check if any manual mount is present already on the host
     * 
     * @param mountPoint
     * @throws InternalException
     */
    protected void checkManualMounts(String mountPoint) throws InternalException {

        ListManualMountsCommand command = new ListManualMountsCommand(mountPoint);
        _log.info("Check manual Mount Point command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
        Map<String, String> manualMounts = command.getResults();
        for (String mountPath : manualMounts.values()) {
            if (StringUtils.equals(mountPath, mountPoint)) {
                throw new IllegalStateException("Mount point already exists (Manual mount): " + mountPoint);
            }
        }
    }

    public boolean verifyMountPoints(String mountPoint, String mountPath) throws InternalException {
        ListMountPointsCommand command = new ListMountPointsCommand();
        _log.info("check existing command:" + command.getResolvedCommandLine());
        cli.executeCommand(command);
        Map<String, MountPoint> mountPoints = command.getResults();
        for (MountPoint mp : mountPoints.values()) {
            if (StringUtils.equals(mp.getDevice(), mountPoint) && StringUtils.equals(mp.getPath(), mountPath)) {
                return true;
            }
        }
        return false;
    }

    public static LinuxSystemCLI convertHost(Host host) {
        LinuxSystemCLI cli = new LinuxSystemCLI();
        cli.setHost(host.getHostName());
        cli.setPort(host.getPortNumber());
        cli.setUsername(host.getUsername());
        cli.setPassword(host.getPassword());
        cli.setHostId(host.getId());
        return cli;
    }

    public static LinuxSystemCLI createLinuxCLI(Host host) {
        if ((host.getPortNumber() != null) && (host.getPortNumber() > 0)) {
            return new LinuxSystemCLI(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else {
            return new LinuxSystemCLI(host.getHostName(), host.getUsername(), host.getPassword());
        }
    }

    public String generateMountTag(URI hostId, String mountPath, String subDirectory, String securityType) {
        return "mountNFS;" + hostId.toString() + ";" + mountPath + ";" + subDirectory + ";" + securityType;
    }
}
