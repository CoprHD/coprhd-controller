/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.command;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;

public class LocalCommandExecutor implements CommandExecutor {
    private String[] environment;
    private File workingDir;
    private String charset = StreamConsumer.DEFAULT_CHARSET;

    public void setEnvironment(String[] environment) {
    	if (environment == null) {
    		this.environment = new String[0];
    	} else {
    		this.environment = Arrays.copyOf(environment, environment.length);
    	}
    }

    public void setWorkingDir(File workingDir) {
        this.workingDir = workingDir;
    }

    @Override
    public CommandOutput executeCommand(Command command) throws CommandException {
        String cli = command.getCommandLine();
        try {
            String[] args = createArgs(cli);
            Process p = Runtime.getRuntime().exec(args, environment, workingDir);
            StreamConsumer stdout = new StreamConsumer(p.getInputStream(), charset);
            StreamConsumer stderr = new StreamConsumer(p.getErrorStream(), charset);

            try {
                int exitCode = p.waitFor();
                return new CommandOutput(stdout.toString(), stderr.toString(), exitCode);
            }
            catch (InterruptedException e) {
                throw new CommandException(e);
            }
            finally {
                stdout.close();
                stderr.close();
            }
        }
        catch (IOException e) {
            throw new CommandException(e);
        }
        catch (RuntimeException e) {
            throw new CommandException(e);
        }
    }

    private String[] createArgs(String commandLine) {
        if (isWindows()) {
            return new String[] { "cmd", "/C", commandLine };
        }
        else {
            return new String[] { "sh", "-c", commandLine };
        }
    }

    private boolean isWindows() {
        return StringUtils.startsWithIgnoreCase(System.getProperty("os.name"), "win");
    }
}
