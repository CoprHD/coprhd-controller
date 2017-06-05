/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class CifsShareACLRunAsRootAttributeMigration extends BaseCustomMigrationCallback {

    private static final Logger logger = LoggerFactory
            .getLogger(CifsShareACLRunAsRootAttributeMigration.class);

    private static final Integer MAX_RECORDS_SIZE = 100;

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("Migration started");

        DbClient dbClient = getDbClient();

        try {
            List<URI> cifsShareACLURIList = dbClient.queryByType(CifsShareACL.class,
                    true);
            Iterator<CifsShareACL> cifsShareACLList = dbClient
                    .queryIterativeObjects(CifsShareACL.class, cifsShareACLURIList,
                            true);

            List<CifsShareACL> modifiedACLList = new ArrayList<CifsShareACL>();
            while (cifsShareACLList.hasNext()) {
                CifsShareACL ace = cifsShareACLList.next();
                ace.setRunAsRoot(false);
                modifiedACLList.add(ace);

                if (modifiedACLList.size() >= MAX_RECORDS_SIZE) {
                    dbClient.updateObject(modifiedACLList);
                    logger.info("Processed {} share ACEs.", modifiedACLList.size());
                    modifiedACLList.clear();
                }

            }

            if (!modifiedACLList.isEmpty()) {
                logger.info("Processed {} share ACEs.", modifiedACLList.size());
                dbClient.updateObject(modifiedACLList);
                modifiedACLList.clear();
            }

            logger.info("Migration completed successfully");
        } catch (Exception e) {
            logger.error("Exception occured while migrating cifs share acl run as root setting.");
            logger.error(e.getMessage(), e);
        }

    }
}
