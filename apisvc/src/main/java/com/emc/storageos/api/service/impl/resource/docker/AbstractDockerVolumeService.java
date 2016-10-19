/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.docker;

import java.net.URI;

import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public abstract class AbstractDockerVolumeService extends TaskResourceService {

    @Override
    protected StorageOSUser getUserFromContext() {
        if (!hasValidUserInContext()) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        return (StorageOSUser) sc.getUserPrincipal();
    }

    /**
     * Returns the bean responsible for servicing the request
     * 
     * @param vpool
     *            Virtual Pool
     * @return block service implementation object
     */
    protected static BlockServiceApi getBlockServiceImpl(VirtualPool vpool, DbClient dbClient) {
        // Mutually exclusive logic that selects an implementation of the block
        // service
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.rp.name());
        } else if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.vplex.name());
        } else if (VirtualPool.vPoolSpecifiesSRDF(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.srdf.name());
        } else if (VirtualPool.vPoolSpecifiesMirrors(vpool, dbClient)) {
            return BlockService.getBlockServiceImpl("mirror");
        } else if (vpool.getMultivolumeConsistency() != null && vpool.getMultivolumeConsistency()) {
            return BlockService.getBlockServiceImpl("group");
        }

        return BlockService.getBlockServiceImpl("default");
    }

    /**
     * Get varray from the given label
     * 
     * 
     * @prereq none
     * 
     * @param varray_name
     * @param user
     * 
     * @brief get varray
     * @return varray
     */
    protected VirtualArray getVarray(String varrayName) {

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(PrefixConstraint.Factory.getLabelPrefixConstraint(VirtualArray.class, varrayName),
                uris);
        if (uris != null) {
            while (uris.iterator().hasNext()) {
                URI varrayUri = uris.iterator().next();
                VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayUri);
                if (varray != null && !varray.getInactive())
                    return varray;
            }
        }
        return null; // no matching varray found
    }

    protected VirtualPool getVPool(String vPoolName) {

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(PrefixConstraint.Factory.getLabelPrefixConstraint(VirtualPool.class, vPoolName),
                uris);
        if (uris != null) {
            while (uris.iterator().hasNext()) {
                URI vPoolUri = uris.iterator().next();
                VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, vPoolUri);
                if (vPool != null && !vPool.getInactive())
                    return vPool;
            }
        }
        return null; // no matching vpool found
    }

    /**
     * Get project from the OpenStack Tenant ID parameter
     * 
     * 
     * @prereq none
     * 
     * @param openstackTenantId
     * @param user
     *            - with user credential details
     * 
     * @brief get project fro given tenant_id
     * @return Project
     */
    protected Project getProject(String openstackTenantId, StorageOSUser user) {
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(Project.class, openstackTenantId),
                uris);
        for (URI projectUri : uris) {
            Project project = _dbClient.queryObject(Project.class, projectUri);
            if (project != null && isAuthorized(projectUri, user))
                return project;
            else if (!isAuthorized(projectUri, user)) {
                throw APIException.badRequests.accessDenied();
            }
        }
        return null; // no project found
    }

    // Helper function to check if the user has authorization to access the
    // project
    // This is used by all search functions
    private boolean isAuthorized(URI projectUri, StorageOSUser user) {
        if (_permissionsHelper == null)
            return false;
        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        if (project == null)
            return false;
        if ((_permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(), Role.SYSTEM_MONITOR,
                Role.TENANT_ADMIN) || _permissionsHelper.userHasGivenACL(user, projectUri, ACL.ANY))) {
            return true;
        } else
            return false;
    }

}
