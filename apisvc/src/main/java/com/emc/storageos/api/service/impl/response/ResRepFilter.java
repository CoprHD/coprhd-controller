/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.response;

import java.net.URI;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;

/*  
 * Base class of resource reprensentation filter
 * Used by search for now
 */
public abstract class ResRepFilter<E extends RelatedResourceRep> {
    private static final Logger _log = LoggerFactory.getLogger(ResRepFilter.class);

    protected PermissionsHelper _permissionsHelper;
    protected StorageOSUser _user;

    public static class ResourceFilteringCache {
        public HashSet<URI> _accessibleParentResources = new HashSet<URI>();
        public HashSet<URI> _nonAccessibleParentResources = new HashSet<URI>();
    }

    private final ResourceFilteringCache _cache = new ResourceFilteringCache();

    protected ResRepFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        _user = user;
        _permissionsHelper = permissionsHelper;
    }

    /**
     * verify whether the user in the filter has access to the resource
     * 
     * @param relatedResourceRep the resource to be checked upon.
     * @return true if user can access the resource.
     */
    public abstract boolean isAccessible(E relatedResourceRep);

    /**
     * verify whether the user in the filter has access to tenant
     * 
     * @param tenant the tenant to be checked upon.
     * @return true if user can access the tenant.
     */
    public boolean isTenantAccessible(URI tenant) {
        ArgValidator.checkFieldNotNull(tenant, "tenant");
        if (tenant == null) {
            return false;
        }

        // refine cache later
        if (_cache._accessibleParentResources.contains(tenant)) {
            return true;
        }
        if (_cache._nonAccessibleParentResources.contains(tenant)) {
            return false;
        }

        boolean ret =
                _permissionsHelper.userHasGivenRole(
                        _user, tenant, Role.TENANT_ADMIN);
        if (ret) {
            _cache._accessibleParentResources.add(tenant);
            _log.info("user {} has TENANT_ADMIN role for tenant {}.",
                    _user.toString(), tenant.toString());
        } else {
            _cache._nonAccessibleParentResources.add(tenant);
            _log.info("user {} has not TENANT_ADMIN role for tenant {}.",
                    _user.toString(), tenant.toString());
        }
        return ret;
    }

    /**
     * verify whether the user in the filter has access to the project
     * 
     * @param project the project to be checked upon.
     * @return true if user can access the project.
     */
    public boolean isProjectAccessible(URI project) {
        if (project == null) {
            return false;
        }

        if (_cache._accessibleParentResources.contains(project)) {
            return true;
        }
        if (_cache._nonAccessibleParentResources.contains(project)) {
            return false;
        }

        boolean ret = _permissionsHelper.userHasGivenACL(
                _user, project, ACL.ANY);
        if (ret) {
            _cache._accessibleParentResources.add(project);
            _log.info("user {} has ACL.ANY for project {}.",
                    _user.toString(), project.toString());
        } else {
            _cache._nonAccessibleParentResources.add(project);
            _log.info("user {} has not ACL.ANY for project {}.",
                    _user.toString(), project.toString());
        }
        return ret;
    }

    /**
     * verify whether the user in the filter has access to the vpool
     * based on resource ACL
     * 
     * @return true if user can access the resource.
     */
    public boolean isVirtualPoolAccessible(VirtualPool resource) {
        return _permissionsHelper.tenantHasUsageACL(
                URI.create(_user.getTenantId()), resource);
    }

    /**
     * verify whether the user in the filter has access to the computeVirtualpool
     * based on resource ACL
     * 
     * @return true if user can access the resource.
     */
    public boolean isComputeVirtualPoolAccessible(ComputeVirtualPool resource) {
        return _permissionsHelper.tenantHasUsageACL(
                URI.create(_user.getTenantId()), resource);
    }

    /**
     * verify whether the user in the filter has access to the neighbor
     * based on resource ACL
     * 
     * @return true if user can access the resource.
     */
    public boolean isVirtualArrayAccessible(VirtualArray resource) {
        return _permissionsHelper.tenantHasUsageACL(
                URI.create(_user.getTenantId()), resource);
    }

}
