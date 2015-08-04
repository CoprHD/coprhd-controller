/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.model;

import javax.ws.rs.core.Response.StatusType;

public interface StatusCoded extends ServiceCoded {
    public StatusType getStatus();
}
