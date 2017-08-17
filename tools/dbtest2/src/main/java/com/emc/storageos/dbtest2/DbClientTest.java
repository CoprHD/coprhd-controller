/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.dbtest2;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbViewQuery;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.DbViewQueryImpl;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeRestRep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

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
     //   dbClient.start();
      //  log.info("dbclient started");
    }

    public void write(int count) {
        NamedURI prj = new NamedURI(URIUtil.createId(Project.class), "prj_" + randomSuffix());
        log.info("##### write start. The project is {}, {}", prj.getURI(), prj.getName());
        System.out.printf("The project is %s  ,  %s", prj.getName(), prj.getURI());

        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setLabel("vol" + randomSuffix());
            volume.setProject(prj);
            volume.setType(0);
            dbClient.createObject(volume);
            volume.setType(1);
            dbClient.updateObject(volume);
        }

        long dur = System.currentTimeMillis() - start;
        log.info("##### Write and update {} volumes done. Spent {} seconds", count, dur/1000);
        System.out.printf("Done. The project is %s  ,  %s", prj.getName(), prj.getURI());
    }

    private String randomSuffix() {
        LocalDateTime t = LocalDateTime.now();
        return t.getDayOfMonth() + "_" + t.getHour() + "_" + t.getMinute() + "_" + t.getSecond();
    }

    public void list(String prj) {

        QueryResultList resultList = new QueryResultList() {
            @Override
            public Object createQueryHit(URI uri) {
                log.info("createQueryHit get called 1111");
                return null;
            }

            @Override
            public Object createQueryHit(URI uri, String name, UUID timestamp) {
                log.info("createQueryHit get called 22222");
                return null;
            }
        };
        log.info("##### list vol start");
        long start = System.currentTimeMillis();
        DbViewQuery viewQuery = new DbViewQueryImpl((DbClientImpl) dbClient);
        viewQuery.listVolumesByProject(URI.create(prj), Volume.VOL_TYPE.SRDF_SOURCE, resultList);

        Iterator<Volume> volItr = resultList.iterator();
        int volCount = printVolumeList(volItr);
        long duration = System.currentTimeMillis() - start;
        log.info("##### list vol end {} volumes. Spent time {} ms", volCount, duration);
        System.out.printf("list vol done. %d volumes, spent time %d", volCount, duration);
    }

    private int printVolumeList(Iterator<Volume> volItr) {
        int count = 0;
        while (volItr.hasNext()) {
            Volume vol = volItr.next();

            VolumeRestRep rep = new VolumeRestRep();
            rep.setId(vol.getId());
            rep.setName(vol.getLabel());
            count++;
        }
        return count;
    }

}
