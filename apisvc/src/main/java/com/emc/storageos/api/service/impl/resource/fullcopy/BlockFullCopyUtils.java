/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;

/**
 * Utilities class for processing fully copy request
 */
/**
 * @author cgarber
 *
 */
public class BlockFullCopyUtils {

    public static final String REPLICA_TYPE_FULL_COPY = "Full copy";
    public static final String REPLICA_TYPE_SNAPSHOT = "Snapshot";
    public static final String REPLICA_TYPE_CONTINUOUS_COPY = "Continuous copy";

    /**
     * Returns the volume or block snapshot instance for the passed URI.
     * 
     * @param fcResourceURI The URI for the Volume or BlockSnapshot instance.
     * @param uriInfo A reference to the URI information.
     * @param isSource true if the passed URI is for the full copy source, false otherwise.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the block object.
     */
    public static BlockObject queryFullCopyResource(URI fcResourceURI, UriInfo uriInfo,
            boolean isSource, DbClient dbClient) {
        ArgValidator.checkUri(fcResourceURI);
        if (isSource) {
            if ((!URIUtil.isType(fcResourceURI, Volume.class))
                    && (!URIUtil.isType(fcResourceURI, BlockSnapshot.class))) {
                throw APIException.badRequests.invalidFullCopySource(fcResourceURI.toString());
            }
        } else if (!URIUtil.isType(fcResourceURI, Volume.class)) {
            throw APIException.badRequests.protectionVolumeNotFullCopy(fcResourceURI);
        }
        BlockObject blockObj = BlockObject.fetch(dbClient, fcResourceURI);
        ArgValidator.checkEntity(blockObj, fcResourceURI,
                BlockServiceUtils.isIdEmbeddedInURL(fcResourceURI, uriInfo), true);
        return blockObj;
    }

    /**
     * Returns the project for the full copy source.
     * 
     * @param fcSourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the project for the full copy source.
     */
    public static Project queryFullCopySourceProject(BlockObject fcSourceObj, DbClient dbClient) {
        URI fcSourceURI = fcSourceObj.getId();
        URI projectURI = null;
        if (URIUtil.isType(fcSourceURI, Volume.class)) {
            projectURI = ((Volume) fcSourceObj).getProject().getURI();
        } else if (URIUtil.isType(fcSourceURI, BlockSnapshot.class)) {
            projectURI = ((BlockSnapshot) fcSourceObj).getProject().getURI();
        }

        if (projectURI == null) {
            throw APIException.badRequests.invalidFullCopySource(fcSourceURI.toString());
        }

        Project project = dbClient.queryObject(Project.class, projectURI);
        return project;
    }

    /**
     * Returns the vpool for the full copy source.
     * 
     * @param fcSourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the vpool for the full copy source.
     */
    public static VirtualPool queryFullCopySourceVPool(BlockObject fcSourceObj, DbClient dbClient) {
        URI fcSourceURI = fcSourceObj.getId();
        URI vpoolURI = null;
        if (URIUtil.isType(fcSourceURI, Volume.class)) {
            vpoolURI = ((Volume) fcSourceObj).getVirtualPool();
        } else if (URIUtil.isType(fcSourceURI, BlockSnapshot.class)) {
            URI parentVolURI = ((BlockSnapshot) fcSourceObj).getParent().getURI();
            Volume parentVolume = dbClient.queryObject(Volume.class, parentVolURI);
            vpoolURI = parentVolume.getVirtualPool();
        }

        if (vpoolURI == null) {
            throw APIException.badRequests.invalidFullCopySource(fcSourceURI.toString());
        }

        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        return vpool;
    }

    /**
     * Returns the vpool for the full copy source.
     * 
     * @param fcSourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the vpool for the full copy source.
     */
    public static StoragePool queryFullCopySourceStoragePool(BlockObject fcSourceObj, DbClient dbClient) {
        URI fcSourceURI = fcSourceObj.getId();
        URI poolURI = null;
        if (URIUtil.isType(fcSourceURI, Volume.class)) {
            poolURI = ((Volume) fcSourceObj).getPool();
        } else if (URIUtil.isType(fcSourceURI, BlockSnapshot.class)) {
            URI parentVolURI = ((BlockSnapshot) fcSourceObj).getParent().getURI();
            Volume parentVolume = dbClient.queryObject(Volume.class, parentVolURI);
            poolURI = parentVolume.getPool();
        } else {
            throw APIException.badRequests.invalidFullCopySource(fcSourceURI.toString());
        }

        StoragePool storagePool = null;
        if (!NullColumnValueGetter.isNullURI(poolURI)) {
            storagePool = dbClient.queryObject(StoragePool.class, poolURI);
        }

        return storagePool;
    }

