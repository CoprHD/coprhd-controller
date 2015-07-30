/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import com.emc.storageos.security.authorization.Role;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * Filter to disable security, gives everyone a pass as a sysadmin with TENANT_ADMIN role
 */
public class SecurityDisablerFilter extends AbstractRequestWrapperFilter {

    @Override
    protected AbstractRequestWrapper authenticate(final ServletRequest servletRequest) {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        StorageOSUser user = _userRepo.findOne("root");
        user.addRole(Role.TENANT_ADMIN.toString());
        return new AbstractRequestWrapper(req, user);
    }
}
