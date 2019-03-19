/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux;

import java.util.List;
import java.util.Set;

import com.emc.aix.SecureShellSupport;
import com.emc.hpux.command.GetHpuxVersionCommand;
import com.emc.hpux.command.GetNetworkAdapterMacAddressCommand;
import com.emc.hpux.command.HpuxVersionCommand;
import com.emc.hpux.command.InsfRescanDevicesCommand;
import com.emc.hpux.command.IoscanRescanDevicesCommand;
import com.emc.hpux.command.ListHBAInfoCommand;
import com.emc.hpux.command.ListIPInterfacesCommand;
import com.emc.hpux.command.ListIQNsCommand;
import com.emc.hpux.command.ListNwInterafacesWithIPCommand;
import com.emc.hpux.model.HpuxVersion;
import com.iwave.ext.command.Command;
import com.iwave.ext.command.HostRescanAdapter;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.utility.ssh.SSHCommandExecutor;

public final class HpuxSystem extends SecureShellSupport implements HostRescanAdapter {

    public HpuxSystem() {
        super();
    }

    public HpuxSystem(String host, int port, String username, String password) {
        super(host, port, username, password);
    }

    public HpuxVersion getVersion() {
        HpuxVersionCommand command = new GetHpuxVersionCommand();
        executeCommand(command);
        return command.getResults();
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

    public Set<String> listNwInterfacesWithIP() {
    	ListNwInterafacesWithIPCommand netIntCmd = new ListNwInterafacesWithIPCommand();
        executeCommand(netIntCmd);
        return netIntCmd.getResults();
    }

    public String getNetworkAdapterMacAddress(String adapter) {
        GetNetworkAdapterMacAddressCommand cmd = new GetNetworkAdapterMacAddressCommand(adapter);
        executeCommand(cmd);
        return cmd.getResults();
    }

    @Override
    public void rescan() {
        executeCommand(new IoscanRescanDevicesCommand(), SecureShellSupport.SHORT_TIMEOUT);
        executeCommand(new InsfRescanDevicesCommand(), SecureShellSupport.SHORT_TIMEOUT);
    }

    @Override
    public void executeCommand(Command command) {
        executeCommand(command, SecureShellSupport.NO_TIMEOUT);
    }

    @Override
    public void executeCommand(Command command, int timeout) {
        SSHCommandExecutor executor = new SSHCommandExecutor(getHost(), getPort(), getUsername(), getPassword());
        executor.setCommandTimeout(timeout);
        executor.setSudoPrefix("export PATH=$PATH:/usr/local/bin; sudo -S -p '' sh -c ");
        command.setCommandExecutor(executor);
        command.execute();
    }
}
