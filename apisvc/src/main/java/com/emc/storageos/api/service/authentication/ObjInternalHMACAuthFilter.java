/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.authentication;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import com.emc.storageos.security.authentication.AbstractRequestWrapperFilter;
import com.emc.storageos.security.authentication.InterNodeHMACAuthFilter;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Authentication filter for internal api used from object
 * uses HMAC signed request, with a key in coordinator
 */
public class ObjInternalHMACAuthFilter extends InterNodeHMACAuthFilter {
    @Override
    protected AbstractRequestWrapperFilter.AbstractRequestWrapper authenticate(
            final ServletRequest servletRequest) {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        if (isInternalRequest(req)) {
            if (verifySignature(req)) {
                final StorageOSUser user = getStorageOSUserFromRequest(servletRequest, true);
                return new AbstractRequestWrapperFilter.AbstractRequestWrapper(req, user);
            } else {
                throw APIException.unauthorized
                        .unauthenticatedRequestUnsignedInternalRequest();
            }
        }
        return null;
    }
}
