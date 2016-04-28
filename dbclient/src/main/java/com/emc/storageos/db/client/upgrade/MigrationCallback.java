/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade;

import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration callback interface
 */
public interface MigrationCallback {

    /**
     * actual run method
     */
    public void process() throws MigrationCallbackException;

    /**
     * Get name of the handler
     * 
     * @return
     */
    public String getName();

}
