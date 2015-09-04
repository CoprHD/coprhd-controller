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
package com.emc.storageos.systemservices.impl.security;

import com.emc.storageos.security.SecurityDisabler;
import com.emc.storageos.security.authorization.*;
import com.sun.jersey.spi.container.ResourceFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Set;

/**
 * Class implements ResourceFilterFactory to add permissions filter where needed
 */
public class SyssvcPermissionsFilterFactory extends AbstractPermissionsFilterFactory {
    private BasePermissionsHelper _permissionsHelper;

    @Autowired(required = false)
    private SecurityDisabler _disabler;

    private @Context UriInfo uriInfo;

    /**
     * PermissionsFilter for apisvc
     */
    private class SyssvcPermissionFilter extends AbstractPermissionFilter {
        SyssvcPermissionFilter(Role[] roles, ACL[] acls, boolean proxiedUser, Class resourceClazz, BasePermissionsHelper helper) {
            super(roles, acls, proxiedUser, resourceClazz, helper);
        }

        @Override
        protected UriInfo getUriInfo() {
            return uriInfo;
        }

        /**
         * Get tenant id from the uri
         * 
         * @return
         */
        @Override
        protected URI getTenantIdFromURI(UriInfo uriInfo) {
            return null;
        }

        @Override
        protected URI getProjectIdFromURI(UriInfo uriInfo) {
            return null;
        }

        @Override
        protected Set<String> getUsageAclsFromURI(String tenantId, UriInfo uriInfo) {
            return null;
        }
    }

    /**
     * Setter for permissions helper object
     * 
     * @param permissionsHelper
     */
    public void setPermissionsHelper(BasePermissionsHelper permissionsHelper) {
        _permissionsHelper = permissionsHelper;
    }

    @Override
    protected boolean isSecurityDisabled() {
        return (_disabler != null);
    }

    @Override
    protected ResourceFilter getPreFilter() {
        return null;
    }

    @Override
    protected ResourceFilter getPostFilter() {
        return null;
    }

    @Override
    protected AbstractPermissionFilter getPermissionsFilter(Role[] roles, ACL[] acls, boolean blockProxies, Class resourceClazz) {
        return new SyssvcPermissionFilter(roles, acls, blockProxies, resourceClazz, _permissionsHelper);
    }

    @Override
    protected boolean isLicenseCheckDisabled() {
        // Check - always true for syssvc, since syssvc api doesn't need any license
        return true;
    }

    @Override
    protected AbstractLicenseFilter getLicenseFilter() {
        return null;
    }
}
