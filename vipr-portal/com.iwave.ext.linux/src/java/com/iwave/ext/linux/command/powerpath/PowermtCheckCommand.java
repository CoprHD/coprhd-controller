/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

public class PowermtCheckCommand extends PowermtCommand {

	public PowermtCheckCommand() {
		super();
		addArgument("check");
		addArgument("force");
	}

}
