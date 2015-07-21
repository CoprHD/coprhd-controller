/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Values used with @ClockIndependent must implement this interface
 */
public interface ClockIndependentValue {
    /**
     * Get relative order of this value to others.  Higher value wins.
     *
     * @return
     */
    public int ordinal();
}
