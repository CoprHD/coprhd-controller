/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.common.impl;

/**
 * Enumeration of well known paths for coordinator cluster
 */
public enum ZkPath {
    SERVICE("/service"),
    QUEUE("/queue"),
    LOCKQUEUE("/lockqueue"),
    WORKPOOL("/workpool"),
    SEMAPHORE("/semaphore"),
    MUTEX("/mutex"),
    PERSISTENTLOCK("/persistentlock"),
    CONFIG("/config"),
    WORKFLOW("/workflow"),
    LEADER("/leader"),
    GLOBALLOCK("/globallock"),
    AUTHN("/authservice"),
    STATE("/state"),
    LOCKDATA("/lockdata"),
    KVSTORE("/kvstore"),
    SITES("/sites"),
    BARRIER("/barrier");

    private final String _path;

    ZkPath(String path) {
        _path = path;
    }

    @Override
    public String toString() {
        return _path;
    }
}
