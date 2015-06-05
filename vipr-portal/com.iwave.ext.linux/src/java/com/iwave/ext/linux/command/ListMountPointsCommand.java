/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;
import com.iwave.ext.linux.model.MountPoint;

public class ListMountPointsCommand extends LinuxResultsCommand<Map<String, MountPoint>> {

    public ListMountPointsCommand() {
        setCommand("cat");
        addArgument("/etc/fstab");
    }

    @Override
    public void parseOutput() {
        results = Maps.newHashMap();

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
                    if (pieces.length > 2) {
                        mountPoint.setFsType(pieces[2]);
                    }
                    if (pieces.length > 3) {
                        mountPoint.setOptions(pieces[3]);
                    }
                    results.put(mountPoint.getPath(), mountPoint);
                }
            }
        }
    }

}
