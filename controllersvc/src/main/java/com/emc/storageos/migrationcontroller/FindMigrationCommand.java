package com.emc.storageos.migrationcontroller;

import java.util.regex.Pattern;

import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.text.TextParser;

public class FindMigrationCommand extends LinuxResultsCommand<MigrationInfo> {

    private static final Pattern STATUS_PATTERN = Pattern
            .compile("status:([^ ]+)");
    private static final Pattern PERCENTA_PATTERN = Pattern
            .compile("percent:([^ ]+)");

    public FindMigrationCommand(String args) {
        setCommand("/usr/bin/findMigration");
        addArgument(args);
    }

    @Override
    public void parseOutput() {
        // TODO Auto-generated method stub
        results = new MigrationInfo();

        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();

            TextParser parser = new TextParser();
            String migrationStatus = parser.findMatch(STATUS_PATTERN, stdout);
            results.setStatus(migrationStatus);

            String percentageDone = parser.findMatch(PERCENTA_PATTERN, stdout);
            results.setPercentageDone(percentageDone);

        }
    }
}
