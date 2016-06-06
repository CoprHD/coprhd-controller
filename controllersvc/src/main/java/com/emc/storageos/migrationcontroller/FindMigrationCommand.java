package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class FindMigrationCommand extends LinuxResultsCommand<MigrationInfo> {

    public FindMigrationCommand(String args) {
        setCommand("/usr/bin/findMigration");
        addArgument(args);
    }

    @Override
    public void parseOutput() {
        // TODO Auto-generated method stub

    }
}
