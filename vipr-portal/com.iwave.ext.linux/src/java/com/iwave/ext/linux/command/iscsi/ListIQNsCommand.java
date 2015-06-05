/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.iscsi;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;
import com.iwave.ext.linux.command.LinuxResultsCommand;

public class ListIQNsCommand extends LinuxResultsCommand<Set<String>> {

    private static Pattern IQN_PATTERN = Pattern.compile("InitiatorName=(.*)");

    public ListIQNsCommand() {
        setRunAsRoot(true);
        setCommand("find /etc -name \"*iscsi*\" -type f -exec grep \"InitiatorName=\" \\{\\} \\;");
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
