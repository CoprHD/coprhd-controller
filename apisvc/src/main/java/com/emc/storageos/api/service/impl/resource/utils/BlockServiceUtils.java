/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Utility class to hold generic, reusable block service methods
 */
public class BlockServiceUtils {

    /**
     * Validate that the passed block object is not an internal block object,
     * such as a backend volume for a VPLEX volume. If so, throw a bad request
     * exception unless the SUPPORTS_FORCE flag is present AND force is true.
     * 
     * @param blockObject A reference to a BlockObject
     * @param force true if an operation should be forced regardless of whether
     *            or not the passed block object is an internal object, false
     *            otherwise.
     */
    public static void validateNotAnInternalBlockObject(BlockObject blockObject, boolean force) {
        if (blockObject != null) {
            if (blockObject.checkInternalFlags(Flag.INTERNAL_OBJECT)
                    && !blockObject.checkInternalFlags(Flag.SUPPORTS_FORCE)) {
                throw APIException.badRequests.notSupportedForInternalVolumes();
            }
            else if (blockObject.checkInternalFlags(Flag.INTERNAL_OBJECT)
                    && blockObject.checkInternalFlags(Flag.SUPPORTS_FORCE)
                    && !force) {
                throw APIException.badRequests.notSupportedForInternalVolumes();
            }
        }
    }

    /**
     * Gets and verifies that the VirtualArray passed in the request is
     * accessible to the tenant.
     * 
     * @param project A reference to the project.
     * @param varrayURI The URI of the VirtualArray
     * 
     * @return A reference to the VirtualArray.
     */
    public static VirtualArray verifyVirtualArrayForRequest(Project project,
            URI varrayURI, UriInfo uriInfo, PermissionsHelper permissionsHelper, DbClient dbClient) {
        VirtualArray neighborhood = dbClient.queryObject(VirtualArray.class, varrayURI);
        ArgValidator.checkEntity(neighborhood, varrayURI, isIdEmbeddedInURL(varrayURI, uriInfo));
        permissionsHelper.checkTenantHasAccessToVirtualArray(project.getTenantOrg()
                .getURI(), neighborhood);
        return neighborhood;
    }

    /**
     * Determine if the unique id for a resource is embedded in the passed
     * resource URI.
     * 
     * @param resourceURI A resource URI.
     * @param uriInfo A reference to the URI info.
     * 
     * @return true if the unique id for a resource is embedded in the passed
     *         resource URI, false otherwise.
     */
    public static boolean isIdEmbeddedInURL(final URI resourceURI, UriInfo uriInfo) {
        ArgValidator.checkUri(resourceURI);
        return isIdEmbeddedInURL(resourceURI.toString(), uriInfo);
    }

    /**
     * Determine if the unique id for a resource is embedded in the passed
     * resource id.
     * 
     * @param resourceId A resource Id.
     * @param uriInfo A reference to the URI info.
     * 
     * @return true if the unique id for a resource is embedded in the passed
     *         resource Id, false otherwise.
     */
    public static boolean isIdEmbeddedInURL(final String resourceId, UriInfo uriInfo) {
        try {
            final Set<Entry<String, List<String>>> pathParameters = uriInfo
                    .getPathParameters().entrySet();
            for (final Entry<String, List<String>> entry : pathParameters) {
                for (final String param : entry.getValue()) {
                    if (param.equals(resourceId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore any errors and return false
        }

        return false;
    }

    /**
     * Verify the user is authorized for a request.
     * 
     * @param project A reference to the Project.
     */
    public static void verifyUserIsAuthorizedForRequest(Project project,
            StorageOSUser user, PermissionsHelper permissionsHelper) {
        if (!(permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(),
                Role.TENANT_ADMIN) || permissionsHelper.userHasGivenACL(user,
                project.getId(), ACL.OWN, ACL.ALL))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }

    /**
     * Get StorageOSUser from the passed security context.
     * 
     * @param securityContext A reference to the security context.
     * 
     * @return A reference to the StorageOSUser.
     */
    public static StorageOSUser getUserFromContext(SecurityContext securityContext) {
        if (!hasValidUserInContext(securityContext)) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        return (StorageOSUser) securityContext.getUserPrincipal();
    }

    /**
     * Determine if the security context has a valid StorageOSUser object.
     * 
     * @param securityContext A reference to the security context.
     * 
     * @return true if the StorageOSUser is present.
     */
    public static boolean hasValidUserInContext(SecurityContext securityContext) {
        if ((securityContext != null)
                && (securityContext.getUserPrincipal() instanceof StorageOSUser)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * For VMAX3, We can't create fullcopy/mirror when there are active snap sessions.
     * 
     * @TODO remove this validation when provider add support for this.
     * @param sourceVolURI
     * @param dbClient
     */
    public static void validateVMAX3ActiveSnapSessionsExists(URI sourceVolURI, DbClient dbClient, String replicaType) {
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(sourceVolURI),
                queryResults);
        Iterator<URI> queryResultsIter = queryResults.iterator();
        while (queryResultsIter.hasNext()) {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, queryResultsIter.next());
            if ((snapshot != null) && (!snapshot.getInactive()) && (snapshot.getIsSyncActive())) {
                throw APIException.badRequests.noFullCopiesForVMAX3VolumeWithActiveSnapshot(replicaType);
            }
        }
    }

    /**
     * For VMAX, creating/deleting volume in/from CG with existing group relationship is supported for SMI-S provider version 8.0.3 or higher
     *
     * @param volume Volume part of the CG
     * @return true if the operation is supported.
     */
    public static boolean checkVolumeCanBeAddedOrRemoved(Volume volume, DbClient dbClient) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        return (storage != null && storage.deviceIsType(Type.vmax) && storage.getUsingSmis80());
    }
    
    /**
     * Check if the storage system type is openstack, vnxblock, vmax or ibmxiv.
     * Snapshot full copy is supported only on these storage systems.
     * 
     * @param blockSnapURI SnapshotURI for which storage system type needs to be checked
     * @param dbClient DBClient object
     * @return
     */
    public static boolean isSnapshotFullCopySupported(URI blockSnapURI, DbClient dbClient) {
        BlockSnapshot blockObj = dbClient.queryObject(BlockSnapshot.class, blockSnapURI);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, blockObj.getStorageController());
        return (storage != null && (storage.deviceIsType(Type.openstack) 
                                    || storage.deviceIsType(Type.vnxblock)
                                    || storage.deviceIsType(Type.ibmxiv)
                                    || storage.deviceIsType(Type.vmax)));
    }
}
