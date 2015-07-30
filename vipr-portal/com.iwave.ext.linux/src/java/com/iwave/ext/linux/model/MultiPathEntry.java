/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;
import java.util.List;

public class MultiPathEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String wwid;
    private String dmName;
    private List<PathInfo> paths;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWwid() {
        return wwid;
    }

    public void setWwid(String wwid) {
        this.wwid = wwid;
    }

    public String getDmName() {
        return dmName;
    }

    public void setDmName(String dmName) {
        this.dmName = dmName;
    }

    public List<PathInfo> getPaths() {
        return paths;
    }

    public void setPaths(List<PathInfo> paths) {
        this.paths = paths;
    }

    public boolean hasFailedPaths() {
        if (paths != null) {
            for (PathInfo path : paths) {
                if (path.isFailed()) {
                    return true;
                }
            }
        }
        return false;
    }

    public String toString() {
        return String.format("%s (%s) %s %s", name, wwid, dmName, paths);
    }
}
