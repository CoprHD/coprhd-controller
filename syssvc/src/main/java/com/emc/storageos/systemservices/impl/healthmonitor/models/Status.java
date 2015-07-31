/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
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
