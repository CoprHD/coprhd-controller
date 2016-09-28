/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

/**
 * This interface is used to parse the given input values from SBSDK method call into
 * the target request body for VMAXV3 REST API call.
 *
 * Created by gang on 9/28/16.
 */
public interface RequestBodyParser {
    /**
     * Return the target request body instance.
     *
     * @return
     */
    public RequestBody parse();
}
