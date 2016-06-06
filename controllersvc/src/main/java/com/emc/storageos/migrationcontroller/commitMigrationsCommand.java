package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class commitMigrationsCommand extends LinuxResultsCommand<String> {
    public commitMigrationsCommand(String args) {
        setCommand("/usr/bin/commitMigration");
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
