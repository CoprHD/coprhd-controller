/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

import java.util.Map;

/**
 * Define the operation requested by the SDK driver framework.
 *
 * Created by gang on 6/21/16.
 */
public interface Operation {

    /**
     * Check if the current operation instance is matched to the given
     * operation request.
     *
     * @param name Requested operation name.
     * @param parameters Request operation arguments.
     * @return True/False.
     */
    public boolean isMatch(String name, Object... parameters);

    /**
     * Execute the operation. Note that in the "execute" implementation,
     * the given "Object... parameters" may need to be updated by adding
     * information, which is requested by the Southbound SDK framework.
     *
     * @return A map like below to indicate if result of the execution:
     *         {"success": true} means success;
     *         {"success": false, "message": "error message"} means failure.
     */
    public Map<String, Object> execute();

    /**
     * Set the "Registry" instance into the "Operation" instance.
     *
     * @param registry
     */
    public void setRegistry(Registry registry);

    /**
     * Set the "LockManager" instance into the "Operation" instance.
     * 
     * @param lockManager
     */
    public void setLockManager(LockManager lockManager);
}
