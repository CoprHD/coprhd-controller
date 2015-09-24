/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.*;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract filter factory for resource filters
 */
public abstract class AbstractPermissionsFilterFactory implements com.sun.jersey.spi.container.ResourceFilterFactory {
    /**
     * filter to clear the object cache
     */
    private class PostProcessingFilter implements ResourceFilter, ContainerResponseFilter {
        @Override
        public ContainerRequestFilter getRequestFilter() {
            return null;
        }

        @Override
        public ContainerResponseFilter getResponseFilter() {
            return this;
        }

        @Override
        public ContainerResponse filter(ContainerRequest request, ContainerResponse resp) {
            QueriedObjectCache.clearCache();
            return resp;
        }
    }

    /**
     * Check if permissions filters need to be disabled
     * 
     * @return
     */
    protected abstract boolean isSecurityDisabled();

    /**
     * Check if licensing filter need to be disabled
     * 
     * @return
     */
    protected abstract boolean isLicenseCheckDisabled();

    /**
     * Create an instance of license check filter
     * 
     * @return
     */
    protected abstract AbstractLicenseFilter getLicenseFilter();

    /**
     * ResourceFilter that needs to be added in the front of the permissions filter
     * can be null
     * 
     * @return
     */
    protected abstract ResourceFilter getPreFilter();

    /**
     * ResourceFilter that needs to be added after the permissions filter
     * can be null
     * 
     * @return
     */
    protected abstract ResourceFilter getPostFilter();

    /**
     * Create and return the permissions filter from arguments provided
     * 
     * @param roles
     * @param acls
     * @param blockProxies
     * @param resourceClazz
     * @return
     */
    protected abstract AbstractPermissionFilter getPermissionsFilter(Role[] roles, ACL[] acls,
            boolean blockProxies, Class resourceClazz);

    /**
     * Check if we should do license check for given API call.
     * 
     * @param am
     * @return true for no license check, otherwise false
     */
    private boolean isExcludedForLicenseCheck(AbstractMethod am) {
        // Exclude DR APIs
        Path p = am.getAnnotation(Path.class);
        if (p != null && p.value().startsWith("/site")) {
            return true;
        }
        // add license check for all non GET methods that are not annotated with ExcludeLicenseCheck
        // except for POST requests on '/bulk' api
        if (am.getAnnotation(GET.class) == null && am.getAnnotation(ExcludeLicenseCheck.class) == null) {
            if (!(p != null && am.getAnnotation(POST.class) != null && p.value().endsWith("/bulk")) ) {
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    @Override
    public List<ResourceFilter> create(AbstractMethod am) {
        List<ResourceFilter> filters = new ArrayList<ResourceFilter>();
        ResourceFilter preFilter = getPreFilter();
        if (preFilter != null) {
            filters.add(preFilter);
        }

        // Adding license filter for all operations that should be forbidden without it.
        if (!isLicenseCheckDisabled()) {
            if (!isExcludedForLicenseCheck(am)) {
                filters.add(getLicenseFilter());
            }
        }

        if (!isSecurityDisabled()) {
            CheckPermission perms = am.getAnnotation(CheckPermission.class);
            if (perms != null) {
                filters.add(getPermissionsFilter(perms.roles(),
                        perms.acls(), perms.blockProxies(), am.getResource().getResourceClass()));
            } else {
                InheritCheckPermission inherit = am.getAnnotation(InheritCheckPermission.class);
                if (inherit != null) {
                    DefaultPermissions defaults =
                            am.getResource().getResourceClass().getAnnotation(DefaultPermissions.class);
                    if (defaults == null) {
                        throw new RuntimeException(String.format("%s does not have default permissions defined",
                                am.getResource().getResourceClass()));
                    }
                    if (inherit.writeAccess()) {
                        filters.add(getPermissionsFilter(defaults.writeRoles(),
                                defaults.writeAcls(), false, am.getResource().getResourceClass()));
                    } else {
                        filters.add(getPermissionsFilter(defaults.readRoles(),
                                defaults.readAcls(), false, am.getResource().getResourceClass()));
                    }
                }
            }
        }
        ResourceFilter postFilter = getPostFilter();
        if (postFilter != null) {
            filters.add(postFilter);
        }
        filters.add(new PostProcessingFilter());
        return filters;
    }
}
