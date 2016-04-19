/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

public class ListIQNsCommand extends AixResultsCommand<Set<String>> {

    private static Pattern IQN_PATTERN = Pattern.compile("initiator_name = \"(.+)\"");

    public ListIQNsCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in `lsdev | grep ^iscsi | cut -d \" \" -f1`; do ");
        sb.append("  lsdevinfo | grep -p $host | grep -E -i -w 'initiator_name'; ");
        sb.append("done; ");
        setCommand(sb.toString());
        setRunAsRoot(true);
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