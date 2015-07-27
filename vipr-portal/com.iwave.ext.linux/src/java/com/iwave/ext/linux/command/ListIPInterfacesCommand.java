/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.ext.text.TextParser;

public class ListIPInterfacesCommand extends LinuxResultsCommand<List<IPInterface>> {
    private static final Pattern BLOCK_PATTERN = Pattern.compile("^|[.]*\n\n");
    private static final Pattern INTERFACE_NAME = Pattern.compile("^([^ ]+)[ ]");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("addr:([^ ]+)");
    private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("HWaddr ([^ ]+)");
    private static final Pattern MASK_PATTERN = Pattern.compile("Mask:([^ ]+)");   
    private static final Pattern IP6_ADDRESS = Pattern.compile("inet6 addr: ([^ ]+)");
    private static final Pattern BROADCAST_ADDRESS_PATTERN = Pattern.compile("Bcast:([^ ]+)");
    
    public ListIPInterfacesCommand() {
        setCommand(CommandConstants.IFCONFIG);
        setRunAsRoot(true);
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
                
                String macAddress = parser.findMatch(MAC_ADDRESS_PATTERN, textBlock);
                ipInfo.setMacAddress(StringUtils.trim(macAddress));
                
                String netMask = parser.findMatch(MASK_PATTERN, textBlock);
                ipInfo.setNetMask(StringUtils.trim(netMask));
                
                String ip6Address = parser.findMatch(IP6_ADDRESS, textBlock);
                ipInfo.setIP6Address(StringUtils.trim(ip6Address));

                String broadcastAddress = parser.findMatch(BROADCAST_ADDRESS_PATTERN, textBlock);
                ipInfo.setBroadcastAddress(StringUtils.trim(broadcastAddress));
                
                results.add(ipInfo);
            }
        }
    }
}
