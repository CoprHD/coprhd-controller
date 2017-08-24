/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    public static enum HostLUNAccessEnum {
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
