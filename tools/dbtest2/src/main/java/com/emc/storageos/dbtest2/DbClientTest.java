/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.dbtest2;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

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

    public void write(int count) {
        NamedURI prj = new NamedURI(URIUtil.createId(Project.class), "prj_" + randomSuffix());

        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setLabel("vol" + randomSuffix());
            volume.setProject(prj);
            volume.setType(0);
            dbClient.createObject(volume);
        }

        long dur = System.currentTimeMillis() - start;
        log.info("Write {} volumes done. Spent {} seconds", count, dur/1000);
    }

    private String randomSuffix() {
        LocalDateTime t = LocalDateTime.now();
        return t.getDayOfMonth() + "_" + t.getHour() + "_" + t.getMinute() + "_" + t.getSecond();
    }

}
