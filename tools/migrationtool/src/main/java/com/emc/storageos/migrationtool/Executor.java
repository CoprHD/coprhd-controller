/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbClientImpl;

public abstract class Executor {

    private static final Logger log = LoggerFactory.getLogger(Executor.class);

    DbClientImpl _dbClient = null;

    public abstract boolean execute();

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

    public void start() {
        try {
            log.info("Initializing db client ...");
            _dbClient.start();
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

}
