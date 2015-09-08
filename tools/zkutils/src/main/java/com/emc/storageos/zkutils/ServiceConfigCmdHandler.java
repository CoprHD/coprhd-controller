/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.zkutils;

import java.util.ArrayList;
import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.client.model.MigrationStatus;

/**
 * Handle service information configuration
 */
public class ServiceConfigCmdHandler {
    private static final Logger log = LoggerFactory
            .getLogger(ServiceConfigCmdHandler.class);
    private static final String ZKUTI_CONF = "/zkutils-conf.xml";
    private static final String COORDINATOR_BEAN = "coordinator";
    private static final String ROOT_PATH = "/";

    private CoordinatorClient coordinator;

    public ServiceConfigCmdHandler() {
        ApplicationContext ctx = new ClassPathXmlApplicationContext(ZKUTI_CONF);
        coordinator = (CoordinatorClient) ctx.getBean(COORDINATOR_BEAN);
    }

    /**
     * Reset Migration FAILED status by cleaning up all the Zookeeper path related to the target version
     */
    public void resetMigrationFailed() {
        MigrationStatus nowStatus = coordinator.getMigrationStatus();
        if (nowStatus != null && nowStatus.equals(MigrationStatus.FAILED)) {
            log.info("Reset Migration status from {}.", nowStatus);
            cleanZkPathForVersion(coordinator.getTargetDbSchemaVersion());
            nowStatus = coordinator.getMigrationStatus();

            if (nowStatus == null) {
                System.out.println("Reset Migration status Successfully.");
            } else {
                String errMsg = "Fail to reset Migration status.";
                System.out.println(errMsg);
                log.error(errMsg);
            }
        } else {
            String errMsg = String.format("The Migration status is %s, not %s.",
                    nowStatus, MigrationStatus.FAILED);
            log.error(errMsg);
            System.out.println(errMsg);
        }
    }

    private void cleanZkPathForVersion(String version) {
        CoordinatorClientImpl coordinatorClientImpl = (CoordinatorClientImpl) coordinator;
        ZkConnection zkConnection = coordinatorClientImpl.getZkConnection();
        try {
            List<String> paths = new ArrayList<>();
            CuratorFramework curator = zkConnection.curator();
            queryAllPath(version, ROOT_PATH, paths, curator);
            log.info("Found the following Zookeeper paths, {}", paths);
            removePath(paths, curator);
        } catch (Exception e) {
            log.error("Fail to clean Zookeeper paths, excetpion={}", e);
        }

    }

    private void queryAllPath(String pattern, String path, List<String> output,
            CuratorFramework curator) throws Exception {
        List<String> configPaths = curator.getChildren().forPath(path);
        for (String configPath : configPaths) {
            String newPath;
            if (!path.equals(ROOT_PATH)) {
                newPath = String.format("%1$s/%2$s", path, configPath);
            } else {
                newPath = String.format("/%1$s", configPath);
            }
            if (configPath.equals(pattern)) {
                output.add(newPath);
                return;
            }
            queryAllPath(pattern, newPath, output, curator);
        }
    }

    private void removePath(List<String> paths, CuratorFramework curator)
            throws Exception {
        for (String path : paths) {
            curator.delete().deletingChildrenIfNeeded().forPath(path);
            log.warn("Deleted {} in Zookeeper.", path);
        }
    }
}
