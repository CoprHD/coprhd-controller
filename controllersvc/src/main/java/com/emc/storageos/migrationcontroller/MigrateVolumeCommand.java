package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class MigrateVolumeCommand extends LinuxResultsCommand<String> {

    public MigrateVolumeCommand(String... args) {
        setRunAsRoot(true);
        setCommand("/tmp/coprhdMigration/migrateVolume.sh");
        addArguments(args);
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            results = stdout;
        }
      
    }
}
