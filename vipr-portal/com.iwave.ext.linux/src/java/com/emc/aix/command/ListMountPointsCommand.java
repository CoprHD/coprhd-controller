/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import java.util.List;
import java.util.Map;

import com.emc.aix.command.parse.TextOutputUnmarshaller;
import com.emc.aix.model.MountPoint;
import com.google.common.collect.Maps;

public class ListMountPointsCommand extends AixResultsCommand<Map<String, MountPoint>> {

    public ListMountPointsCommand() {
        setCommand("mount");
    }

    @Override
    public void parseOutput() {
        results = Maps.newHashMap();
        if (getOutput() != null && getOutput().getStdout() != null) {

            String stdout = getOutput().getStdout();

            TextOutputUnmarshaller parser = TextOutputUnmarshaller.instance();

            List<MountPoint> mountPoints = parser.with(stdout).parse(MountPoint.class);

            for (MountPoint m : mountPoints) {
                results.put(m.getPath(), m);
            }
        }
    }

}
