/**
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

public class IsilonClusterInfo {
    
    public class OnefsVersion {
        private String build;
        private String release;
        private String revision;
        private String type;
        private String version;
        
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ build: " + build);
            str.append(", release: " +  release);
            str.append(", revision: " + revision);
            str.append(", type: " + type);
            str.append(", version: " + version + "]");
            return str.toString();
        }
    };

    public class LogOn {
        private String motd_header;
        private String motd;

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
    
    public void setVersion(String versionStr){
        onefs_version_info = new Gson().fromJson(versionStr, OnefsVersion.class);
    }

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
