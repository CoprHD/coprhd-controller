/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.generator;

import java.io.IOException;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.server.impl.CassandraConfigLoader;
import com.emc.storageos.db.server.impl.SchemaUtil;

public class DbSchemaGenerator {
    private final static Logger log = LoggerFactory.getLogger(DbSchemaGenerator.class);

    private final static String YAML_FILE_NAME = "db-schema-generator-conf.yaml";
    private final static String CONF_FILE_NAME = "db-schema-generator-conf.xml";
    private final static String SCHEMA_BEAN = "dbschemautil";
    private final static String NODEMAP_FILE_NAME = "nodeaddrmap-var.xml";
    private final static String NODEMAP_BEAN = "inetAddessLookupMap";


    public static void main(String[] args) throws IOException {
        log.info("Starting Cassandra Daemon...");
        
        System.setProperty("cassandra.config", YAML_FILE_NAME);
        System.setProperty("cassandra.config.loader", CassandraConfigLoader.class.getName());
        
        CassandraDaemon daemon = new CassandraDaemon();
        daemon.init(null);
        daemon.start();
        
        log.info("Starting to create db schemas.");
        System.out.println("Initializing schema util ...");
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(CONF_FILE_NAME);
        SchemaUtil schemaUtil = (SchemaUtil) ctx.getBean(SCHEMA_BEAN);
        ctx = new ClassPathXmlApplicationContext(NODEMAP_FILE_NAME);
        CoordinatorClientInetAddressMap nodeMap = (CoordinatorClientInetAddressMap) ctx.getBean(NODEMAP_BEAN);

        schemaUtil.scanAndSetupDb(false, nodeMap);
        
        flushCassandra();
        daemon.stop();
        log.info("Finished to create db schemas.");
    }
    
    /**
     * Shut down gossip/thrift and then drain
     */
    private static void flushCassandra() {
        StorageServiceMBean svc = StorageService.instance;

        if (svc.isInitialized()) {
            svc.stopGossiping();
        }

        if (svc.isRPCServerRunning()) {
            svc.stopRPCServer();
        }

        try {
            svc.drain();
        } catch (Exception e) {
            log.error("Fail to drain:", e);
        }

    }
    

}
