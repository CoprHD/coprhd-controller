/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

/**
 * Interface for providing access to an auth token cookie. This allows the 'ownership'
 * of the auth token to be external from the filter.
 */
public interface TokenAccess {
    public void setToken(String token);

    public String getToken();
}