    /**
     * Returns the capacity of the source object. The snap implementation used
     * provisioned capacity, while the volume implementation used the user specified
     * capacity.
     * 
     * TBD: Evaluate the need for this. This was just the current implementation.
     * 
     * @param fcSourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return The capacity.
     */
    public static long getCapacityForFullCopySource(BlockObject fcSourceObj, DbClient dbClient) {
        URI fcSourceURI = fcSourceObj.getId();
        if (URIUtil.isType(fcSourceURI, Volume.class)) {
            return ((Volume) fcSourceObj).getCapacity();
        } else if (URIUtil.isType(fcSourceURI, BlockSnapshot.class)) {
            return ((BlockSnapshot) fcSourceObj).getProvisionedCapacity();
        } else {
            throw APIException.badRequests.invalidFullCopySource(fcSourceURI.toString());
        }
    }

    /**
     * Returns the capacity of the source object. The snap implementation used
     * provisioned capacity, while the volume implementation used the user specified
     * capacity.
     * 
     * TBD: Evaluate the need for this. This was just the current implementation.
     * 
     * @param fcSourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return The capacity.
     */
    public static long getAllocatedCapacityForFullCopySource(BlockObject fcSourceObj, DbClient dbClient) {
        URI fcSourceURI = fcSourceObj.getId();
        if (URIUtil.isType(fcSourceURI, Volume.class)) {
            return ((Volume) fcSourceObj).getAllocatedCapacity();
        } else if (URIUtil.isType(fcSourceURI, BlockSnapshot.class)) {
            return ((BlockSnapshot) fcSourceObj).getAllocatedCapacity();
        } else {
            throw APIException.badRequests.invalidFullCopySource(fcSourceURI.toString());
        }
    }

    /**
     * Gets the SRDF copy mode of the passed volume.
     * 
     * @param fcSourceVolume A reference to a volume.
     * @param dbClient A reference to a database client.
     * 
     * @return The SRDF copy mode of the passed volume.
     */
    public static String getSRDFCopyMode(Volume volume, DbClient dbClient) {
        if (Volume.isSRDFProtectedTargetVolume(volume)) {
            if (PersonalityTypes.SOURCE.toString().equalsIgnoreCase(volume.getPersonality())) {
                StringSet targetIds = volume.getSrdfTargets();
                if ((null != targetIds) && !targetIds.isEmpty()) {
                    for (String targetId : targetIds) {
                        Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetId));
                        return targetVolume.getSrdfCopyMode();
                    }
                }
            }

