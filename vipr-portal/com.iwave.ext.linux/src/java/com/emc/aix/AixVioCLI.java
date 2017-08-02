/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix;

import java.util.List;
import java.util.Set;

import com.emc.aix.command.GetVIONetworkAdapterMacAddressCommand;
import com.emc.aix.command.ListVIOHBAInfoCommand;
import com.emc.aix.command.ListVIOIQNsCommand;
import com.emc.aix.command.version.AixVersionCommand;
import com.emc.aix.command.version.GetAixVioVersionCommand;
import com.emc.aix.model.AixVersion;
import com.iwave.ext.command.Command;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.utility.ssh.ShellCommandExecutor;

public class AixVioCLI extends SecureShellSupport {

    public AixVioCLI(String host, int port, String username, String password) {
        super(host, port, username, password);
    }

    @Override
    public void executeCommand(Command command) {
        executeCommand(command, SecureShellSupport.NO_TIMEOUT);
    }

    public String getNetworkAdapterMacAddress(String adapter) {
        GetVIONetworkAdapterMacAddressCommand cmd = new GetVIONetworkAdapterMacAddressCommand(adapter);
        executeCommand(cmd);
        return cmd.getResults();
    }

    public List<HBAInfo> listInitiators() {
        ListVIOHBAInfoCommand cmd = new ListVIOHBAInfoCommand();
        executeCommand(cmd);
        return cmd.getResults();
    }

    public Set<String> listIQNs() {
        ListVIOIQNsCommand cmd = new ListVIOIQNsCommand();
        executeCommand(cmd);
        return cmd.getResults();
    }

    public AixVersion getVersion() {
        AixVersionCommand version = new GetAixVioVersionCommand();
        executeCommand(version);
        return version.getResults();
    }

    @Override
    public void executeCommand(Command command, int timeout) {
        ShellCommandExecutor executor = new ShellCommandExecutor(this.getHost(), this.getPort(), this.getUsername(), this.getPassword());
        executor.setTimeoutInSeconds(timeout);
        command.setCommandExecutor(executor);
        command.execute();
        executor.disconnect();

    }
}