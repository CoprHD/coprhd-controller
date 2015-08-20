package com.emc.storageos.object.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ObjectApiFactory {
	private Logger _log = LoggerFactory.getLogger(ObjectApiFactory.class);

    /**
     * Initialize
     */
    public void init() {
        _log.info(" ObjectApi factory initialized");
    }

}
