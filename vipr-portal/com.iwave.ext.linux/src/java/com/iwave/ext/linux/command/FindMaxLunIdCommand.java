/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import com.google.common.collect.Sets;

public class FindMaxLunIdCommand extends LinuxResultsCommand<Integer> {
    private static final String ATTACHED_DEVICES = "Attached devices:";
    private static final Pattern HOST_PATTERN = Pattern.compile("host(\\d+)");
    private static final Pattern SCSI_PATTERN = Pattern
            .compile("Host\\: scsi(\\d+)\\s+Channel\\: (\\d+)\\s+Id: (\\d+)\\s+Lun\\: (\\d+)");

    public FindMaxLunIdCommand() {
        setCommand("ls /sys/class/fc_host ; cat /proc/scsi/scsi");
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();

        String hosts = getHostsBlock(stdout);
        String devices = getDevicesBlock(stdout);

        Set<String> hostIds = getHostIds(hosts);
        results = getMaxLunId(devices, hostIds);
    }

    private String getHostsBlock(String text) {
        return StringUtils.substringBefore(text, ATTACHED_DEVICES);
    }

    private Set<String> getHostIds(String text) {
        Set<String> hostIds = Sets.newHashSet();
        Matcher matcher = HOST_PATTERN.matcher(text);
        while (matcher.find()) {
            hostIds.add(matcher.group(1));
        }
        return hostIds;
    }

    private String getDevicesBlock(String text) {
        return StringUtils.substringAfter(text, ATTACHED_DEVICES);
    }

    private Integer getMaxLunId(String text, Set<String> hostIds) {
        Integer maxLunId = null;
        Matcher matcher = SCSI_PATTERN.matcher(text);
        while (matcher.find()) {
            String hostId = matcher.group(1);
            // String channel = matcher.group(2);
            // String id = matcher.group(3);
            String lun = matcher.group(4);

            if (hostIds.contains(hostId)) {
                int lunId = NumberUtils.toInt(lun, -1);
                if ((maxLunId == null) || (maxLunId < lunId)) {
                    maxLunId = lunId;
                }
            }
        }
        return maxLunId;
    }
}
