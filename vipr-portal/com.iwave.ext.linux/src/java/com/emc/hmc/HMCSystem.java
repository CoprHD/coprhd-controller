/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.hmc;

import java.util.List;
import java.util.Set;

import com.emc.aix.SecureShellSupport;
import com.emc.aix.command.GetNetworkAdapterMacAddressCommand;
import com.emc.aix.command.ListHBAInfoCommand;
import com.emc.aix.command.ListIPInterfacesCommand;
import com.emc.aix.command.ListIQNsCommand;
import com.emc.aix.command.MakeFilesystemCommand;
import com.emc.hmc.command.GetHMCVersionCommand;
import com.emc.hmc.command.HMCVersionCommand;
import com.emc.hmc.model.HMCVersion;
import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandExecutor;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.utility.ssh.SSHCommandExecutor;

public final class HMCSystem extends SecureShellSupport {

    public HMCSystem() {
        super();
    }

    public HMCSystem(String host, int port, String username, String password) {
        super(host, port, username, password);
    }

    public HMCVersion getVersion() {
        HMCVersionCommand command = new GetHMCVersionCommand();
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
    public void executeCommand(Command command) {
        CommandExecutor executor = new SSHCommandExecutor(getHost(), getPort(), getUsername(), getPassword());
        command.setCommandExecutor(executor);
        command.execute();
    }
}
