/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.migrationtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.net.URI;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.client.URIUtil;


public class DbMigrationTool {
    private static final Logger log = LoggerFactory.getLogger(DbMigrationTool.class);

    DbClientImpl _dbClient = null;
 
    private static final boolean DEBUG = false;
 
    public DbMigrationTool() {
       
    }

    /**
     * Initiate the dbclient
     */
    public void initDbClient() {
        try {
            System.out.println("Initializing db client ...");
            _dbClient.start();

        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

    public DbClientImpl getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this._dbClient = dbClient;
    }

    public void stop() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    public void start(boolean skipMigrationCheck) {
        _dbClient.setBypassMigrationLock(skipMigrationCheck);
        _dbClient.start();
    }

}
