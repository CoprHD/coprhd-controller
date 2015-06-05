/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

import org.apache.commons.lang.StringUtils;

public class MkfsCommand extends LinuxCommand {
    
	public MkfsCommand(String device, String fileSystemType, String blockSize, boolean journaling) {
		setCommand("echo n | "+CommandConstants.MKFS);
		addArguments("-t", fileSystemType);
		if (StringUtils.isNotBlank(blockSize)) {
		    addArguments("-b", blockSize);
		}
		if (journaling) {
			addArgument("-j");
		}
		addArgument(device);
		setRunAsRoot(true);
	}
   
}
