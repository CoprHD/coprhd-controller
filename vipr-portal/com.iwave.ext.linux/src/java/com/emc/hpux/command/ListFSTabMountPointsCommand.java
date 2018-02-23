/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.hpux.model.MountPoint;

public class ListFSTabMountPointsCommand extends HpuxResultsCommand<List<MountPoint>> {

    public ListFSTabMountPointsCommand() {
        setCommand("cat");
        addArgument("/etc/fstab");
    }

    @Override
    public void parseOutput() {
        results = new ArrayList<MountPoint>();

        if (getOutput() != null && getOutput().getStdout() != null) {
            String[] lines = getOutput().getStdout().split("\n");
            for (String line : lines) {
                String s = StringUtils.substringBefore(line, "#");
                if (StringUtils.isNotBlank(s)) {
                    String[] pieces = StringUtils.trim(s).split("\\s+");

                    MountPoint mountPoint = new MountPoint();
                    mountPoint.setDevice(pieces[0]);
                    if (pieces.length > 1) {
                        mountPoint.setPath(pieces[1]);
                    }
                    results.add(mountPoint);
                }
            }
        }
    }

}