/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor.models;

/**
 * Enumeration class that holds various health status that are allowed for a node or
 * service.
 */
public enum Status {
    GOOD("Good"),
    DEGRADED("Degraded"),
    RESTARTED("Restarted"),
    UNAVAILABLE("Unavailable"),
    NODE_OR_SYSSVC_UNAVAILABLE("Node/syssvc Unavailable");

    private String _name;

    Status(String name) {
        _name = name;
    }

    @Override
    public String toString() {
        return _name;
    }
}
