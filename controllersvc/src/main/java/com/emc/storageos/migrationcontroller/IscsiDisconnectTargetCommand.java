package com.emc.storageos.migrationcontroller;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class IscsiDisconnectTargetCommand extends LinuxResultsCommand<String> {

    public IscsiDisconnectTargetCommand(String args) {
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
