/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys.ipreconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Network Reconfiguration Status
 */
@XmlRootElement(name = "ipreconfig_status")
public class ClusterNetworkReconfigStatus {
    private static final long IPRECONFIG_SUCCEEDSTATUS_DISPLAY_TIMEOUT = 24 * 60 * 60 * 1000; // Keep "Succeed" status for 1 day in GUI
    /**
     * The status of ip reconfig
     */
    @XmlType(name="clusterNetworkReconfigStatus_Status")
    public enum Status {
        STARTED("STARTED"),
        SUCCEED("SUCCEED"),
        FAILED("FAILED");

        private String name;
        private Status(String name) { this.name = name; }
        public String toString() {return name;}
    }

    private Status status;
    private String message;
    private String expiration;

    @XmlElement(name = "status")
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlElement(name = "message")
    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement(name = "expiration")
    public String getExpiration() {
        return this.expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public boolean isRecentlyReconfigured() {
    	if (expiration == null) {
    		return false;
    	}
        long expiration_time = Long.valueOf(expiration);
        if(System.currentTimeMillis() >= expiration_time + IPRECONFIG_SUCCEEDSTATUS_DISPLAY_TIMEOUT) {
            return false;
        }
        return true;
    }
}
