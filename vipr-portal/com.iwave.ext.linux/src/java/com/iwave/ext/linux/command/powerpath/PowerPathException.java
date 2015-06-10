/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.command.CommandOutput;

public class PowerPathException extends CommandException {
    private static final long serialVersionUID = -4149408393935211655L;

    public PowerPathException(String message, CommandOutput output) {
        super(message);
    }
}
