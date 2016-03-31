/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindMultipathBlockDevicesCommand extends LinuxResultsCommand<List<String>> {

    public FindMultipathBlockDevicesCommand(String device) {
        StringBuilder sb = new StringBuilder();
        sb.append("ls -1 -d /sys/block/*/holders/");
        sb.append(device).append(" ");
        sb.append("| grep \"/sys/block\" ");
        sb.append("| sed 's/\\/sys\\/block\\/\\(.*\\)\\/holders\\/.*/\\1/'");
        setCommand(sb.toString());
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        Pattern pattern = Pattern.compile("(\\w+)");
        Matcher matcher = pattern.matcher(getOutput().getStdout());
        this.results = new ArrayList<String>();
        while (matcher.find()) {
            this.results.add(matcher.group(1));
        }
    }
}
