/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.coordinator.client.service;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Util class to write or read configuration in zookeeper
 */
public class ConfigurationUtil {

    private static Logger log = LoggerFactory.getLogger(ConfigurationUtil.class);
    private CoordinatorClient coordinator;

    public ConfigurationUtil(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void write(String siteId, String configKind, String configId, String configKey, String configVal, String lockName) throws Exception {

        InterProcessLock lock = acquireLock(lockName);

        try {
            Configuration config = coordinator.queryConfiguration(siteId, configKind, configId);
            if (config == null) {
                ConfigurationImpl configImpl = new ConfigurationImpl();
                configImpl.setId(configId);
                configImpl.setKind(configKind);
                config = configImpl;
                log.debug("Creating new config");
            }
            config.setConfig(configKey, configVal);
            coordinator.persistServiceConfiguration(siteId, config);
            log.info("Write config successfully");
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Reads object of the specified kind from coordinator and deserializes it. Config is
     * in ZK global aread /config
     *
     * @param configKind
     * @param configId
     * @param ConfigKey
     * @return the retrieved object or null if not found
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public String read(String configKind, String configId, String ConfigKey)
            throws IOException, ClassNotFoundException {
        return read(null, configKind, configId, ConfigKey);
    }

    /**
     * Reads object of the specified kind from coordinator and deserializes it. If siteId
     * is not null, the config is in zk site specific area. Otherwise in global area
     *
     * @param siteId
     * @param configKind
     * @param configId
     * @param ConfigKey
     * @return the retrieved object or null if not found
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public String read(String siteId, String configKind, String configId, String ConfigKey) throws
            IOException, ClassNotFoundException {
        Configuration config = coordinator.queryConfiguration(siteId, configKind, configId);
        if (config == null || config.getConfig(ConfigKey) == null) {
            return null;
        }
        return config.getConfig(ConfigKey);
    }

    /**
     * Acquires an interprocess lock
     *
     * @param lockName
     *            the lock to acquire
     * @return the acquired lock
     * @throws Exception
     *             if failed to acquire the lock
     */
    private synchronized InterProcessLock acquireLock(String lockName) throws Exception {
        if (lockName == null) {
            return null;
        }

        InterProcessLock lock = coordinator.getSiteLocalLock(lockName);
        lock.acquire();
        log.info("Acquired the lock {}", lockName);
        return lock;
    }

    /**
     * release the specified lock
     *
     * @param lock
     *            the lock to release
     */
    private void releaseLock(InterProcessLock lock) {
        if (lock == null) {
            return;
        }

        try {
            lock.release();
            log.info("Released the lock {}", lock.toString());
        } catch (Exception e) {
            log.error("Failed to release lock", e);
        }
    }
}
