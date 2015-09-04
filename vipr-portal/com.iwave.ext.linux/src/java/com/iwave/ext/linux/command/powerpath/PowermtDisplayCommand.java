/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import org.apache.log4j.Logger;

import com.iwave.ext.command.CommandException;

public class PowermtDisplayCommand extends PowermtCommand {

    Logger log = Logger.getLogger(PowermtDisplayCommand.class);

    public PowermtDisplayCommand() {
        super();
        addArgument("display");
        addArgument("dev=all");
    }

    @Override
    protected void processOutput() throws CommandException {
        log.warn(getOutput().getStdout());
    }

}
