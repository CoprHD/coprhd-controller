/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Maps;
import com.iwave.ext.linux.command.LinuxResultsCommand;

public class GetDeviceLunMappingCommand extends LinuxResultsCommand<Map<String, Integer>> {
    private static final Pattern DEVICE_PATTERN = Pattern
            .compile("- \\d+:\\d+:\\d+:(\\d+)\\s+(\\w+) ");
    private static final String MPATH_NAME = "mpathName";

    public GetDeviceLunMappingCommand() {
        setCommand(CommandConstants.MULTIPATH);
        addArgument("-ll").addVariable(MPATH_NAME);
        setRunAsRoot(true);
    }

    public void setMpathName(String name) {
        setVariableValue(MPATH_NAME, name);
    }

    @Override
    public void parseOutput() {
        results = Maps.newHashMap();

        Matcher m = DEVICE_PATTERN.matcher(getOutput().getStdout());
        while (m.find()) {
            Integer lunId = Integer.parseInt(m.group(1));
            String device = m.group(2);

            results.put(device, lunId);
        }
    }
}
