/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix;

import java.util.List;
import java.util.Set;

import com.emc.aix.command.GetNetworkAdapterMacAddressCommand;
import com.emc.aix.command.ListHBAInfoCommand;
import com.emc.aix.command.ListIPInterfacesCommand;
import com.emc.aix.command.ListIQNsCommand;
import com.emc.aix.command.MakeFilesystemCommand;
import com.emc.aix.command.RescanDevicesCommand;
import com.emc.aix.command.version.AixVersionCommand;
import com.emc.aix.command.version.GetAixVersionCommand;
import com.emc.aix.model.AixVersion;
import com.iwave.ext.command.Command;
import com.iwave.ext.command.HostRescanAdapter;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.utility.ssh.SSHCommandExecutor;

public final class AixSystem extends SecureShellSupport implements HostRescanAdapter {

    public AixSystem() {
        super();
    }

    public AixSystem(String host, int port, String username, String password) {
        super(host, port, username, password);
    }

    public AixVersion getVersion() {
        AixVersionCommand command = new GetAixVersionCommand();
        executeCommand(command);
        return command.getResults();
    }

    public void makeFilesystem(String hdisk, String fsType) {
        executeCommand(new MakeFilesystemCommand(hdisk, fsType));
    }

    public List<HBAInfo> listInitiators() {
        ListHBAInfoCommand command = new ListHBAInfoCommand();
        executeCommand(command);
        return command.getResults();
    }

    public List<IPInterface> listIPInterfaces() {
        ListIPInterfacesCommand command = new ListIPInterfacesCommand();
        executeCommand(command);
        return command.getResults();
    }

    public Set<String> listIQNs() {
        ListIQNsCommand iqnCmd = new ListIQNsCommand();
        executeCommand(iqnCmd);
        return iqnCmd.getResults();
    }

    public String getNetworkAdapterMacAddress(String adapter) {
        GetNetworkAdapterMacAddressCommand cmd = new GetNetworkAdapterMacAddressCommand(adapter);
        executeCommand(cmd);
        return cmd.getResults();
    }

    @Override
    public void rescan() {
        RescanDevicesCommand command = new RescanDevicesCommand();
        executeCommand(command, SecureShellSupport.SHORT_TIMEOUT);
    }

    @Override
    public void executeCommand(Command command, int timeout) {
        SSHCommandExecutor executor = new SSHCommandExecutor(getHost(), getPort(), getUsername(), getPassword());
        executor.setCommandTimeout(timeout);
        command.setCommandExecutor(executor);
        command.execute();
    }

    @Override
    public void executeCommand(Command command) {
        executeCommand(command, SecureShellSupport.NO_TIMEOUT);
    }
}
