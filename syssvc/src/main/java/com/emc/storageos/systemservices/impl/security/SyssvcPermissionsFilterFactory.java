/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.security;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.security.SecurityDisabler;
import com.emc.storageos.security.authorization.*;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;

import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Class implements ResourceFilterFactory to add permissions filter where needed
 */
public class SyssvcPermissionsFilterFactory extends AbstractPermissionsFilterFactory {
    private static final List<String> FORBIDDEN_PATHS = Arrays.asList("backupset", "control/cluster/recovery");
    private BasePermissionsHelper _permissionsHelper;

    @Autowired(required = false)
    private SecurityDisabler _disabler;

    private @Context
    UriInfo uriInfo;

    private boolean isStandby = false; // default to false

    public void setIsStandby(boolean isStandby) {
        this.isStandby = isStandby;
    }

    @Autowired
    private DrUtil drUtil;

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

        /**
         * Get tenant ids from the uri
         *
         * @return
         */
        @Override
        protected Set<URI> getTenantIdsFromURI(UriInfo uriInfo) {
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
        return new StandbySyssvcFilter();
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
    /**
     * Request filter for syssvc on standby node. We disable backup reuquests on standby.
     */
    private class StandbySyssvcFilter implements ResourceFilter, ContainerRequestFilter {
        @Override
        public ContainerRequest filter(ContainerRequest request) {
            // allow all request on active site
            // use a injected variable rather than querying with DrUtil every time
            // because if a ZK quorum is lost on the active site all the ZK accesses will fail
            // note that readonly mode is not enabled on the active site.
            if (!isStandby) {
                return request;
            }
            String path = request.getPath();
            if (isPathForbidden(path)) {
                throw APIException.forbidden.disallowOperationOnDrStandby(drUtil.getActiveSite().getVip());
            }
            return request;
        }

        private boolean isPathForbidden(String path) {
            for (String forbid : FORBIDDEN_PATHS) {
                if (path.startsWith(forbid)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public ContainerRequestFilter getRequestFilter() {
            return this;
        }

        @Override
        public ContainerResponseFilter getResponseFilter() {
            return null;
        }
    }
}
