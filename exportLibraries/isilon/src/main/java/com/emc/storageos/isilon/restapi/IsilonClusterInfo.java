/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import com.google.gson.Gson;

public class IsilonClusterInfo {

    public class OnefsVersion {
        private String build;
        private String release;
        private String revision;
        private String type;
        private String version;

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ build: " + build);
            str.append(", release: " + release);
            str.append(", revision: " + revision);
            str.append(", type: " + type);
            str.append(", version: " + version + "]");
            return str.toString();
        }
    };

    public class LogOn {
        private String motd_header;
        private String motd;

        @Override
        public String toString() {
            return "[ motd_header: " + motd_header
                    + ", motd: " + motd + "]";
        }
    };

    private String name;
    private String guid;
    private String description;
    private OnefsVersion onefs_version_info;
    private LogOn logon;

    public void setVersion(String versionStr) {
        onefs_version_info = new Gson().fromJson(versionStr, OnefsVersion.class);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("ClusterInfo ( name: " + name);
        str.append(", guid: " + guid);
        str.append(", description: " + description);
        str.append(", logon: " + logon.toString());
        str.append(", version: " + ((onefs_version_info != null) ? onefs_version_info.toString() : ""));
        return str.toString();
    }

}
