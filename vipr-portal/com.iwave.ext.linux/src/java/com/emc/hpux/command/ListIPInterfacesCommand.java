/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.ext.text.TextParser;

public class ListIPInterfacesCommand extends HpuxResultsCommand<List<IPInterface>> {
    private static final Pattern BLOCK_PATTERN = Pattern.compile("^|[.]*\n\n");
    private static final Pattern INTERFACE_NAME = Pattern.compile("^([^ ]+):[ ]");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("inet ([^ ]+)");
    private static final Pattern MASK_PATTERN = Pattern.compile("netmask ([^ ]+)");
    private static final Pattern IP6_ADDRESS = Pattern.compile("inet6 ([^ ]+)\n");
    private static final Pattern BROADCAST_ADDRESS_PATTERN = Pattern.compile("broadcast ([^ ]+)\n");

    public ListIPInterfacesCommand() {
        setCommand("for host in `netstat -rn|egrep -v \"Interface|Routing\"|awk '{print $5}'`;do /usr/sbin/ifconfig $host; done");
    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();

        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            TextParser parser = new TextParser();
            parser.setRepeatPattern(BLOCK_PATTERN);

            for (String textBlock : parser.parseTextBlocks(StringUtils.trim(stdout))) {
                IPInterface ipInfo = new IPInterface();

                String interfaceName = parser.findMatch(INTERFACE_NAME, textBlock);
                ipInfo.setInterfaceName(StringUtils.trim(interfaceName));

                String ipAddress = parser.findMatch(ADDRESS_PATTERN, textBlock);
                ipInfo.setIpAddress(StringUtils.trim(ipAddress));

                String netMask = parser.findMatch(MASK_PATTERN, textBlock);
                ipInfo.setNetMask(StringUtils.trim(netMask));

                String ip6Address = parser.findMatch(IP6_ADDRESS, textBlock);
                ipInfo.setIP6Address(StringUtils.trim(ip6Address));

                String broadcastAddress = parser.findMatch(BROADCAST_ADDRESS_PATTERN, textBlock);
                ipInfo.setBroadcastAddress(StringUtils.trim(broadcastAddress));

                if (ipInfo.getIpAddress() != null) {
                    results.add(ipInfo);
                }
            }
        }
    }
}