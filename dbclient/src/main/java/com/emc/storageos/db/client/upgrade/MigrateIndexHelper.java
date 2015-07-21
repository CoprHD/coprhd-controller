/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * @author cgarber
 *
 */
public class MigrateIndexHelper {
    
    private static final Logger log = LoggerFactory.getLogger(MigrateIndexHelper.class);

    public static void migrateAddedIndex(InternalDbClient dbClient, Class<? extends DataObject> cfClass, String fieldName, String annoName) {
        if (cfClass == null || fieldName == null || annoName == null) {
            // this callback has not been set up; skip it.
            throw DatabaseException.fatals.failedDuringUpgrade("Unexpected state: callback not setup", null);
        }
        
        String cfName = cfClass.getCanonicalName();
        log.info("Adding new index records for class: {} field: {} annotation: {}",
                new Object[]{cfName, fieldName, annoName});
        if (DataObject.class.isAssignableFrom(cfClass)) {
            try {
                dbClient.generateFieldIndex(cfClass, fieldName);
            } catch (Exception e) {
                throw DatabaseException.fatals.failedDuringUpgrade( "db schema migration error: could not update index "
                        + annoName + " for " + cfName + ":" + fieldName + " fields", e);
            }
        } else {
            throw DatabaseException.fatals.failedDuringUpgrade( "db schema migration error: could not update index "
                    + annoName + " for " + cfName + ":" + fieldName + " fields", null);
        }
        
    }
    
    public static void migrateRemovedIndex(InternalDbClient dbClient, Class<? extends DataObject> cfClass, String fieldName, String annoName, String previousIndexCf) {
        if (cfClass == null || fieldName == null || annoName == null || previousIndexCf == null) {
            // this callback has not been set up; skip it.
            throw DatabaseException.fatals.failedDuringUpgrade("Unexpected state: callback not setup", null);
        }
        
        String cfName = cfClass.getCanonicalName();
        log.info("Removing index records for class: {} field: {} annotation: {} index cf: {}",
                new Object[]{cfName, fieldName, annoName, previousIndexCf});
        if (DataObject.class.isAssignableFrom(cfClass)) {
            try {
                dbClient.removeFieldIndex(cfClass, fieldName, previousIndexCf);
            } catch (Exception e) {
                throw DatabaseException.fatals.failedDuringUpgrade( "db schema migration error: could not update index "
                        + annoName + " for " + cfName + ":" + fieldName + " fields", e);
            }
        } else {
            throw DatabaseException.fatals.failedDuringUpgrade( "db schema migration error: could not update index "
                    + annoName + " for " + cfName + ":" + fieldName + " fields", null);
        }
    }


}
