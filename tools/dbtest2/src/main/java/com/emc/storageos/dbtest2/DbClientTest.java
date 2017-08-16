/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.dbtest2;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created by wangs12 on 8/16/2017.
 */
public class DbClientTest {
    private static Logger log = LoggerFactory.getLogger(DbClientTest.class);

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Autowired
    private DbClient dbClient;

    public void init() {
        dbClient.start();
        log.info("dbclient started");
    }
}
