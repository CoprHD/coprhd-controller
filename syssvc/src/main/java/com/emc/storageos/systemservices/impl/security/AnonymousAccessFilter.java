/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.security;

import com.emc.storageos.security.authentication.AbstractAuthenticationFilter;
import com.emc.storageos.security.authentication.AbstractRequestWrapperFilter;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * Filter for allowing cli downloads anonymous access
 */
public class AnonymousAccessFilter extends AbstractAuthenticationFilter {
    public static final String CLI_PATH = "/cli";
    
    @Override
    protected AbstractRequestWrapperFilter.AbstractRequestWrapper authenticate(final ServletRequest servletRequest) {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        if (req.getRequestURI().contains(CLI_PATH)) {
            return new AbstractRequestWrapperFilter.AbstractRequestWrapper(req, null);
        }
        return null;
    }
}
