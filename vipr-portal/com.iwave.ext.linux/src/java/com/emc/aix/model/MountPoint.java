/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.model;

import java.util.Date;

import com.emc.aix.command.parse.MultiPosition;
import com.emc.aix.command.parse.Position;
import com.emc.aix.command.parse.TextObject;
import com.emc.aix.format.MountPointDateFormatter;

@TextObject(startLine = 3)
public class MountPoint {

    @Position(2)
    private String device;

    @Position(3)
    private String path;

    @Position(4)
    private String vfs;

    @MultiPosition(value = { 5, 6, 7 }, formatter = MountPointDateFormatter.class)
    private Date date;

    public String getDevice() {
        return device;
    }

    public String getPath() {
        return path;
    }

    public String getVfs() {
        return vfs;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "MountPoint [device=" + device + ", path=" + path + ", vfs="
                + vfs + ", date=" + date + "]\n";
    }

}
