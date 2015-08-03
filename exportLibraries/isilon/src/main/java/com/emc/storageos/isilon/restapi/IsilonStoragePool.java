/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

@SuppressWarnings({ "squid:S00100" })
/*
 * Isilon API return with json fields has underline.
 */
public class IsilonStoragePool {

    // [{"disk_usage":{"available":15754415955968,"total":16921439059968,"used":94310457344},"entry_id":1,"name":"x200_5.5tb_200gb-ssd_6gb"}]

    private DiskUsage disk_usage;
    private String entry_id;
    private String name;

    public class DiskUsage {
        public Long available;
        public Long total;
        public Long used;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ available: " + available);
            str.append(", total: " + total);
            str.append(", used: " + used + "]");
            return str.toString();
        }

    };

    public DiskUsage getDiskUsage() {
        return disk_usage;
    }

    public String getEntry_id() {
        return entry_id;
    }

    public String getName() {
        return name;
    }

    public Long getAvailable() {
        return getDiskUsage().available;
    }

    public Long getUsed() {
        return getDiskUsage().used;
    }

    public Long getTotal() {
        return getDiskUsage().total;
    }

    public String getNativeId() {
        return getName();
    }
}
