/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis;

import com.emc.storageos.db.client.model.StorageSystem;

import javax.wbem.WBEMException;

/**
 * Interface for invoking the EMCRefreshSystem method on an SMI-S provider.
 */
public interface EMCRefreshSystemInvoker {
    /**
     * Invoke the EMCRefreshSystem method on an SMI-S provider.
     *
     * @param system    StorageSystem
     * @return          true, if the invocation occurred, false otherwise.
     * @throws          WBEMException
     */
    boolean invoke(StorageSystem system) throws WBEMException;
}
