/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.descriptor.AbstractServiceDescriptors;
import com.emc.sa.descriptor.ServiceDefinition;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;

/**
 * Stores service descriptors in Zookeeper nodes.
 * 
 * Descriptors are stored in nodes that have the same name as the serviceid
 */
@Component
public class ZkServiceDescriptors extends AbstractServiceDescriptors {
    private static Logger LOG = Logger.getLogger(ZkServiceDescriptors.class);

    private static final int MAX_SERVICE_NODES = 200;
    private static String ZK_SERVICE_DEFINITION_PATH = "/portal/servicedefinitions";
    private CoordinatorClient coordinatorClient;
    private DistributedDataManager dataManager;

    @PostConstruct
    public void start() {
        try {
            coordinatorClient.start();
            dataManager = coordinatorClient.createDistributedDataManager(ZK_SERVICE_DEFINITION_PATH, MAX_SERVICE_NODES);
        } catch (IOException e) {
            LOG.error(e);
            throw new RuntimeException("Error Starting ServiceDescriptors", e);
        }
    }

    @Autowired
    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    /**
     * @return A list all service definitions stored in Zookeeper
     */
    @Override
    protected Collection<ServiceDefinition> getServiceDefinitions() {
        List<ServiceDefinition> serviceDefinitions = new ArrayList<>();

        try {
            if (dataManager.checkExists(ZK_SERVICE_DEFINITION_PATH) != null) {
                for (String serviceId : dataManager.getChildren(ZK_SERVICE_DEFINITION_PATH)) {
                    try {
                        serviceDefinitions.add(getServiceDefinition(serviceId));
                    } catch (Exception e) {
                        LOG.error("Failed to get definition for service: " + serviceId, e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error listing Service definitions", e);
        }
        return serviceDefinitions;
    }

    @Override
    protected ServiceDefinition getServiceDefinition(String serviceId) {
        try {
            String path = getServiceDefinitionPath(serviceId);
            if (dataManager.checkExists(path) != null) {
                return (ServiceDefinition) dataManager.getData(path, false);
            }
        } catch (Exception e) {
            LOG.error("Error getting service definition: " + serviceId, e);
        }
        throw new IllegalStateException("Service " + serviceId + " not found");
    }

    /**
     * Adds all the given service definitions to the Zookeeper tree
     */
    public void addServices(List<ServiceDefinition> services) throws Exception {
        ensurePathExists();

        Set<String> remainingDescriptors = new HashSet<>(dataManager.getChildren(ZK_SERVICE_DEFINITION_PATH));
        for (ServiceDefinition service : services) {
            LOG.debug(String.format("Adding Service %s into ZK", service.serviceId));
            String path = getServiceDefinitionPath(service.serviceId);
            try {
                Stat before = dataManager.checkExists(path);
                dataManager.putData(path, service);
                Stat after = dataManager.checkExists(path);
                nodeUpdated(path, before, after);
                // Remove the service from the remaining list
                remainingDescriptors.remove(service.serviceId);
            } catch (Exception e) {
                LOG.error(String.format("Failed to add Service %s into ZK, path: %s", service.serviceId, path), e);
                throw e;
            }
        }

        // Remove any remaining descriptors
        for (String descriptorName : remainingDescriptors) {
            LOG.info(String.format("Removing old Service %s from ZK", descriptorName));
            dataManager.removeNode(ZK_SERVICE_DEFINITION_PATH + "/" + descriptorName);
        }
    }

    private String getServiceDefinitionPath(String serviceId) {
        return ZK_SERVICE_DEFINITION_PATH + "/" + serviceId;
    }

    private void ensurePathExists() throws Exception {
        if (dataManager.checkExists(ZK_SERVICE_DEFINITION_PATH) == null) {
            LOG.info("Creating ZK node: " + ZK_SERVICE_DEFINITION_PATH);
            dataManager.createNode(ZK_SERVICE_DEFINITION_PATH, false);
            nodeUpdated(ZK_SERVICE_DEFINITION_PATH, null, dataManager.checkExists(ZK_SERVICE_DEFINITION_PATH));
        }
    }

    private void nodeUpdated(String path, Stat before, Stat after) {
        if (before == null) {
            if (after == null) {
                LOG.warn(String.format("Failed to create ZK node: %s", path));
            }
            else {
                LOG.debug(String.format("Created ZK node [%s, created:%s]", path, new Date(after.getCtime())));
            }
        }
        else {
            int version = after.getVersion();
            long beforeTime = before.getMtime();
            long afterTime = after.getMtime();
            if (beforeTime == afterTime) {
                LOG.warn(String.format("Failed to update ZK node [%s, version:%s, before:%s, after:%s]", path, version,
                        new Date(before.getMtime()), new Date(after.getMtime())));
            }
            else {
                LOG.debug(String.format("Updated ZK node [%s, version:%s, before:%s, after:%s]", path, version,
                        new Date(before.getMtime()), new Date(after.getMtime())));
            }
        }
    }

    @PreDestroy
    public void closeDataMangager() {
        if (dataManager != null) {
            dataManager.close();
        }
    }
}
