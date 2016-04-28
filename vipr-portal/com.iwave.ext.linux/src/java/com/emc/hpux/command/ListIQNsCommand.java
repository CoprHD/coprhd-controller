/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

public class ListIQNsCommand extends HpuxResultsCommand<Set<String>> {

    private static Pattern IQN_PATTERN = Pattern.compile("Initiator Name\\s+:\\s+(.+)");

    public ListIQNsCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("/opt/iscsi/bin/iscsiutil -l");
        setCommand(sb.toString());
    }

    @Override
    public void parseOutput() {
        results = Sets.newHashSet();

        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            Matcher m = IQN_PATTERN.matcher(stdout);
            while (m.find()) {
                results.add(m.group(1));
            }
        }
    }
}