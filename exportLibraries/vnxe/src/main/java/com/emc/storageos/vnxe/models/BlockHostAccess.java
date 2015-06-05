/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

public class BlockHostAccess {
    private VNXeBase host;
    private int accessMask;
    
    public VNXeBase getHost() {
        return host;
    }

    public void setHost(VNXeBase host) {
        this.host = host;
    }

    public int getAccessMask() {
        return accessMask;
    }

    public void setAccessMask(int accessMask) {
        this.accessMask = accessMask;
    }

    public  static enum HostLUNAccessEnum {
        NOACCESS(0),
        PRODUCTION(1),
        SNAPSHOT(2),
        BOTH(3);
        
        private int value;
        private HostLUNAccessEnum(int value) {
            this.value = value;
        }
        public int getValue() {
            return value;
        }
    }
}
