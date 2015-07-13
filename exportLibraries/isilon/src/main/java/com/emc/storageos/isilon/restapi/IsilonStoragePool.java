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


import java.util.ArrayList;

public class IsilonStoragePool {

    //[{"disk_usage":{"available":15754415955968,"total":16921439059968,"used":94310457344},"entry_id":1,"name":"x200_5.5tb_200gb-ssd_6gb"}]

    private DiskUsage diskUsage;
    private String entryId;
    private String name;

    public class DiskUsage {
        public Long available;
        public Long total;
        public Long used;
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ available: " + available);
            str.append(", total: " +  total);
            str.append(", used: " + used + "]");
            return str.toString();
        }

    };

    public DiskUsage getDiskUsage() {
        return diskUsage;
    }

    public String getEntry_id(){
        return entryId;
    }
    public String getName() {
        return name;
    }

    public Long getAvailable(){
        return getDiskUsage().available;
    }

    public Long getUsed(){
        return getDiskUsage().used;
    }

    public Long getTotal(){
        return getDiskUsage().total;
    }

    public String getNativeId(){
        return getName();
    }
}
