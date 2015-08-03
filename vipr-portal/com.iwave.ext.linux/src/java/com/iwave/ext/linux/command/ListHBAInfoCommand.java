/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.text.TextParser;

public class ListHBAInfoCommand extends LinuxResultsCommand<List<HBAInfo>> {
    private static final Pattern HOST_PATTERN = Pattern.compile("host: host(\\d+)");
    private static final Pattern WWNN_PATTERN = Pattern.compile("node:\\s*0x([0-9a-fA-F]*)\\b");
    private static final Pattern WWPN_PATTERN = Pattern.compile("port:\\s*0x([0-9a-fA-F]*)\\b");

    public ListHBAInfoCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in `ls /sys/class/fc_host`; do ");
        sb.append("  echo \"host: $host\" ; ");
        sb.append("  echo -n \"node: \" ; cat /sys/class/fc_host/$host/node_name; ");
        sb.append("  echo -n \"port: \" ; cat /sys/class/fc_host/$host/port_name; ");
        sb.append("done; ");
        setCommand(sb.toString());
    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            TextParser parser = new TextParser();
            parser.setRepeatPattern(HOST_PATTERN);

            for (String textBlock : parser.parseTextBlocks(stdout)) {
                String host = parser.findMatch(HOST_PATTERN, textBlock);
                if (StringUtils.isNotBlank(host)) {
                    HBAInfo hba = new HBAInfo();
                    hba.setHostId(Integer.parseInt(host));

                    String wwnn = parser.findMatch(WWNN_PATTERN, textBlock);
                    hba.setWwnn(normalizeWWN(wwnn));

                    String wwpn = parser.findMatch(WWPN_PATTERN, textBlock);
                    hba.setWwpn(normalizeWWN(wwpn));
                    results.add(hba);
                }
            }
        }
    }

    private String normalizeWWN(String wwn) {
        wwn = StringUtils.trim(wwn);
        wwn = StringUtils.leftPad(wwn, 16, '0');
        wwn = StringUtils.lowerCase(wwn);
        return wwn;
    }
}
