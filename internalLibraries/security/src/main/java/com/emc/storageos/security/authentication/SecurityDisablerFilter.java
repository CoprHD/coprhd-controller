/**
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

import com.emc.storageos.security.authorization.Role;
import org.springframework.beans.factory.annotation.Autowired;

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
