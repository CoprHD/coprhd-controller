package com.emc.storageos.migrationcontroller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.adapter.LinuxHostDiscoveryAdapter;
import com.emc.storageos.db.client.model.Host;
import com.iwave.ext.linux.LinuxSystemCLI;

public class HostMigrationCommand {
    private static final Logger LOG = LoggerFactory.getLogger(HostMigrationCommand.class);
    public HostMigrationCommand() {

    }

    public String hostIscsiConnetTarget(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        IscsiConnectTargetCommand command = new IscsiConnectTargetCommand(args);
        cli.executeCommand(command);
        LOG.info("host conncet to target");
        return command.getResults();
    }

    public String hostIscsiDisconnetTarget(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        IscsiDisconnectTargetCommand command = new IscsiDisconnectTargetCommand(args);
        cli.executeCommand(command);
        LOG.info("host disconncet to target");
        return command.getResults();
    }

    public String hostDoMigration(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        DoMigrationDDCommand command = new DoMigrationDDCommand(args);
        cli.executeCommand(command);
        LOG.info("host do dd migration");
        return command.getResults();
    }
}