            return volume.getSrdfCopyMode();
        }

        return null;
    }

    /**
     * Verifies the passed source and full copy URIs for a requested full
     * copy operation.
     * 
     * @param sourceURI The URI of the source volume or snapshot.
     * @param fullCopyURI The URI of a full copy of the source.
     * @param uriInfo A reference to the URI information.
     * @param dbClient A reference to a database client.
     * 
     * @return The map containing references to the source and full copy.
     */
    public static Map<URI, BlockObject> verifySourceAndFullCopy(
            URI sourceURI, URI fullCopyURI, UriInfo uriInfo, DbClient dbClient) {

        // Verify passed URIs.
        BlockObject fcSourceObj = queryFullCopyResource(sourceURI, uriInfo, true, dbClient);
        Volume fullCopyVolume = (Volume) queryFullCopyResource(fullCopyURI, uriInfo, false, dbClient);

        // Verify the full copy volume is actually a full copy.
        verifyVolumeIsFullCopy(fullCopyVolume);

        // Verify the copy is for the source.
        verifyCopyIsForSource(fullCopyVolume, sourceURI);

        // Add the volumes to the volume map.
        Map<URI, BlockObject> resourceMap = new HashMap<URI, BlockObject>();
        resourceMap.put(sourceURI, fcSourceObj);
        resourceMap.put(fullCopyURI, fullCopyVolume);
        return resourceMap;
    }

    /**
     * Verifies that the passed volume is really a full copy volume.
     * 
     * @param fullCopyVolume A reference to a volume.
     */
    public static void verifyVolumeIsFullCopy(Volume fullCopyVolume) {
        if (fullCopyVolume.getAssociatedSourceVolume() == null) {
            throw APIException.badRequests.protectionOnlyFullCopyVolumesCanBeActivated();
        }
    }

    /**
     * Verifies that the passed URI references the actual source for the passed
     * full copy.
     * 
     * @param fullCopyVolume A reference to the full copy volume.
     * @param fcSourceURI The URI of the full copy source.
     */
    public static void verifyCopyIsForSource(Volume fullCopyVolume, URI fcSourceURI) {
        if (!fullCopyVolume.getAssociatedSourceVolume().toString()
                .equals(fcSourceURI.toString())) {
            throw APIException.badRequests.protectionVolumeNotFullCopyOfVolume(
                    fullCopyVolume.getId(), fcSourceURI);
        }
    }

    /**
     * Verify that a volume with full copies can be deleted.
     * 
     * @param volume A reference to a volume that has full copies.
     * @param dbClient A reference to a database client.
     * 
     * @return true, if the volume has no full copies or is detached from all
     *         full copies, false otherwise.
     */
    public static boolean volumeDetachedFromFullCopies(Volume volume, DbClient dbClient) {
        boolean detached = true;

        // The volume must be detached from all its full copies.
        StringSet fullCopyIds = volume.getFullCopies();
        if ((fullCopyIds != null) && (!fullCopyIds.isEmpty())) {
            Iterator<String> fullCopyIdsIter = fullCopyIds.iterator();
            while (fullCopyIdsIter.hasNext()) {
                String fullCopyId = fullCopyIdsIter.next();
                Volume fullCopyVolume = dbClient.queryObject(Volume.class,
                        URI.create(fullCopyId));
                if (!isFullCopyDetached(fullCopyVolume, dbClient)) {
                    detached = false;
                }
            }
        }

        return detached;
    }

    /**
     * Check if the full copy volume could be restored.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the full copy is restorable, false otherwise.
     */
    public static boolean isFullCopyRestorable(Volume volume, DbClient dbClient) {
        boolean result = false;
        String replicaState = volume.getReplicaState();
        if (isVolumeFullCopy(volume, dbClient) && replicaState != null && !replicaState.isEmpty()) {
            ReplicationState state = ReplicationState.getEnumValue(replicaState);
            if (state != null && state == ReplicationState.SYNCHRONIZED) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Check if the full copy volume could be resynchronized.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the full copy can be resynchronized, false otherwise.
     */
    public static boolean isFullCopyResynchronizable(Volume volume, DbClient dbClient) {
        boolean result = false;
        String replicaState = volume.getReplicaState();
        if (isVolumeFullCopy(volume, dbClient) && replicaState != null && !replicaState.isEmpty()) {
            ReplicationState state = ReplicationState.getEnumValue(replicaState);
            if (state != null && state == ReplicationState.SYNCHRONIZED) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Check if the full copy is detached.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the full copy is detached from the source, false otherwise.
     */
    public static boolean isFullCopyDetached(Volume volume, DbClient dbClient) {
        boolean result = false;
        String replicaState = volume.getReplicaState();
        // When the full copy is detached, it will not have reference to the source volume.
        if (!isVolumeFullCopy(volume, dbClient) && replicaState != null && !replicaState.isEmpty()) {
            ReplicationState state = ReplicationState.getEnumValue(replicaState);
            if (state != null && state == ReplicationState.DETACHED) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Check if the full copy is inactive.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the full copy is inactive, false otherwise.
     */
    public static boolean isFullCopyInactive(Volume volume, DbClient dbClient) {
        boolean result = true;
        String replicaState = volume.getReplicaState();
        if (isVolumeFullCopy(volume, dbClient) && replicaState != null && !replicaState.isEmpty()) {
            ReplicationState state = ReplicationState.getEnumValue(replicaState);
            if (state != null && state != ReplicationState.INACTIVE) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Determines if the passed volume is a full copy.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the volume is a full copy, false otherwise.
     */
    public static boolean isVolumeFullCopy(Volume volume, DbClient dbClient) {
        boolean isFullCopy = false;
        URI fcSourceObjURI = volume.getAssociatedSourceVolume();
        if (!NullColumnValueGetter.isNullURI(fcSourceObjURI)) {
            BlockObject fcSourceObj = BlockObject.fetch(dbClient, fcSourceObjURI);
            if ((fcSourceObj != null) && (!fcSourceObj.getInactive())) {
                // The volume has a valid source object, so it
                // is a full copy volume. We check the source,
                // because the full copy mat have been detached
                // from the source and the source may have been
                // deleted.
                isFullCopy = true;
            }
        }

        return isFullCopy;
    }

    /**
     * Determine if the passed volume is a source volume
     * for any full copies.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to a database client.
     * 
     * @return true if the volume is a full copy source, false otherwise.
     */
    public static boolean isVolumeFullCopySource(Volume volume, DbClient dbClient) {
        boolean isFullCopySource = false;
        StringSet fullCopyIds = volume.getFullCopies();
        if ((fullCopyIds != null) && (!fullCopyIds.isEmpty())) {
            Iterator<String> fullCopyIdsIter = fullCopyIds.iterator();
            while (fullCopyIdsIter.hasNext()) {
                URI fullCopyURI = URI.create(fullCopyIdsIter.next());
                Volume fullCopyVolume = dbClient.queryObject(Volume.class, fullCopyURI);
                if ((fullCopyVolume != null) && (!fullCopyVolume.getInactive())) {
                    isFullCopySource = true;
                }
            }
        }

        return isFullCopySource;
    }
    
    /**
     * Determine if the passed volume is a source volume
     * for any consistency group full copies.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to a database client.
     * 
     * @return true if the volume is a CG full copy source, false otherwise.
     */
    public static boolean isVolumeCGFullCopySource(Volume volume, DbClient dbClient) {
        boolean isFullCopySource = false;
        StringSet fullCopyIds = volume.getFullCopies();
        if ((fullCopyIds != null) && (!fullCopyIds.isEmpty())) {
            Iterator<String> fullCopyIdsIter = fullCopyIds.iterator();
            while (fullCopyIdsIter.hasNext()) {
                URI fullCopyURI = URI.create(fullCopyIdsIter.next());
                Volume fullCopyVolume = dbClient.queryObject(Volume.class, fullCopyURI);
                if ((fullCopyVolume != null) && (!fullCopyVolume.getInactive())) {
                    String groupName = fullCopyVolume.getReplicationGroupInstance();
                    if (NullColumnValueGetter.isNotNullValue(groupName) ||
                            VPlexUtil.isBackendFullCopyInReplicationGroup(fullCopyVolume, dbClient)) {
                        isFullCopySource = true;
                        break;
                    }
                }
            }
        }

        return isFullCopySource;
    }


    /**
     * Determines if the passed volume has an active full copy session.
     * 
     * @param volume A reference to the volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the volume has a full copy session, false otherwise.
     */
    public static boolean volumeHasFullCopySession(Volume volume, DbClient dbClient) {
        boolean hasFcSession = false;
        if (((isVolumeFullCopy(volume, dbClient)) && (!isFullCopyDetached(volume, dbClient))) ||
                ((isVolumeFullCopySource(volume, dbClient)) && (!volumeDetachedFromFullCopies(volume, dbClient)))) {
            // The volume is a full copy and it is not detached
            // from its source or it is a full copy source volume
            // and it is not detached from one or more of its full
            // copies.
            hasFcSession = true;
        }

        return hasFcSession;
    }

    /**
     * Determines if the active full count is violated when a request
     * is made for the passed number of full copies for the source
     * volume with the passed URI. Throws and exception if this is
     * the case.
     * 
     * @param sourceVolumeURI The URI of the full copy source volume.
     * @param numRequested The number of requested full copies.
     * @param maxCount The maximum number of active full copy sessions.
     * @param dbClient A reference to a database client.
     */
    public static void validateActiveFullCopyCount(BlockObject fcSourceObj,
            int numRequested, DbClient dbClient) {
        validateActiveFullCopyCount(fcSourceObj, numRequested, 0, dbClient);
    }

    /**
     * Determines if the active full count is violated when a request
     * is made for the passed number of full copies for the source
     * volume with the passed URI. Throws and exception if this is
     * the case.
     * 
     * @param sourceVolumeURI The URI of the full copy source volume.
     * @param numRequested The number of requested full copies.
     * @param otherCount Other additional count to be considered.
     * @param maxCount The maximum number of active full copy sessions.
     * @param dbClient A reference to a database client.
     */
    public static void validateActiveFullCopyCount(BlockObject fcSourceObj,
            int numRequested, int otherCount, DbClient dbClient) {
        List<Volume> undetachedFullCopies = getUndetachedFullCopiesForSource(fcSourceObj,
                dbClient);
        int currentCount = undetachedFullCopies.size() + otherCount;
        URI systemURI = fcSourceObj.getStorageController();
        StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
        int maxCount = Integer.MAX_VALUE;
        if (system != null) {
            maxCount = BlockFullCopyManager.getMaxFullCopiesForSystemType(system.getSystemType());
        }

        if ((numRequested + currentCount) > maxCount) {
            throw APIException.badRequests.maxFullCopySessionLimitExceeded(
                    fcSourceObj.getId(), maxCount - currentCount);
        }
    }

    /**
     * Gets a list of the full copies for passed full copy source
     * that are not detached from the source.
     * 
     * @param fcSourceObj The full copy source.
     * @param dbClient A reference to a database client.
     * 
     * @return A list of the undetached full copies for the source.
     */
    public static List<Volume> getUndetachedFullCopiesForSource(BlockObject fcSourceObj,
            DbClient dbClient) {
        ArrayList<Volume> undetachedFullCopies = new ArrayList<Volume>();
        URI fcSourceURI = fcSourceObj.getId();
        List<Volume> fullCopies = CustomQueryUtility.queryActiveResourcesByConstraint(
                dbClient, Volume.class, ContainmentConstraint.Factory
                        .getAssociatedSourceVolumeConstraint(fcSourceURI));
        Iterator<Volume> fullCopiesIter = fullCopies.iterator();
        while (fullCopiesIter.hasNext()) {
            Volume fullCopy = fullCopiesIter.next();
            String fullCopyReplicaState = fullCopy.getReplicaState();
            if ((!fullCopy.getInactive())
                    && (!Volume.ReplicationState.DETACHED.name().equals(
                            fullCopyReplicaState))) {
                undetachedFullCopies.add(fullCopy);
            }
        }

        return undetachedFullCopies;
    }

    /**
     * Gets all clones for the given set name and volume group.
     * 
     * @param cloneSetName
     * @param volumeGroupId
     * @param dbClient
     * @return
     */
    public static List<Volume> getClonesBySetName(String cloneSetName, URI volumeGroupId, DbClient dbClient) {
        List<Volume> setClones = new ArrayList<Volume>();
        if (cloneSetName != null) {
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFullCopiesBySetName(cloneSetName), list);
            Iterator<Volume> iter = dbClient.queryIterativeObjects(Volume.class, list);
            while (iter.hasNext()) {
                Volume vol = iter.next();
                URI sourceId = getSourceIdForFullCopy(vol);
                if (sourceId != null) {
                    Volume sourceVol = dbClient.queryObject(Volume.class, sourceId);
                    if (sourceVol != null && !sourceVol.getInactive() && sourceVol.getVolumeGroupIds() != null
                            && sourceVol.getVolumeGroupIds().contains(volumeGroupId)) {
                        setClones.add(vol);
                    }
                }
            }
        }
        return setClones;
    }

    /**
     * gets the source URI for a replica
     * 
     * @param replica
     * @return
     */
    public static URI getSourceIdForFullCopy(BlockObject replica) {
        URI sourceURI = null;
        if (replica instanceof BlockSnapshot) {
            sourceURI = ((BlockSnapshot) replica).getParent().getURI();
        } else if (replica instanceof BlockMirror) {
            sourceURI = ((BlockMirror) replica).getSource().getURI();
        } else if (replica instanceof Volume) {
            sourceURI = ((Volume) replica).getAssociatedSourceVolume();
        }
        return sourceURI;
    }

    /**
     * gets the replica type for a replica
     * 
     * @param replica
     * @return
     */
    public static String getReplicaType(BlockObject replica) {
        String replicaType = null;
        if (replica instanceof BlockSnapshot) {
            replicaType = REPLICA_TYPE_SNAPSHOT;
        } else if (replica instanceof BlockMirror) {
            replicaType = REPLICA_TYPE_CONTINUOUS_COPY;
        } else if (replica instanceof Volume) {
            replicaType = REPLICA_TYPE_FULL_COPY;
        }
        return replicaType;
    }
}
