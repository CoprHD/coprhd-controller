/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbutils;

import java.io.IOException;

import org.apache.cassandra.service.CassandraDaemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.server.impl.CassandraConfigLoader;
import com.emc.storageos.db.server.impl.SchemaUtil;

public class DbSchemaCreator {
    private final static Logger log = LoggerFactory.getLogger(DbSchemaCreator.class);

    static void usage() {
        log.info("DbSchemaCreator, prepopulate db schemas at build time to reduce system boot time ");
    }

    public static void main(String[] args) throws IOException {
        log.info("Starting Cassandra Daemon...");
        
        System.setProperty("cassandra.config", "dbcreator-conf.yaml");
        System.setProperty("cassandra.config.loader", CassandraConfigLoader.class.getName());
        
        CassandraDaemon daemon = new CassandraDaemon();
        daemon.init(null);
        daemon.start();
        
        log.info("Starting to create db schemas.");
        System.out.println("Initializing schema util ...");
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/dbcreator-conf.xml");
        
        SchemaUtil schemaUtil = (SchemaUtil) ctx.getBean("dbschemautil");
        schemaUtil.scanAndSetupDb(false);
        
        log.info("Finished to create db schemas.");
        
        

    }
    

}
