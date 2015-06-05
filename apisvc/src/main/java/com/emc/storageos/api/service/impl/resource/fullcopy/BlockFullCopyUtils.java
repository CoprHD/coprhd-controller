/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;


import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Utilities class for processing fully copy request
 */
public class BlockFullCopyUtils {
    
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
        }
        
        if (poolURI == null) {
            throw APIException.badRequests.invalidFullCopySource(fcSourceURI.toString());
        }
        
        StoragePool storagePool = dbClient.queryObject(StoragePool.class, poolURI);
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
     * Verify full copy is supported for the passed object.
     * 
     * @param fcSourceObj A reference to a potential full copy source.
     * @param dbClient A reference to a database client.
     * 
     * return false if SRDF copy mode is asynchronous, true otherwise
     */
    public static boolean isSRDFCopyModeAsync(BlockObject fcSourceObj, DbClient dbClient) {
        boolean isAsync = false;
        URI fcSourceURI = fcSourceObj.getId();
        if (URIUtil.isType(fcSourceURI, Volume.class)) {
            Volume fcSourceVolume = (Volume) fcSourceObj;
            String copyMode = getSRDFCopyMode(fcSourceVolume, dbClient);
            if (SupportedCopyModes.ASYNCHRONOUS.toString().equalsIgnoreCase(copyMode)) {
                isAsync = true;
            }
        }
        return isAsync;
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
                if ((null != targetIds) && (targetIds.size() > 0)) {
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
        boolean result = true;
        String replicaState = volume.getReplicaState();
        if (isVolumeFullCopy(volume, dbClient) && replicaState != null && !replicaState.isEmpty()) {
            ReplicationState state = ReplicationState.getEnumValue(replicaState);
            if (state != null && state != ReplicationState.DETACHED) {
                result = false;
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
}
