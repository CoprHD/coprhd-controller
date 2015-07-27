/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import com.emc.storageos.security.authentication.AbstractAuthenticationFilter;
import com.emc.storageos.security.authentication.StorageOSUserRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *  Dummy authentication filter, takes in the user context from a header string
 */
public class NoAuthHeaderUserFilter extends AbstractAuthenticationFilter {
    public static String USER_INFO_HEADER_TAG = "BourneUser";

    @Autowired
    private StorageOSUserRepository _userRepo;

    private boolean fromLocalhost(HttpServletRequest req) {
        return (req.getRemoteHost().equalsIgnoreCase("localhost") ||
                req.getRemoteHost().equals("127.0.0.1"));
    }

    @Override
    protected AbstractRequestWrapper authenticate(final ServletRequest servletRequest) {
        // check if we can extract user context from request header
        // and the request is coming on localhost
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        final String user = req.getHeader(USER_INFO_HEADER_TAG);
        if (user != null && !user.isEmpty() && fromLocalhost(req)) {
            return new AbstractRequestWrapper(req, _userRepo.findOne(user));
        }
        return null;
    }
}
