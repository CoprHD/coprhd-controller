/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.emc.hpux.model.MountPoint;

public class ListMountPointsCommand extends HpuxResultsCommand<List<MountPoint>> {

    private static Pattern IQN_PATTERN = Pattern.compile("(\\S+)\\s+on\\s+(\\S+)\\s+(\\S+)");

    public ListMountPointsCommand() {
        setCommand("mount");
    }

    @Override
    public void parseOutput() {
        results = new ArrayList<MountPoint>();

        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            Matcher m = IQN_PATTERN.matcher(stdout);
            while (m.find()) {
                MountPoint mountpoint = new MountPoint(m.group(1), m.group(2), m.group(3));
                results.add(mountpoint);
            }
        }
    }

}
