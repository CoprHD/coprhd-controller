/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.response;

/**
 * A marker interface for all concrete storage port classes to implement.
 *
 * Created by gang on 6/24/16.
 */
public interface SymmetrixPort {
    /**
     * Get the "identifier" field value of the port.
     *
     * @return
     */
    public String getIdentifier();
}
