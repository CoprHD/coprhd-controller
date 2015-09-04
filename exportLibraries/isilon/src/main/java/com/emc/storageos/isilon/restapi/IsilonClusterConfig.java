/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.isilon.restapi;

import com.google.gson.Gson;

import java.util.ArrayList;

public class IsilonClusterConfig {

    private String description;
    private ArrayList<Devices> devices;
    private String encoding;
    private String guid;
    private String join_mode;
    private String local_devid;
    private String local_lnn;
    private String local_serial;
    private String name;
    private OnefsVersion onefs_version;

    public static class Devices {
        private String devid;
        private String guid;
        private String lnn;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ devid: " + devid);
            str.append(", guid: " + guid);
            str.append(", lnn: " + lnn + "]");
            return str.toString();
        }

    }

    public static class OnefsVersion {
        private String build;
        private String release;
        private String revision;
        private String type;
        private String version;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ build: " + build);
            str.append(", release: " + release);
            str.append(", revision: " + revision);
            str.append(", type: " + type);
            str.append(", version: " + version + "]");
            return str.toString();
        }

        public String getReleaseVersionNumber() {
            if (release.startsWith("v")) {
                return release.substring(1, release.length());
            }
            return release;
        }
    };

    public static class TimeZone {
        private String abbreviation;
        private String custom;
        private String name;
        private String path;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ abbreviation: " + abbreviation);
            str.append(", custom: " + custom);
            str.append(", name: " + name);
            str.append(", path: " + path + "]");
            return str.toString();
        }
    };

    public void setVersion(String versionStr) {
        onefs_version = new Gson().fromJson(versionStr, OnefsVersion.class);
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("ClusterInfo ( name: " + name);
        str.append(", guid: " + guid);
        str.append(", description: " + description);
        str.append(", devices: " + ((devices != null) ? devices.toString() : ""));
        str.append(", version: " + ((onefs_version != null) ? onefs_version.toString() : ""));
        return str.toString();
    }

    public String getDescription() {
        return description;
    }

    public ArrayList<Devices> getDevices() {
        return devices;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getGuid() {
        return guid;
    }

    public String getJoin_mode() {
        return join_mode;
    }

    public String getLocal_devid() {
        return local_devid;
    }

    public String getLocal_lnn() {
        return local_lnn;
    }

    public String getLocal_serial() {
        return local_serial;
    }

    public String getName() {
        return name;
    }

    public OnefsVersion getOnefs_version_info() {
        return onefs_version;
    }

}
