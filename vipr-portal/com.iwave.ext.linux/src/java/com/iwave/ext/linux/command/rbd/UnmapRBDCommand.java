/*
 * Copyright (c) 2012-2015 EMC Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.rbd;

import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.iwave.ext.linux.command.LinuxCommand;

public class UnmapRBDCommand extends LinuxCommand {
    private String _template;

    public UnmapRBDCommand() {
        try {
            _template = IOUtils.toString(getClass().getResourceAsStream("unmap.sh"));
        } catch (IOException e) {
        }
        setRunAsRoot(true);
    }

    public void setVolume(String pool, String volume, String snapshot) {
        String snap = snapshot;
        if (snap == null || snap.isEmpty()) {
            snap = "-";
        }
        String cmd = String.format(_template, pool, volume, snap);
        setCommand(cmd);
    }
}
