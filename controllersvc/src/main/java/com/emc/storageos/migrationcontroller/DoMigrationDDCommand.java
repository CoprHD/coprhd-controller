package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class DoMigrationDDCommand extends LinuxResultsCommand<String> {

    public DoMigrationDDCommand(String args) {
        setCommand("iscsictl");
        addArgument(args);
    }

    @Override
    public void parseOutput() {
        // TODO Auto-generated method stub
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            // todo parse output   
            
            results = "";
        }
      
    }
}
