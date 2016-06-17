package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class CancelMigrationCommand extends LinuxResultsCommand<String> {
    public CancelMigrationCommand(String args) {
        setCommand("/usr/bin/cancelmigration");
        addArgument(args);
    }

    @Override
    public void parseOutput() {
        // TODO Auto-generated method stub
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            // todo parse output

            results = stdout;
        }

    }
}
