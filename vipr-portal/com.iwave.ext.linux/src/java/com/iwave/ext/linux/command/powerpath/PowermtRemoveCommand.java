/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import com.iwave.ext.linux.model.PowerPathDevice;

public class PowermtRemoveCommand extends PowermtCommand {
	
	public PowermtRemoveCommand(PowerPathDevice device) {
		super();
		addArgument("remove");
		addArgument(String.format("dev=%s", device.getDeviceName()));
		addArgument("force");
	}
	
}
