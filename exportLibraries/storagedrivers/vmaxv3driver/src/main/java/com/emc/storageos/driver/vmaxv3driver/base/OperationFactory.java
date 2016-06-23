/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

/**
 * Define the "Operation" factory which is used to get the proper operation
 * instance by given arguments.
 * 
 * Created by gang on 6/21/16.
 */
public interface OperationFactory {

    /**
     * Get the proper "Operation" instance by given operation name and arguments.
     * If none can be found, "null" will be returned.
     *
     * @param registry The "registry" instance passed in by the Southbound SDK framework.
     * @param lockManager The "LockManager" instance passed in by the Southbound SDK framework.
     * @param name The operation name.
     * @param parameters The operation arguments.
     * @return The requested operation instance if found or null if not found.
     */
    public Operation getInstance(Registry registry, LockManager lockManager, String name, Object... parameters);
}
