/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

public class ListWWNsCommand extends LinuxResultsCommand<Set<String>> {
    private static final Pattern WWN_PATTERN = Pattern.compile("\\b0x([0-9a-fA-F]*)\\b");
    
    public ListWWNsCommand() {
        setCommand("cat");
        addArgument("/sys/class/fc_host/host*/port_name");
    }
    
    @Override
    public void parseOutput() {
        results = Sets.newLinkedHashSet();
        
        Matcher wwnMatcher = WWN_PATTERN.matcher(getOutput().getStdout());
        while (wwnMatcher.find()) {
            String wwn = normalizeWWN(wwnMatcher.group(1));
            results.add(wwn);
        }
    }
    
    private static String normalizeWWN(String unpaddedWWN) {
        // The regex can match less than 16 characters. Pad the first ones with zeros
        String wwn = StringUtils.leftPad(unpaddedWWN, 16, '0');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wwn.length(); i += 2) {
            String slice = StringUtils.substring(wwn, i, i+2);
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(slice);
        }
        return sb.toString();
    }
}
