package com.emc.storageos.migrationcontroller;

import java.util.regex.Pattern;

import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.text.TextParser;

public class PollMigrationCommand extends LinuxResultsCommand<String> {

    public PollMigrationCommand(String... args) {
        setCommand("/tmp/coprhdMigration/pollMigration.sh");
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
