package com.iwave.ext.linux.command.migration;

import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.iwave.ext.linux.command.LinuxResultsCommand;

public class PollMigrationCommand extends LinuxResultsCommand<String> {

    String template;

    public PollMigrationCommand(String ddpid, String in, String out) {
        try {
            template = IOUtils.toString(getClass().getResourceAsStream("pollMigration.sh"));
        } catch (IOException e) {}
        setRunAsRoot(true);
        String cmd = String.format(template, ddpid, in, out);
        setCommand(cmd);
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();
        results = stdout.trim();
    }
}
