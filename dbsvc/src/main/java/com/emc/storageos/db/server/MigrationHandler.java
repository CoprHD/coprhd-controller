/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server;

import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Interface for handling data migration during upgrades
 */
public interface MigrationHandler {
    /**
     *   process migration
     *   Returns true on success, false on failure
     */
    public boolean run() throws DatabaseException;

}
