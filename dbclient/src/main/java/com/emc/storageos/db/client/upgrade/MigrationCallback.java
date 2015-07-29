/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade;

/**
 * Migration callback interface
 */
public interface MigrationCallback {

    /**
     * actual run method
     */
    public void process();

    /**
     * Get name of the handler
     * 
     * @return
     */
    public String getName();

}
