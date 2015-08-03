/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

public class PowermtCommand extends LinuxCommand {

    public PowermtCommand() {
        setCommand(CommandConstants.POWERMT);
        setRunAsRoot(true);
    }

}
