/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import com.iwave.ext.linux.model.PowerPathDevice;

public class PowermtSetModeStandbyCommand extends PowermtCommand {

    public PowermtSetModeStandbyCommand(PowerPathDevice device) {
        super();
        addArgument("set");
        addArgument("mode=standby");
        addArgument(String.format("dev=%s", device.getDeviceName()));
        addArgument("force");
    }

}
