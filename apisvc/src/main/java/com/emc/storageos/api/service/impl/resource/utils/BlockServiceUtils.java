/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import static com.emc.storageos.db.client.model.BlockMirror.SynchronizationState.FRACTURED;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;

/**
 * Utility class to hold generic, reusable block service methods
 */
public class BlockServiceUtils {

    private static Logger _log = LoggerFactory.getLogger(BlockServiceUtils.class);

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

        // Also check for snapshot sessions.
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(sourceVolURI));
        if (!snapSessions.isEmpty()) {
            throw APIException.badRequests.noFullCopiesForVMAX3VolumeWithActiveSnapshot(replicaType);
        }
    }

    /**
     * For VMAX, creating/deleting volume in/from CG with existing group relationship is supported for SMI-S provider version 8.0.3 or
     * higher
     * 
     * @param volume Volume part of the CG
     * @return true if the operation is supported.
     */
    public static boolean checkVolumeCanBeAddedOrRemoved(Volume volume, DbClient dbClient) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        return (storage != null && storage.deviceIsType(Type.vmax) && storage.getUsingSmis80());
    }

    /**
     * Return a list of active BlockMirror URI's that are known to be active
     * (in Synchronized state).
     * 
     * @param volume Volume to check for mirrors against
     * @param dbClient A reference to a database client.
     * 
     * @return List of active BlockMirror URI's
     */
    public static List<URI> getActiveMirrorsForVolume(Volume volume, DbClient dbClient) {
        List<URI> activeMirrorURIs = new ArrayList<>();
        if (hasMirrors(volume)) {
            Collection<URI> mirrorUris = transform(volume.getMirrors(), FCTN_STRING_TO_URI);
            List<BlockMirror> mirrors = dbClient.queryObject(BlockMirror.class, mirrorUris);
            for (BlockMirror mirror : mirrors) {
                if (!FRACTURED.toString().equalsIgnoreCase(mirror.getSyncState())) {
                    activeMirrorURIs.add(mirror.getId());
                }
            }
        }
        return activeMirrorURIs;
    }

    /**
     * Determines if the passed volume has attached mirrors.
     * 
     * @param volume A reference to a Volume.
     * 
     * @return true if passed volume has attached mirrors, false otherwise.
     */
    public static boolean hasMirrors(Volume volume) {
        return volume.getMirrors() != null && !volume.getMirrors().isEmpty();
    }

    /**
     * Return a list of active VplexMirror URI's that are known to be active.
     * 
     * @param volume Volume to check for mirrors against.
     * @param dbClient A reference to a database client.
     * 
     * @return List of active VplexMirror URI's.
     */
    public static List<URI> getActiveMirrorsForVplexVolume(Volume volume, DbClient dbClient) {
        List<URI> activeMirrorURIs = new ArrayList<>();
        if (BlockServiceUtils.hasMirrors(volume)) {
            List<VplexMirror> mirrors = dbClient.queryObject(VplexMirror.class,
                    StringSetUtil.stringSetToUriList(volume.getMirrors()));
            for (VplexMirror mirror : mirrors) {
                if (!mirror.getInactive()) {
                    activeMirrorURIs.add(mirror.getId());
                }
            }
        }
        return activeMirrorURIs;
    }

    /**
     * Checks if there are any native array snapshots with the requested name.
     * 
     * @param requestedName A name requested for a new native array snapshot.
     * @param sourceURI The URI of the snapshot source.
     * @param dbClient A reference to a database client.
     */
    public static void checkForDuplicateArraySnapshotName(String requestedName, URI sourceURI, DbClient dbClient) {
        // We need to check the BlockSnapshotSession instances created using
        // the new Create Snapshot Session service as it creates a native
        // array snapshot.
        String modifiedRequestedName = ResourceOnlyNameGenerator.removeSpecialCharsForName(
                requestedName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(sourceURI));
        for (BlockSnapshotSession snapSession : snapSessions) {
            if (modifiedRequestedName.equals(snapSession.getSessionLabel())) {
                throw APIException.badRequests.duplicateLabel(requestedName);
            }
        }

        // We also need to check BlockSnapshot instances created on the source
        // using the existing Create Snapshot service. We only need to check
        // those BlockSnapshot instances which are not a linked target of a
        // BlockSnapshotSession instance.
        List<BlockSnapshot> sourceSnapshots = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshot.class, ContainmentConstraint.Factory.getVolumeSnapshotConstraint(sourceURI));
        for (BlockSnapshot snapshot : sourceSnapshots) {
            URIQueryResultList queryResults = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(
                    snapshot.getId()), queryResults);
            Iterator<URI> queryResultsIter = queryResults.iterator();
            if ((!queryResultsIter.hasNext()) && (modifiedRequestedName.equals(snapshot.getSnapsetLabel()))) {
                throw APIException.badRequests.duplicateLabel(requestedName);
            }
        }
    }

    /**
     * Gets the number of native array snapshots created for the source with
     * the passed URI.
     * 
     * @param sourceURI The URI of the source.
     * @param dbClient A reference to a database client.
     * 
     * @return The number of native array snapshots for the source.
     */
    public static int getNumNativeSnapshots(URI sourceURI, DbClient dbClient) {
        // The number of native array snapshots is determined by the
        // number of BlockSnapshotSession instances created for the
        // source using new Create Snapshot Session service.
        List<BlockSnapshotSession> snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(sourceURI));
        int numSnapshots = snapSessions.size();

        // Also, we must account for the native array snapshots associated
        // with the BlockSnapshot instances created using the existing Create
        // Block Snapshot service. These will be the BlockSnapshot instances
        // that are not a linked target for a BlockSnapshotSession instance.
        List<BlockSnapshot> sourceSnapshots = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshot.class, ContainmentConstraint.Factory.getVolumeSnapshotConstraint(sourceURI));
        for (BlockSnapshot snapshot : sourceSnapshots) {
            URIQueryResultList queryResults = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(
                    snapshot.getId()), queryResults);
            Iterator<URI> queryResultsIter = queryResults.iterator();
            if ((!queryResultsIter.hasNext()) &&
                    (snapshot.getTechnologyType().equals(TechnologyType.NATIVE.toString()))) {
                numSnapshots++;
            }
        }

        return numSnapshots;
    }

    /**
     * Given a list of Tenants and DataObject references, check if any of the DataObjects have pending
     * Tasks against them. If so, generate an error that this cannot be deleted.
     * 
     * @param tenants - in] List or Tenant URIs
     * @param dataObjects - [in] List of DataObjects to check
     * @param dbClient - Reference to a database client
     */
    public static void checkForPendingTasks(Collection<URI> tenants, Collection<? extends DataObject> dataObjects, DbClient dbClient) {
        Set<URI> objectURIsThatHavePendingTasks = getObjectURIsThatHavePendingTasks(tenants, dbClient);

        // Search through the list of Volumes to see if any are in the pending list
        List<String> pendingObjectLabels = new ArrayList<>();
        for (DataObject dataObject : dataObjects) {
            if (dataObject.getInactive()) {
                continue;
            }
            String label = dataObject.getLabel();
            if (label == null) {
                label = dataObject.getId().toString();
            }
            if (objectURIsThatHavePendingTasks.contains(dataObject.getId())) {
                pendingObjectLabels.add(label);
                // Remove entry, since we already found it was matched.
                objectURIsThatHavePendingTasks.remove(dataObject.getId());
            }
        }

        // If there are an pendingObjectLabels, then we found some objects that have
        // a pending task against them. Need to signal an error
        if (!pendingObjectLabels.isEmpty()) {
            String pendingListStr = Joiner.on(',').join(pendingObjectLabels);
            _log.warn(String.format(
                    "Attempted to execute operation against these resources while there are tasks pending against them: %s",
                    pendingListStr));
            throw APIException.badRequests.cannotExecuteOperationWhilePendingTask(pendingListStr);
        }
    }

    /**
     * Return a set of URIs referencing DataObject associated to the list of Tenants that have pending Tasks.
     * 
     * @param tenants - [in] List or Tenant URIs
     * @return Set or URIs referencing DataObjects that have pending Tasks against them
     * @param dbClient - Reference to a database client
     */
    public static Set<URI> getObjectURIsThatHavePendingTasks(Collection<URI> tenants, DbClient dbClient) {
        // Generate a set of Resource URIs that have pending Tasks against them
        Set<URI> urisHavingPendingTasks = new HashSet<>();
        for (URI tenant : tenants) {
            TaskUtils.ObjectQueryResult<Task> queryResult = TaskUtils.findTenantTasks(dbClient, tenant);
            while (queryResult.hasNext()) {
                Task task = queryResult.next();
                if (task == null || task.getCompletedFlag() || task.getInactive()) {
                    continue;
                }
                if (task.isPending()) {
                    urisHavingPendingTasks.add(task.getResource().getURI());
                }
            }
        }

        return urisHavingPendingTasks;
    }
}
