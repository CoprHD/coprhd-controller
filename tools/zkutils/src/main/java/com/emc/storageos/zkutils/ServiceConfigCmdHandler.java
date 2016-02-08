/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.zkutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.Constants;

/**
 * Handle service information configuration
 */
public class ServiceConfigCmdHandler {
    private static final Logger log = LoggerFactory
            .getLogger(ServiceConfigCmdHandler.class);
    private static final String ZKUTI_CONF = "/zkutils-conf.xml";
    private static final String COORDINATOR_BEAN = "coordinator";

    private CoordinatorClient coordinator;

    public ServiceConfigCmdHandler() {
        ApplicationContext ctx = new ClassPathXmlApplicationContext(ZKUTI_CONF);
        coordinator = (CoordinatorClient) ctx.getBean(COORDINATOR_BEAN);
    }

    /**
     * Reset Migration status from specific status
     * 
     * @param status
     *            specific status
     */
    public void resetMigrationStatus(MigrationStatus status) {
        MigrationStatus nowStatus = coordinator.getMigrationStatus();
        if (nowStatus != null && nowStatus.equals(status)) {
            log.info("Reset Migration status from {}.", nowStatus);
            coordinator.removeServiceConfiguration(coordinator.getSiteId(), getMigrationConfiguration());
            nowStatus = coordinator.getMigrationStatus();
            log.info("After reseting, the status is {}.", nowStatus);
            if (nowStatus == null) {
                System.out.println("Reset Migration status Successfully.");
            } else {
                System.out.println("Fail to reset Migration status.");
                log.error("Fail to reset Migration status.");
            }
        } else {
            log.error("The Migration status is {}, not specific {}.", nowStatus, status);
            System.out.println(String.format("The Migration status is %s, not %s.",
                    nowStatus, status));
        }
    }

    /**
     * Get MigrationConfiguration Config. If you want to get status, use
     * coordinator.getMigrationStatus();
     * 
     * @return null if not found
     */
    public Configuration getMigrationConfiguration() {
        Configuration config = coordinator.queryConfiguration( coordinator.getSiteId(), 
                coordinator.getVersionedDbConfigPath(Constants.DBSVC_NAME,
                        coordinator.getTargetDbSchemaVersion()), Constants.GLOBAL_ID);
        return config;
    }
}
