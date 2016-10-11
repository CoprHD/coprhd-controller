/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.wbem.WBEMException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newConcurrentHashSet;

/**
 * Allows invocation of EMCRefreshSystem once per system.
 */
class OneTimeEMCRefreshSystem implements EMCRefreshSystemInvoker {

    private static final Logger log = LoggerFactory.getLogger(OneTimeEMCRefreshSystem.class);

    private Set<StorageSystem> refreshedSystems;
    private SmisCommandHelper helper;

    /**
     * Default constructor.
     *
     * @param helper    SmisCommandHelper instance, non-null.
     */
    public OneTimeEMCRefreshSystem(SmisCommandHelper helper) {
        this.helper = checkNotNull(helper);
        refreshedSystems = newConcurrentHashSet();
    }

    @Override
    public boolean invoke(StorageSystem system) throws WBEMException {
        checkNotNull(system);
        if (refreshedSystems.contains(system)) {
            log.info("Skipping EMCRefreshSystem for {}", system.getId());
            return false;
        }

        log.info("Invoking EMCRefreshSystem for {}", system.getId());
        helper.callRefreshSystem(system);
        refreshedSystems.add(system);

        return true;
    }
}
