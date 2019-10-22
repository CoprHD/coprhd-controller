/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import static com.emc.storageos.db.client.model.SynchronizationState.FRACTURED;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.util.TagUtils;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

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
            } else if (blockObject.checkInternalFlags(Flag.INTERNAL_OBJECT)
                    && blockObject.checkInternalFlags(Flag.SUPPORTS_FORCE)
                    && !force) {
                throw APIException.badRequests.notSupportedForInternalVolumes();
            }
        }
    }

    /**
     * Validate that the passed block object is not marked as a boot volume.
     *
     * @param blockObject A reference to a BlockObject
     * @param force true if an operation should be forced regardless of whether
     *            or not the passed block object is an internal object, false
     *            otherwise.
     */
    public static void validateNotABootVolume(BlockObject blockObject, boolean force) {
        if (blockObject != null && blockObject.getTag() != null) {
            Iterator<ScopedLabel> slIter = blockObject.getTag().iterator();
            boolean taggedAsBootVolume = false;
            while (slIter.hasNext()) {
                ScopedLabel sl = slIter.next();
                if (sl.getLabel().startsWith(TagUtils.getBootVolumeTagName())) {
                    taggedAsBootVolume = true;
                }
            }
            
            if (taggedAsBootVolume && !force) {
                throw APIException.badRequests.notSupportedForBootVolumes();
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
     * Fox XtremIO creating/deleting volume in/from CG with existing CG is supported.
     *
     * For VNX, creating/deleting volume in/from CG with existing group relationship is supported if volume is not part of an array
     * replication group
     *
     * For Application support, allow volumes to be added/removed to/from CG for VPLEX when the backend volume is VMAX/VNX/XtremIO
     *
     * @param cg BlockConsistencyGroup
     * @param volume Volume part of the CG
     * @dbClient DbClient
     * @return true if the operation is supported.
     */
    public static boolean checkCGVolumeCanBeAddedOrRemoved(BlockConsistencyGroup cg, Volume volume, DbClient dbClient) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        if (storage != null) {
            if (storage.deviceIsType(Type.vmax)) {
                if (storage.getUsingSmis80()) {
                    return true;
                }
            } else if (storage.deviceIsType(Type.vnxblock)) {
                BlockConsistencyGroup consistencyGroup = cg;
                if (consistencyGroup == null) {
                    consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                }

                if (consistencyGroup != null && !consistencyGroup.getInactive()) {
                    return !consistencyGroup.getArrayConsistency();
                }
            } else if (storage.deviceIsType(Type.xtremio)) {
                return true;
            } else if (storage.deviceIsType(Type.unity) && volume.checkForRp()) {
                return true;
            }

            if (storage.deviceIsType(Type.vplex)) {
                Set<Type> applicationSupported = Sets.newHashSet(Type.vmax, Type.vnxblock, Type.xtremio, Type.unity);
                Set<Type> backendSystemTypes = new HashSet<>();

                if (volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty()) {
                    for (String associatedVolumeId : volume.getAssociatedVolumes()) {
                        Volume associatedVolume = dbClient.queryObject(Volume.class,
                                URI.create(associatedVolumeId));
                        if (associatedVolume != null) {
                            StorageSystem backendSystem = dbClient.queryObject(StorageSystem.class,
                                    associatedVolume.getStorageController());
                            if (backendSystem != null && !Strings.isNullOrEmpty(backendSystem.getSystemType())) {
                                backendSystemTypes.add(Type.valueOf(backendSystem.getSystemType()));
                            }
                        }
                    }
                }

                // Application support: Allow volumes to be added/removed to/from CG for VPLEX and RP
                // when the backend volume is VMAX/VNX/XtremIO
                if (volume.getApplication(dbClient) != null) {
                    // Returns true, if any backendSystemTypes are in the supported set for applications
                    return !Collections.disjoint(applicationSupported, backendSystemTypes);
                } else if (!Volume.checkForRP(dbClient, volume.getId())) {
                    // Returns true, for VPLEX&VMAX scenarios
                    return (backendSystemTypes.contains(Type.vmax) || backendSystemTypes.contains(Type.xtremio));
                }

            }
        }

        return false;
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
     * Group volumes by array group.
     *
     * @param volumes the volumes
     * @return the map of array group to volumes
     */
    public static Map<String, List<Volume>> groupVolumesByArrayGroup(List<Volume> volumes) {
        Map<String, List<Volume>> arrayGroupToVolumes = new HashMap<String, List<Volume>>();
        for (Volume volume : volumes) {
            String repGroupName = volume.getReplicationGroupInstance();
            if (arrayGroupToVolumes.get(repGroupName) == null) {
                arrayGroupToVolumes.put(repGroupName, new ArrayList<Volume>());
            }
            arrayGroupToVolumes.get(repGroupName).add(volume);
        }
        return arrayGroupToVolumes;
    }

    /**
     * Checks if there are any native array snapshots with the requested name.
     *
     * @param requestedName A name requested for a new native array snapshot.
     * @param sourceURI The URI of the snapshot source.
     * @param dbClient A reference to a database client.
     */
    public static void checkForDuplicateArraySnapshotName(String requestedName, URI sourceURI, DbClient dbClient) {
        // First ensure the requested snapshot name is no more than 63 characters in length.
        // If we remove special characters and truncate to 63 characters, this could lead
        // to invalid duplicate name exceptions (COP-14512). By restricting to 63 characters in total, this
        // won't happen.
        ArgValidator.checkFieldLengthMaximum(requestedName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH, "snapshotName");

        // We need to check the BlockSnapshotSession instances created using
        // the new Create Snapshot Session service as it creates a native
        // array snapshot.
        String modifiedRequestedName = ResourceOnlyNameGenerator.removeSpecialCharsForName(
                requestedName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        List<BlockSnapshotSession> snapSessions = null;
        Volume sourceVolume = null;
        if (URIUtil.isType(sourceURI, Volume.class)) {
            sourceVolume = dbClient.queryObject(Volume.class, sourceURI);
        }
        if (sourceVolume != null && NullColumnValueGetter.isNotNullValue(sourceVolume.getReplicationGroupInstance())) {
            snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                    BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getBlockSnapshotSessionByConsistencyGroup(sourceVolume.getConsistencyGroup()));
        } else {
            snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                    BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(sourceURI));
        }
        for (BlockSnapshotSession snapSession : snapSessions) {
            if (modifiedRequestedName.equals(snapSession.getSessionLabel())) {
                throw APIException.badRequests.duplicateLabel(requestedName);
            }
        }

        // We also need to check BlockSnapshot instances created on the source
        // using the existing Create Snapshot service.
        List<BlockSnapshot> sourceSnapshots = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                BlockSnapshot.class, ContainmentConstraint.Factory.getVolumeSnapshotConstraint(sourceURI));
        for (BlockSnapshot snapshot : sourceSnapshots) {
            if (modifiedRequestedName.equals(snapshot.getSnapsetLabel())) {
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
                    (TechnologyType.NATIVE.toString().equalsIgnoreCase(snapshot.getTechnologyType()))) {
                numSnapshots++;
            }
        }

        return numSnapshots;
    }

    /**
     * Creates a Task on given Volume with Error state
     *
     * @param opr the opr
     * @param volume the volume
     * @param sc the sc
     * @return the failed task for volume
     */
    public static TaskResourceRep createFailedTaskOnVolume(DbClient dbClient,
            Volume volume, ResourceOperationTypeEnum opr, ServiceCoded sc) {
        String taskId = UUID.randomUUID().toString();
        Operation op = new Operation();
        op.setResourceType(opr);
        dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId, op);

        volume = dbClient.queryObject(Volume.class, volume.getId());
        op = volume.getOpStatus().get(taskId);
        op.error(sc);
        volume.getOpStatus().updateTaskStatus(taskId, op);
        dbClient.updateObject(volume);
        return TaskMapper.toTask(volume, taskId, op);
    }

    /**
     * Creates a Task on given CG with Error state
     *
     * @param opr the opr
     * @param cg the consistency group
     * @param sc the sc
     * @return the failed task for cg
     */
    public static TaskResourceRep createFailedTaskOnCG(DbClient dbClient,
            BlockConsistencyGroup cg, ResourceOperationTypeEnum opr, ServiceCoded sc) {
        String taskId = UUID.randomUUID().toString();
        Operation op = new Operation();
        op.setResourceType(opr);
        dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(), taskId, op);

        cg = dbClient.queryObject(BlockConsistencyGroup.class, cg.getId());
        op = cg.getOpStatus().get(taskId);
        op.error(sc);
        cg.getOpStatus().updateTaskStatus(taskId, op);
        dbClient.updateObject(cg);
        return TaskMapper.toTask(cg, taskId, op);
    }

    /**
     * Creates a Task on given snapshot session with Error state
     *
     * @param opr the opr
     * @param session the snap session
     * @param sc the sc
     * @return the failed task for snap session
     */
    public static TaskResourceRep createFailedTaskOnSnapshotSession(DbClient dbClient,
            BlockSnapshotSession session, ResourceOperationTypeEnum opr, ServiceCoded sc) {
        String taskId = UUID.randomUUID().toString();
        Operation op = new Operation();
        op.setResourceType(opr);
        dbClient.createTaskOpStatus(BlockSnapshotSession.class, session.getId(), taskId, op);

        session = dbClient.queryObject(BlockSnapshotSession.class, session.getId());
        op = session.getOpStatus().get(taskId);
        op.error(sc);
        session.getOpStatus().updateTaskStatus(taskId, op);
        dbClient.updateObject(session);
        return TaskMapper.toTask(session, taskId, op);
    }

    /**
     * Given a Tenant and DataObject references, check if any of the DataObjects have pending
     * Tasks against them. If so, generate an error that this cannot be deleted.
     *
     * @param tenant - [in] Tenant URI
     * @param dataObjects - [in] List of DataObjects to check
     * @param dbClient - Reference to a database client
     */
    public static void checkForPendingTasks(URI tenant, Collection<? extends DataObject> dataObjects, DbClient dbClient) {
        // First, find tasks for the resources sent in.
        Set<URI> objectURIsThatHavePendingTasks = new HashSet<URI>();

        // Get a unique list of Task objects associated with the data objects
        for (DataObject dataObject : dataObjects) {
            List<Task> newTasks = TaskUtils.findResourceTasks(dbClient, dataObject.getId());
            for (Task newTask : newTasks) {
                if (newTask.isPending() && newTask.getTenant().equals(tenant)) {
                    objectURIsThatHavePendingTasks.add(dataObject.getId());
                }
            }
        }

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
     * Group volumes by storage system and replication group
     *
     * @param volumeUris List of volumes (part or all) in a volume group
     * @param cgUri
     * @param dbClient
     * @return table with storage URI, replication group name, and volumes
     */
    public static Table<URI, String, List<Volume>> getReplicationGroupVolumes(List<URI> volumeUris, URI cgUri, DbClient dbClient,
            UriInfo uriInfo) {
        // Group volumes by storage system and replication group
        Table<URI, String, List<Volume>> storageRgToVolumes = HashBasedTable.create();
        for (URI volumeUri : volumeUris) {
            ArgValidator.checkFieldUriType(volumeUri, Volume.class, "volume");
            Volume volume = dbClient.queryObject(Volume.class, volumeUri);
            ArgValidator.checkEntity(volume, volumeUri, isIdEmbeddedInURL(volumeUri, uriInfo));
            if (!volume.isInCG() || !volume.getConsistencyGroup().equals(cgUri)) {
                throw APIException.badRequests.invalidParameterSourceVolumeNotInGivenConsistencyGroup(volumeUri, cgUri);
            }

            String label = volume.getLabel();
            boolean isVPlex = volume.isVPlexVolume(dbClient);
            if (isVPlex) {
                // get backend source volume to get RG name
                volume = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
                if (volume == null || volume.getInactive()) {
                    throw APIException.badRequests.noBackendVolume(label);
                }
            }

            String rgName = volume.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNullValue(rgName)) {
                throw APIException.badRequests.noRepGroupInstance(volume.getLabel());
            }

            URI storage = volume.getStorageController();
            if (!storageRgToVolumes.contains(storage, rgName)) {
                List<Volume> volumes = ControllerUtils.getVolumesPartOfRG(storage, rgName, dbClient);
                if (isVPlex) {
                    List<Volume> vplexVolumes = new ArrayList<Volume>();
                    for (Volume backendVol : volumes) {
                        Volume vplexVol = Volume.fetchVplexVolume(dbClient, backendVol);
                        if (vplexVol == null || vplexVol.getInactive()) {
                            throw APIException.badRequests.noVPLEXVolume(backendVol.getLabel());
                        }
                        vplexVolumes.add(vplexVol);
                    }

                    volumes = vplexVolumes;
                }

                storageRgToVolumes.put(storage, rgName, volumes);
            }
        }

        return storageRgToVolumes;
    }

    /**
     * Group CG volumes by storage system and replication group
     *
     * @param srcVolumes List of all volumes in a CG
     * @param dbClient
     * @return table with storage URI, replication group name, and volumes
     */
    public static Table<URI, String, List<Volume>> getReplicationGroupVolumes(List<Volume> srcVolumes, DbClient dbClient) {
        // Group volumes by storage system and replication group
        Table<URI, String, List<Volume>> storageRgToVolumes = HashBasedTable.create();
        for (Volume volume : srcVolumes) {
            String rgName = null;
            URI storage = null;
            if (volume.isVPlexVolume(dbClient)) {
                // get backend source volume to get RG name
                Volume backedVol = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
                if (backedVol != null) {
                    rgName = backedVol.getReplicationGroupInstance();
                    storage = backedVol.getStorageController();
                }
            } else {
                rgName = volume.getReplicationGroupInstance();
                storage = volume.getStorageController();
            }

            if (NullColumnValueGetter.isNullValue(rgName)) {
                throw APIException.badRequests.noRepGroupInstance(volume.getLabel());
            }

            List<Volume> volumes = storageRgToVolumes.get(storage, rgName);
            if (volumes == null) {
                volumes = new ArrayList<Volume>();
                storageRgToVolumes.put(storage, rgName, volumes);
            }
            volumes.add(volume);
        }

        return storageRgToVolumes;
    }

    public static BlockSnapshot querySnapshotResource(URI snapshotURI, UriInfo uriInfo, DbClient dbClient) {
        ArgValidator.checkFieldUriType(snapshotURI, BlockSnapshot.class, "snapshots");
        BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
        ArgValidator.checkEntity(snapshot, snapshotURI,
                BlockServiceUtils.isIdEmbeddedInURL(snapshotURI, uriInfo), true);
        return snapshot;
    }

    /**
     * validate volume with no replica
     *
     * @param volume
     * @param application
     * @param dbClient
     */
    public static void validateVolumeNoReplica(Volume volume, VolumeGroup application, DbClient dbClient) {
        // check if the volume has any replica
        // no need to check backing volumes for vplex virtual volumes because for full copies
        // there will be a virtual volume for the clone
        boolean hasReplica = volume.getFullCopies() != null && !volume.getFullCopies().isEmpty() ||
                volume.getMirrors() != null && !volume.getMirrors().isEmpty();

        // check for snaps only if no full copies
        if (!hasReplica) {
            Volume snapSource = volume;
            if (volume.isVPlexVolume(dbClient)) {
                snapSource = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
                if (snapSource == null || snapSource.getInactive()) {
                    return;
                }
            }

            hasReplica = ControllerUtils.checkIfVolumeHasSnapshot(snapSource, dbClient);

            // check for VMAX3 individual session and group session
            if (!hasReplica && snapSource.isVmax3Volume(dbClient)) {
                hasReplica = ControllerUtils.checkIfVolumeHasSnapshotSession(snapSource.getId(), dbClient);

                String rgName = snapSource.getReplicationGroupInstance();
                if (!hasReplica && NullColumnValueGetter.isNotNullValue(rgName)) {
                    URI cgURI = snapSource.getConsistencyGroup();
                    List<BlockSnapshotSession> sessionsList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                            BlockSnapshotSession.class,
                            ContainmentConstraint.Factory.getBlockSnapshotSessionByConsistencyGroup(cgURI));

                    for (BlockSnapshotSession session : sessionsList) {
                        if (rgName.equals(session.getReplicationGroupInstance())) {
                            hasReplica = true;
                            break;
                        }
                    }
                }
            }
        }

        if (hasReplica) {
            throw APIException.badRequests.volumeGroupCantBeUpdated(application.getLabel(),
                    String.format("the volume %s has replica. please remove all replicas from the volume", volume.getLabel()));
        }
    }

    /**
     * Check if a unity volume could be add or removed from unity consistency group. for Unity, if the unity CG has snapshot, volumes could
     * not be added or removed.
     * 
     * @param rgName Unity consistency group name
     * @param volume Unity volume to be added or removed
     * @param dbClient
     * @param isAdd If the volume is for add
     * @return true if the volume could be added or removed
     */
    public static boolean checkUnityVolumeCanBeAddedOrRemovedToCG(String rgName, Volume volume, DbClient dbClient, boolean isAdd) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        if (storage != null) {
            if (storage.deviceIsType(Type.unity)) {
                if (isAdd && rgName != null) {
                    List<Volume> volumesInRG = CustomQueryUtility.queryActiveResourcesByConstraint(
                            dbClient, Volume.class, AlternateIdConstraint.Factory.getVolumeByReplicationGroupInstance(rgName));
                    if (volumesInRG != null && !volumesInRG.isEmpty()) {
                        for (Volume vol : volumesInRG) {
                            if (vol.getStorageController().equals(volume.getStorageController())) {
                                // Check if the volume in RG has snapshot
                                List<BlockSnapshot> snaps = getVolumeNativeSnapshots(vol.getId(), dbClient);
                                if (!snaps.isEmpty()) {
                                    return false;
                                }
                            }
                        }
                    }
                } else if (!isAdd) {
                    // for remove, Check if the volume has snapshot
                    List<BlockSnapshot> snaps = getVolumeNativeSnapshots(volume.getId(), dbClient);
                    if (!snaps.isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Get volume's block snapshots, whose technologyType attributes is NATIVE
     * 
     * @param volumeUri The volume URI
     * @param dbClient
     * @return The list of block snapshot for the given volume.
     */
    public static List<BlockSnapshot> getVolumeNativeSnapshots(URI volumeUri, DbClient dbClient) {
        List<BlockSnapshot> result = new ArrayList<BlockSnapshot>();
        URIQueryResultList snapshotURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                volumeUri), snapshotURIs);
        Iterator<URI> it = snapshotURIs.iterator();
        while (it.hasNext()) {
            URI snapUri = it.next();
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapUri);
            if (snapshot != null && !snapshot.getInactive() &&
                    BlockSnapshot.TechnologyType.NATIVE.name().equalsIgnoreCase(snapshot.getTechnologyType())) {
                result.add(snapshot);
            }

        }
        return result;
    }

}
