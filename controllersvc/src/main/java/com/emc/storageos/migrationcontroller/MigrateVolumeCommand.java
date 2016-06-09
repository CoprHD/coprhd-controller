package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class MigrateVolumeCommand extends LinuxResultsCommand<String> {

    public MigrateVolumeCommand(String... args) {
        setRunAsRoot(true);
        setCommand("dd");
        addArguments(args);
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
