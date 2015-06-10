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

package com.emc.storageos.coordinator.common.impl;

/**
 * Enumeration of well known paths for coordinator cluster
 */
public enum ZkPath {
    SERVICE("/service"),
    QUEUE("/queue"),
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
    KVSTORE("/kvstore");

    private final String _path;

    ZkPath(String path) {
        _path = path;
    }

    @Override
    public String toString() {
        return _path;
    }
}
