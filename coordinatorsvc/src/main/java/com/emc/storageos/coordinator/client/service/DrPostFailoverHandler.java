/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler.Status;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

/**
 * Base class for post failover processing for DR. For a service that would do some processing after DR failover -
 * 1) Extend DrPostFailoverHandler and override execute() method. This method is called with lock acquired. 
 * 2) Give a unique name and register the name to ALL_FAILOVER_HANDLERS
 */
public abstract class DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(DrPostFailoverHandler.class);
    private static final String POST_FAILOVER_HANDLER_LOCK = "drPostFailoverLock";
    
    protected static final String CONFIG_KIND = "disasterRecoveryFailoverHandlers";
    protected static final String CONFIG_ID = "global";
    
    enum Status {
        INIT,
        EXECUTING,
        COMPLETED,
        ERROR
    }
    
    @Autowired
    protected CoordinatorClient coordinator;
    @Autowired
    protected DrUtil drUtil;
    
    private String name = "Default";
    
    public DrPostFailoverHandler() {}
    
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
                boolean isExecuted = isExecuted();
                log.info("Execution status for current handler is {}", isExecuted);
                if (!isExecuted) {
                    log.info("Start post failover processing");
                    updateStatus(Status.EXECUTING);
                    execute();
                    updateStatus(Status.COMPLETED);
                }
                log.info("Post failover processing done");
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
    
    protected abstract void execute();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected boolean isExecuted() {
        Configuration config = coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        String value = config.getConfig(name);
        if (value != null && Status.COMPLETED.toString().equals(value)) {
            return true;
        }
        return false;
    }
    
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
}
