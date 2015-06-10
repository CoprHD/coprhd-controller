/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.descriptor.ServiceDefinition;
import com.emc.sa.descriptor.ServiceDefinitionLoader;

/**
 * Loads Service Definitions from the classpath into the Zookeeper Tree
 */
@Component
public class ZkServiceDescriptorLoader {
    private static final Logger LOG = Logger.getLogger(ZkServiceDescriptorLoader.class);

    private ZkServiceDescriptors zkDescriptors;

    @Autowired
    public void setZkServiceDescriptors(ZkServiceDescriptors zkDescriptors) {
        this.zkDescriptors = zkDescriptors;
    }

    @PostConstruct
    public void start() {
        try {
            // TODO : Add Locking around this for multi-node environments
            ClassLoader cl = ZkServiceDescriptorLoader.class.getClassLoader();
            List<ServiceDefinition> services = ServiceDefinitionLoader.load(cl);
            zkDescriptors.addServices(services);
            LOG.info("Loaded " + services.size() + " services");
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to load services", e);
        }
    }
}
