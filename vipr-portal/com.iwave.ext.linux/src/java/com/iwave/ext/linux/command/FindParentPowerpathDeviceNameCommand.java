/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;



public class FindParentPowerpathDeviceNameCommand extends LinuxResultsCommand<String> {
    
    private static final String POWERPATH_NAME_DEV_PATH = "/sys/block/emcpower*/%s/dev";
    private static final String PARENT_DEVICE_SED_SCRIPT = "s:/sys/block/(emcpower[^/]*)/.*:\\1:p";

    public FindParentPowerpathDeviceNameCommand(String partitionDeviceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("echo `echo ").append(String.format(POWERPATH_NAME_DEV_PATH, partitionDeviceName));
        sb.append(" | sed -rn '").append(PARENT_DEVICE_SED_SCRIPT).append("'`");
        setCommand(sb.toString());
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        this.results = getOutput().getStdout();
    }
    
}
