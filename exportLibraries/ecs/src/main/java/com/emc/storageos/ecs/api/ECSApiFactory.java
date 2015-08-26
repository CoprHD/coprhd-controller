package com.emc.storageos.ecs.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECSApiFactory {
	private Logger _log = LoggerFactory.getLogger(ECSApiFactory.class);

    /**
     * Initialize
     */
    public void init() {
        _log.info(" ECSApiFactory ECSApi factory initialized");
    }
    
    /**
     * shutdown http connection manager.
     */
    protected void shutdown() {
    }
}
