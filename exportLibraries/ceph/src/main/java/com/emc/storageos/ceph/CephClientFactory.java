package com.emc.storageos.ceph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CephClientFactory {
    private Logger log = LoggerFactory.getLogger(CephClientFactory.class);

    public void init() {
        log.info("CephClient factory initialized");
    }

    public CephClient getClient(final String monitorHost, final String userName, final String userKey) {
        return new CephNativeClient(monitorHost, userName, userKey);
    }
}
