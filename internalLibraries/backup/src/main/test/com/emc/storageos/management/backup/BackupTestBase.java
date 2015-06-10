/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.services.util.LoggingUtils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public abstract class BackupTestBase {

    protected static ApplicationContext context;
    protected static BackupManager backupManager;
    protected static ZkBackupHandler zkBackupHandler;
    protected static DbBackupHandler geoDbBackupHandler;
    protected static CoordinatorClient coordinatorClient;

    static {
        try {
            // Initializes log4j system at first
            LoggingUtils.configureIfNecessary("resources/backup-test-log4j.properties");

            // Starts Zookeeper service before initializing coordinator client
            context = new ClassPathXmlApplicationContext("resources/backup-test-zk-conf.xml");
            zkBackupHandler = context.getBean("zkBackupHandler", ZkBackupHandler.class);
            ZkSimulator zkSimulator = context.getBean("zkSimulator", ZkSimulator.class);
            coordinatorClient = zkSimulator.getCoordinatorClient();

            // Initializes BackupManager for local db
            context = new ClassPathXmlApplicationContext("resources/backup-test-conf.xml");
            backupManager = context.getBean("backupManager", BackupManager.class);
            backupManager.setCoordinatorClient(coordinatorClient);

            // Initializes geodb simulator and backupHandler
            //context = new ClassPathXmlApplicationContext("backup-test-geodb-conf.xml");
            //geoDbBackupHandler = context.getBean("geoDbBackupHandler", DbBackupHandler.class);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * General method to help create backup of specified Handler
     * @param backupTag
     *          The tag of backup
     * @param newBackupHandler
     *          New backup handler
     */
    public static void createBackup(String backupTag, BackupHandler newBackupHandler) {
        if (backupTag == null || newBackupHandler == null)
            return;
        BackupHandler backupHandler = backupManager.getBackupHandler();
        try {
            backupManager.setBackupHandler(newBackupHandler);
            backupManager.create(backupTag);
        } finally {
            backupManager.setBackupHandler(backupHandler);
        }
    }
}
