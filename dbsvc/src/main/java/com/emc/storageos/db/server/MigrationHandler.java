/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server;

import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Interface for handling data migration during upgrades
 */
public interface MigrationHandler {
    /**
     * process migration
     * Returns true on success, false on failure
     */
    public boolean run() throws DatabaseException;

}
