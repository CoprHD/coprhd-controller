/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation or is licensed to
 * EMC Corporation from third parties. Use of this software and the intellectual property
 * contained therein is expressly limited to the terms and conditions of the License
 * Agreement under which it is provided by or on behalf of EMC.
 */

package com.emc.sa.api.utils;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CatalogConfigUtils {

    private final static Logger log = LoggerFactory.getLogger(CatalogConfigUtils.class);

    protected CoordinatorClient _coordinator;
    private static final String configKey = "timestamp";

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    /**
     * update zk node /config/catalog/acl_change, so portalsvc could get notified and clear its cache.
     * 
     */
    public void notifyCatalogAclChange() {
        ConfigurationImpl configImpl = new ConfigurationImpl();
        configImpl.setKind(Constants.CATALOG_CONFIG);
        configImpl.setId(Constants.CATALOG_ACL_CHANGE);
        String time = String.valueOf(System.currentTimeMillis());
        configImpl.setConfig(configKey, time);
        try {
            log.debug("catalog acl change time: " + time);
            _coordinator.persistServiceConfiguration(configImpl);
        } catch (Exception e) {
            log.warn(String.format(
                    "updating zk node /config/%s/%s failed, portalsvc cache will not clear immediately, but will reload 10 minutes later",
                    Constants.CATALOG_CONFIG, Constants.CATALOG_ACL_CHANGE));
        }
    }
}
