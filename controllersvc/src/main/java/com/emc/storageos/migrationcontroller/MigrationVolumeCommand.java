package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class MigrationVolumeCommand extends LinuxResultsCommand<String> {

    public MigrationVolumeCommand(String args) {
        setCommand("/usr/bin/migrationVolume");
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
