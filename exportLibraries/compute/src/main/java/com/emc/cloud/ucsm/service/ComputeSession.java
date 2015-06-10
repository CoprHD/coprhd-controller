/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import com.emc.cloud.platform.clientlib.ClientGeneralException;

public interface ComputeSession {
    public <T> T execute(Object object,Class<T> returnType) throws ClientGeneralException;
    public void login() throws ClientGeneralException;
    public void logout() throws ClientGeneralException;
    public void clearSession() throws ClientGeneralException;
}
