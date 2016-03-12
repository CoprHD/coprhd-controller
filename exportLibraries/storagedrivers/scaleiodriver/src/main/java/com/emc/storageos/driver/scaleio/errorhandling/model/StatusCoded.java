/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.model;

import javax.ws.rs.core.Response.StatusType;

public interface StatusCoded extends ServiceCoded {
    StatusType getStatus();
}
