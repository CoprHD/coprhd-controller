/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import org.apache.commons.lang.text.StrBuilder;

import com.iwave.ext.command.CommandException;

public class LinuxScriptCommand extends LinuxCommand {
    private StrBuilder script = new StrBuilder();

    public LinuxScriptCommand() {
    }

    public void addCommandLine(String commandLine, Object... args) {
        script.appendSeparator(" ;\n");
        script.append(String.format(commandLine, args));
    }

    @Override
    protected void validateCommandLine() throws CommandException {
        setCommand(script.toString());
        super.validateCommandLine();
    }
}
