/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.command.CommandOutput;

public class MultipathException extends CommandException {
    private static final long serialVersionUID = 1470336800328054076L;

    public MultipathException(String message, CommandOutput output) {
        super(message, output);
    }
}
