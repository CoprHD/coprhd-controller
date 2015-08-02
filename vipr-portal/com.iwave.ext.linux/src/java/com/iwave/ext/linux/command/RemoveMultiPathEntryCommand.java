/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PathInfo;

public class RemoveMultiPathEntryCommand extends LinuxScriptCommand {
    public RemoveMultiPathEntryCommand(MultiPathEntry entry) {
        addCommandLine("%s -f %s", CommandConstants.MULTIPATH, entry.getName());
        for (PathInfo path : entry.getPaths()) {
            addCommandLine("echo 1 > /sys/block/%s/device/delete", path.getDevice());
        }
        setRunAsRoot(true);
    }
}
