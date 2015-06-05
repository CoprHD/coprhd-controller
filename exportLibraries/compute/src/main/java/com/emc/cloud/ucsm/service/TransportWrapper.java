/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import com.emc.cloud.platform.clientlib.ClientGeneralException;

public interface TransportWrapper {
    public <T> T execute(Object device,Object payload,Class<T> returnType) throws ClientGeneralException;
}
