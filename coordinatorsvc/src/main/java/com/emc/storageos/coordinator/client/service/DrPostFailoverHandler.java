/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.util.List;
import java.util.Map;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ZkPath;

/**
 * Base class for post failover processing of DR. For a service that would do some processing after DR failover -
 * 
 * 1) Extend DrPostFailoverHandler and override execute() method. This method is called with lock acquired. Each handler has a unique name 
 * 2) Register the name to DrPostFailoverHandler.Factory in sys-conf.xml
 * 3) Call run() in the middle of your service initialization function. Post DR failover is supposed to run before it starts serving request
 * 
 */
public abstract class DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(DrPostFailoverHandler.class);
    private static final String POST_FAILOVER_HANDLER_LOCK = "drPostFailoverLock";
    
    // ZK configuration to persist handler execution status.
    protected static final String CONFIG_KIND = "disasterRecoveryPostFailoverHandlers";
    protected static final String CONFIG_ID = "global";
    
    // Status for each handler. Persisted to ZK
    enum Status {
        INIT,
        EXECUTING,
        COMPLETED
    }
    
    @Autowired
    private CoordinatorClient coordinator;
    @Autowired
    private DrUtil drUtil;
    private String name;
    
    public DrPostFailoverHandler() {}
    
    /**
     * Run the handler. The handler runs only on single node. If it fails, current service should quit and another node takes over to retry
     * 
     */
    public void run() {
        try {
            SiteState siteState = drUtil.getLocalSite().getState();
            if (!siteState.equals(SiteState.STANDBY_FAILING_OVER)) {
                log.info("Ignore DR post failover handler for site state {}", siteState);
                return;
            }
            
            log.info("Acquiring lock {}", POST_FAILOVER_HANDLER_LOCK);
            InterProcessLock lock = coordinator.getLock(POST_FAILOVER_HANDLER_LOCK);
            lock.acquire();
            log.info("Acquired lock {}", POST_FAILOVER_HANDLER_LOCK);
            try {
                Site site = drUtil.getLocalSite(); // check site state again after acquiring lock
                siteState = site.getState();
                if (!siteState.equals(SiteState.STANDBY_FAILING_OVER)) {
                    log.info("Ignore DR post failover handler for site state {}", siteState);
                    return;
                }
                boolean isExecuted = isCompleted();
                if (!isExecuted) {
                    log.info("Start post failover processing {}", name);
                    updateStatus(Status.EXECUTING);
                    execute();
                    updateStatus(Status.COMPLETED);
                } else {
                    log.info("Handler {} was completed on other node", name);
                }
                if (isAllHandlersCompleted()) {
                    log.info("All handlers successfully completed. Change site state to ACTIVE");
                    site.setState(SiteState.ACTIVE);
                    coordinator.persistServiceConfiguration(site.toConfiguration());
                }
            } finally {
                lock.release();
                log.info("Released lock {}", POST_FAILOVER_HANDLER_LOCK);
            }
        } catch (Exception e) {
            log.error("Failed to execute DR failover handler", e);
            throw new IllegalStateException(e);
        }
    }
    
    /**
     * Subclass should override this method to provide actions to be done after DR failover. It should
     * be idempotent - which means it could be retried multiple times without side effects to the system 
     */
    protected abstract void execute();

    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Check if current handler is completed.
     * 
     * @return true for completed. Otherwise false
     */
    protected boolean isCompleted() {
        Configuration config = coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        String value = config.getConfig(name);
        if (value != null && Status.COMPLETED.toString().equals(value)) {
            return true;
        }
        return false;
    }
    
    /**
     * Update execution status for current handler
     * 
     * @param status
     */
    protected void updateStatus(Status status) {
        Configuration config = coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        if (config == null) {
            ConfigurationImpl configImpl = new ConfigurationImpl();
            configImpl.setKind(CONFIG_KIND);
            configImpl.setId(CONFIG_ID);
            config = configImpl;
        }
        config.setConfig(name, status.toString());
        coordinator.persistServiceConfiguration(config);
    }
    
    /**
     * Check if all handlers are completed.
     * 
     * @return
     */
    protected boolean isAllHandlersCompleted() {
        Configuration config = coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        Map<String, String> allHandlers = config.getAllConfigs(true);
        for (String key : allHandlers.keySet()) {
            String status = allHandlers.get(key);
            if (!Status.COMPLETED.toString().equals(status)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Factory class manages all handlers
     */
    public static class Factory {
        private static final Logger log = LoggerFactory.getLogger(Factory.class);
    
        @Autowired
        protected CoordinatorClient coordinator;
        private List<String> handlers;
        
        public Factory() {
        }
        
        public List<String> getHandlers() {
            return handlers;
        }

        public void setHandlers(List<String> handlers) {
            this.handlers = handlers;
        }


        public void initializeAllHandlers() {
            Configuration config = coordinator.queryConfiguration(DrPostFailoverHandler.CONFIG_KIND, DrPostFailoverHandler.CONFIG_ID);
            if (config != null) {
                coordinator.removeServiceConfiguration(config);
            }
            
            ConfigurationImpl newConfig = new ConfigurationImpl();
            newConfig.setKind(DrPostFailoverHandler.CONFIG_KIND);
            newConfig.setId(DrPostFailoverHandler.CONFIG_ID);
            for (String name : handlers) {
                newConfig.setConfig(name, Status.INIT.toString());
            }
            coordinator.persistServiceConfiguration(newConfig);
            log.info("Initialize failover handler map successfully");
        }
    }
    
    /**
     * Implementation for failover handler to clean up ZK queues  
     */
    public static class QueueCleanupHandler extends DrPostFailoverHandler{
        private List<String> queueNames;
        
        public QueueCleanupHandler() {
        }
        
        @Override
        protected void execute() {
            for (String name : queueNames) {
                String fullQueuePath = String.format("%s/%s", ZkPath.QUEUE, name);
                log.info("Cleanup zk job queue path {}", fullQueuePath);
                getCoordinator().deletePath(fullQueuePath);
            }
        }
        
        public List<String> getQueueNames() {
            return queueNames;
        }

        public void setQueueNames(List<String> names) {
            this.queueNames = names;
        }
    }
}
