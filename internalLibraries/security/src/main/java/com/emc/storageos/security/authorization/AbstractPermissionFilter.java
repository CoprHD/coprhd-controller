/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

import java.net.URI;
import java.security.Principal;
import java.text.CollationElementIterator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import org.springframework.util.CollectionUtils;

/**
 * Abstract filter class for permission checker, implements filter method
 */
public abstract class AbstractPermissionFilter implements ResourceFilter, ContainerRequestFilter {
    private static final Logger _log = LoggerFactory.getLogger(AbstractPermissionFilter.class);
    protected final Role[] _roles;
    protected final ACL[] _acls;
    protected final boolean _blockProxies;
    protected Class<?> _resourceClazz;
    protected BasePermissionsHelper _permissionsHelper;

    protected AbstractPermissionFilter(Role[] roles, ACL[] acls, boolean blockProxies,
            Class resourceClazz, BasePermissionsHelper helper) {
        // To Do - sort permissions
        _roles = (roles != null) ? roles : new Role[] {};
        _acls = (acls != null) ? acls : new ACL[] {};
        _blockProxies = blockProxies;
        _resourceClazz = resourceClazz;
        _permissionsHelper = helper;
    }

    @Override
    public ContainerRequestFilter getRequestFilter() {
        return this;
    }

    @Override
    public ContainerResponseFilter getResponseFilter() {
        return null;
    }

    /**
     * Get tenant id from the uri
     * 
     * @return
     */
    protected abstract URI getTenantIdFromURI(final UriInfo uriInfo);

    /**
     * Get project id from the uri
     * 
     * @return
     */
    protected abstract URI getProjectIdFromURI(final UriInfo uriInfo);

    /**
     * Get usage acls for the given tenant on the resource
     * 
     * @param tenantId
     * @return
     */
    protected abstract Set<String> getUsageAclsFromURI(String tenantId, final UriInfo uriInfo);

    /**
     * Get UriInfo from the context
     * 
     * @return
     */
    protected abstract UriInfo getUriInfo();

    /**
     * Get tenant ids from the uri
     *
     * @return
     */
    protected abstract Set<URI> getTenantIdsFromURI(UriInfo uriInfo);

    /**
     * ContainerRequestFilter - checks to see if one of the specified
     * permissions exists for the user, if not throws
     * APIException.forbidden.insufficientPermissionsForUser
     * 
     * @param request
     * @return ContainerRequest
     */
    @Override
    public ContainerRequest filter(ContainerRequest request) {
        Principal p = request.getUserPrincipal();
        if (!(p instanceof StorageOSUser)) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        StorageOSUser user = (StorageOSUser) p;
        if (_blockProxies && user.isProxied()) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        boolean good = false;
        // Step 1: Roles check - see if the user has one of the allowed roles
        Set<String> tenantRoles = null;
        for (Role role : _roles) {
            if (user.getRoles().contains(role.toString())) {
                good = true;
                break;
            }
            if (_permissionsHelper.isRoleTenantLevel(role.toString())) {
                if (tenantRoles == null) {
                    try {
                        URI tenantId = getTenantIdFromURI(getUriInfo());
                        tenantRoles = _permissionsHelper.getTenantRolesForUser(user, tenantId,
                                isIdEmbeddedInURL(tenantId));
                        if (CollectionUtils.isEmpty(tenantRoles)) {
                            tenantRoles = getTenantRolesFromResource(user);
                        }
                    } catch (DatabaseException ex) {
                        throw APIException.forbidden.failedReadingTenantRoles(ex);
                    }
                }
                if (tenantRoles != null && tenantRoles.contains(role.toString())) {
                    good = true;
                    break;
                }
            }
        }
        // Step 2: if we are still not good, start checking for acls
        if (!good && _acls.length > 0) {
            // grab all acls from the resource
            Set<String> acls = new HashSet<String>();
            URI projectId = getProjectIdFromURI(getUriInfo());
            if (projectId != null) {
                try {
                    acls = _permissionsHelper.getProjectACLsForUser(user, projectId,
                            isIdEmbeddedInURL(projectId));
                } catch (DatabaseException ex) {
                    throw APIException.forbidden.failedReadingProjectACLs(ex);
                }
            } else { /* other resource acls */
                // these acls are assigned to tenant, so enhanced to check not only user's home tenant,
                // but also need to take into consideration of subtenants, which user has tenant roles.
                acls = getUsageAclsFromURI(user.getTenantId(), getUriInfo());
                for (String subtenantId : _permissionsHelper.getSubtenantsForUser(user)) {
                    acls.addAll(getUsageAclsFromURI(subtenantId, getUriInfo()));
                }
            }
            // see if we got any and we got a hit
            if (acls != null) {
                for (ACL acl : _acls) {
                    if (acl.equals(ACL.ANY) &&
                            (acls.contains(ACL.OWN.toString()) ||
                                    acls.contains(ACL.BACKUP.toString()) ||
                            acls.contains(ACL.ALL.toString()))) {
                        good = true;
                        break;
                    } else if (acls.contains(acl.toString())) {
                        good = true;
                        break;
                    }
                }
            }
        }
        // still not good, its not allowed
        if (!good) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        return request;
    }

    private Set<String> getTenantRolesFromResource(StorageOSUser user) {
        Set<String> tenantRoles = null;
        Set<URI> tenantIds = getTenantIdsFromURI(getUriInfo());
        if (CollectionUtils.isEmpty(tenantIds)) {
            return tenantRoles;
        }

        Iterator<URI> tenantIdIterator = tenantIds.iterator();
        while (tenantIdIterator.hasNext()) {
            URI tenantId = tenantIdIterator.next();
            Set<String> localTenantRoles = _permissionsHelper.getTenantRolesForUser(user, tenantId,
                    isIdEmbeddedInURL(tenantId));
            if(CollectionUtils.isEmpty(localTenantRoles)) {
                continue;
            }

            if (tenantRoles == null) {
                tenantRoles = localTenantRoles;
            } else {
                tenantRoles.addAll(localTenantRoles);
            }
        }
        return tenantRoles;
    }

    protected boolean isIdEmbeddedInURL(URI id) {
        if (id == null) {
            return false;
        }
        return isIdEmbeddedInURL(id.toString());
    }

    protected boolean isIdEmbeddedInURL(String id) {
        boolean idEmbeddedInURL = false;

        try {

            String uriStr = getUriInfo().getPathParameters().getFirst("id");
            if (uriStr.equals(id)) {
                idEmbeddedInURL = true;
            }
        } catch (Exception ex) {
            idEmbeddedInURL = false;
        }

        return idEmbeddedInURL;
    }
}
