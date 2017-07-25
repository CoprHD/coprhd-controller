/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class FindPowerpathBlockDevicesCommand extends LinuxResultsCommand<List<String>> {

    private String powerPathDevice;

    public FindPowerpathBlockDevicesCommand(String powerPathDevice) {
        this.powerPathDevice = powerPathDevice;
        StringBuilder sb = new StringBuilder();
        sb.append("pp_inq -parent -no_dots | grep -v PARENT | grep ").append(powerPathDevice);
        setCommand(sb.toString());
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        Pattern pattern = Pattern.compile("(\\w+)\\s.*");
        Matcher matcher = pattern.matcher(getOutput().getStdout());
        this.results = new ArrayList<String>();
        while (matcher.find()) {
            String blockDevice = matcher.group(1);
            if (!StringUtils.equalsIgnoreCase(this.powerPathDevice, blockDevice)) {
                this.results.add(blockDevice);
            }
        }
    }

}
