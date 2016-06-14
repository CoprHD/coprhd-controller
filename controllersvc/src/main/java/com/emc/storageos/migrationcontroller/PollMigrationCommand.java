package com.emc.storageos.migrationcontroller;

import java.util.regex.Pattern;

import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.text.TextParser;

public class PollMigrationCommand extends LinuxResultsCommand<MigrationInfo> {

    public PollMigrationCommand(String pid) {
        setCommand("kill -USR1");
        addArgument(pid);
    }

    @Override
    public void parseOutput() {
        // TODO Auto-generated method stub
        results = new MigrationInfo();

        if (getOutput() != null && getOutput().getStderr() != null) {
            String stderr = getOutput().getStderr();
            results.setStatus(stderr);
        }
    }
}
