/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class FindParentMultipathDeviceCommand extends LinuxResultsCommand<String> {

    private static final String POWERPATH_NAME_FILE_PATH = "/sys/block/dm-*/holders/dm-*/dm/name";
    private static final String PARENT_DEVICE_REGEX = "s:/sys/block/([^/]*)/.*:\\1:p";

    public FindParentMultipathDeviceCommand(String partitionDeviceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("for partitionDevice in `ls ").append(POWERPATH_NAME_FILE_PATH).append("`; do ");
        sb.append("  if [ `cat $partitionDevice` = \"").append(partitionDeviceName).append("\" ]; then ");
        sb.append("    echo $partitionDevice | sed -rn '").append(PARENT_DEVICE_REGEX).append("';");
        sb.append("  fi;");
        sb.append("done;");
        setCommand(sb.toString());
        // Default sleep time to 5s
    }

    @Override
    public void parseOutput() {
        this.results = getOutput().getStdout();
    }

}
