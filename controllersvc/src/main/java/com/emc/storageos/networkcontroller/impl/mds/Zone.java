/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Zone extends BaseZoneInfo {
    private static final Logger _log = LoggerFactory.getLogger(Zone.class);

    List<ZoneMember> members;

    public Zone(String name) {
        super(name);
    }

    /**
     * marked transient because it cannot be serialized
     */
    transient Object cimObjectPath = null;

    public Object getCimObjectPath() {
        return cimObjectPath;
    }

    public void setCimObjectPath(Object cimObjectPath) {
        this.cimObjectPath = cimObjectPath;
    }

    public List<ZoneMember> getMembers() {
        if (members == null) {
            members = new ArrayList<ZoneMember>();
        }
        return members;
    }

    public void setMembers(List<ZoneMember> members) {
        this.members = members;
    }

    public String getLogString() {
        String str = "zone: " + name + (instanceID != null ? (" (" + instanceID + ") ") : "") + (active ? " active" : "");
        for (ZoneMember member : getMembers()) {
            str += "\n    " + member.getLogString();
        }
        return str;
    }

    public void print() {
        _log.info("zone: " + name + (instanceID != null ? (" (" + instanceID + ") ") : "") + (active ? " active" : ""));
        for (ZoneMember member : getMembers()) {
            member.print();
        }
    }

}
