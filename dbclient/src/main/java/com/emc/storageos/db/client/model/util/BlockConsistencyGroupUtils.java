/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.util;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class BlockConsistencyGroupUtils {
    /**
     * Splitter character that divides the cluster name and consistency
     * group name.
     */
    private static final String SPLITTER = ":";
    public static final String CLUSTER_1 = "cluster-1";
    public static final String CLUSTER_2 = "cluster-2";

    /**
     * Parses out the cluster name from the combined cluster/cg name.
     * 
     * @param clusterCgName The combined cluster/cg name from which to extract
     *            the cluster name.
     * @return The cluster name.
     */
    public static String fetchClusterName(String clusterCgName) {
        String clusterName = null;
        if (clusterCgName != null && !clusterCgName.isEmpty()) {
            String[] tmp = clusterCgName.split(SPLITTER);
            clusterName = tmp[0];
        }
        return clusterName;
    }

    /**
     * Parses out the consistency group name from the combined cluster/cg name.
     * 
     * @param clusterCgName The combined cluster/cg name from which to extract
     *            the cg name.
     * @return The cg name.
     */
    public static String fetchCgName(String clusterCgName) {
        String cgName = null;
        if (clusterCgName != null && !clusterCgName.isEmpty()) {
            String[] tmp = clusterCgName.split(SPLITTER);
            cgName = tmp[1];
        }
        return cgName;
    }

    /**
     * Builds a concatenated name combining the cluster name and consistency
     * group name. This is used for mapping VPlex storage systems to their
     * corresponding cluster consistency groups.
     * 
     * @param clusterName The cluster name.
     * @param cgName The consistency group name.
     * @return The cluster name concatenated with the consistency group.
     */
    public static String buildClusterCgName(String clusterName, String cgName) {
        return String.format("%s" + SPLITTER + "%s", clusterName, cgName);
    }

    /**
     * Checks to see if the BlockConsistencyGroup references any VPlex
     * consistency groups.
     * 
     * @return true if this BlockConsistencyGroup references any VPlex
     *         consistency groups, false otherwise.
     */
    public static boolean referencesVPlexCGs(BlockConsistencyGroup cg, DbClient dbClient) {
        if (cg.getTypes() != null && cg.getTypes().contains(Types.VPLEX.name())) {
            for (StorageSystem storageSystem : getVPlexStorageSystems(cg, dbClient)) {
                if (storageSystem.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
                    StringSet cgNames = cg.getSystemConsistencyGroups().get(storageSystem.getId().toString());
                    if (cgNames != null && !cgNames.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static List<StorageSystem> getVPlexStorageSystems(BlockConsistencyGroup cg, DbClient dbClient) {
        List<StorageSystem> vplexStorageSystems = new ArrayList<StorageSystem>();

        if (cg.getSystemConsistencyGroups() != null &&
                !cg.getSystemConsistencyGroups().isEmpty()) {
            for (String systemUri : cg.getSystemConsistencyGroups().keySet()) {
                // Only look at StorageSystem types for VPlex
                if (URIUtil.isType(URI.create(systemUri), StorageSystem.class)) {
                    StorageSystem storageSystem =
                            dbClient.queryObject(StorageSystem.class, URI.create(systemUri));
                    if (storageSystem.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
                        vplexStorageSystems.add(storageSystem);
                    }
                }
            }
        }

        return vplexStorageSystems;
    }

    /**
     * Gets the protection system(s) containing the CGs corresponding to the
     * passed RP CG.
     * 
     * @param cg the BlockConsistencyGroup.
     * @param dbClient the DB client references.
     * @return A list of the the protection system(s) containing the CGs
     *         corresponding to the passed RP CG.
     */
    public static List<ProtectionSystem> getRPProtectionSystems(BlockConsistencyGroup cg, DbClient dbClient) {
        List<ProtectionSystem> rpSystems = new ArrayList<ProtectionSystem>();

        if (cg.getSystemConsistencyGroups() != null &&
                !cg.getSystemConsistencyGroups().isEmpty()) {
            for (String systemUri : cg.getSystemConsistencyGroups().keySet()) {
                if (URIUtil.isType(URI.create(systemUri), ProtectionSystem.class)) {
                    ProtectionSystem rpSystem = dbClient.queryObject(ProtectionSystem.class, URI.create(systemUri));
                    rpSystems.add(rpSystem);
                }
            }
        }

        return rpSystems;
    }

    /**
     * Determines if the given BlockConsistencyGroup references any non-LOCAL storage system
     * consistency groups.
     * 
     * @param cg the BlockConsistencyGroup.
     * @param dbClient the DB client references.
     * @return true if the CG references any non-local CGs, false otherwise.
     */
    public static boolean referencesNonLocalCgs(BlockConsistencyGroup cg, DbClient dbClient) {
        List<StorageSystem> storageSystems = getVPlexStorageSystems(cg, dbClient);
        List<ProtectionSystem> protectionSystems = getRPProtectionSystems(cg, dbClient);
        boolean referencesVplexCgs = false;
        boolean referencesRpCgs = false;

        if (storageSystems != null && !storageSystems.isEmpty()) {
            for (StorageSystem storageSystem : storageSystems) {
                StringSet cgs = cg.getSystemConsistencyGroups().get(storageSystem.getId().toString());
                if (cgs != null && !cgs.isEmpty()) {
                    referencesVplexCgs = true;
                    break;
                }
            }
        }

        if (protectionSystems != null && !protectionSystems.isEmpty()) {
            for (ProtectionSystem protectionSystem : protectionSystems) {
                StringSet cgs = cg.getSystemConsistencyGroups().get(protectionSystem.getId().toString());
                if (cgs != null && !cgs.isEmpty()) {
                    referencesRpCgs = true;
                    break;
                }
            }
        }

        return referencesVplexCgs || referencesRpCgs;
    }

    /**
     * Checks to see if the VPlex CG for the VPlex system and cluster has been created.
     * 
     * @param vplexSystem The VPlex storage system.
     * @param clusterName The VPlex cluster name.
     * @param cgName The consistency group name.
     * @param isDistributed True if the check is for a distributed CG.
     * @return true if the VPlex consistency group has been created, false otherwise.
     */
    public static boolean isVplexCgCreated(BlockConsistencyGroup cg, String vplexSystem, String clusterName, String cgName, boolean isDistributed) {
        boolean vplexCgCreated = false;

        if (cg.getSystemConsistencyGroups() == null) {
            return false;
        }

        StringSet clusterCgNames = cg.getSystemConsistencyGroups().get(vplexSystem);
        if (clusterCgNames != null && !clusterCgNames.isEmpty()) {
            if (isDistributed) {
                String cluster1CgName = buildClusterCgName(CLUSTER_1, cgName);
                String cluster2CgName = buildClusterCgName(CLUSTER_2, cgName);
                vplexCgCreated = clusterCgNames.contains(cluster1CgName)
                                    || clusterCgNames.contains(cluster2CgName);
            } else {
                String clusterCgName = buildClusterCgName(clusterName, cgName);
                vplexCgCreated = clusterCgNames.contains(clusterCgName);
            }
        }

        return vplexCgCreated;
    }

    /**
     * Gets the storage system(s) containing the native CGs corresponding to the
     * passed VPLEX CG.
     * 
     * @param cg A reference to a VPLEX CG
     * @param dbClient A reference to a database client
     * 
     * @return A list of the the storage system(s) containing the native CGs
     *         corresponding to the passed VPLEX CG.
     */
    public static List<URI> getLocalSystems(BlockConsistencyGroup cg, DbClient dbClient) {
        List<URI> localSystemUris = new ArrayList<URI>();
        StringSetMap systemCgMap = cg.getSystemConsistencyGroups();
        if (systemCgMap != null) {
            Iterator<String> cgSystemIdsIter = cg.getSystemConsistencyGroups().keySet().iterator();
            while (cgSystemIdsIter.hasNext()) {
                URI cgSystemUri = URI.create(cgSystemIdsIter.next());
                if (URIUtil.isType(cgSystemUri, StorageSystem.class)) {
                    StorageSystem cgSystem = dbClient.queryObject(StorageSystem.class, cgSystemUri);
                    // TODO: If we support other systems for consistency groups, we may need
                    // to add another type check here.
                    if (!DiscoveredDataObject.Type.vplex.name().equals(cgSystem.getSystemType())) {
                        localSystemUris.add(cgSystemUri);
                    }
                }
            }
        }

        return localSystemUris;
    }

    /**
     * Gets the storage system(s) containing the native CGs corresponding to the
     * passed VPLEX CG.
     * 
     * @param cg A reference to a VPLEX CG
     * @param dbClient A reference to a database client
     * 
     * @return A list of the the storage system(s) containing the native CGs
     *         corresponding to the passed VPLEX CG.
     */
    public static List<URI> getLocalSystemsInCG(BlockConsistencyGroup cg, DbClient dbClient) {
        List<URI> localSystemUris = new ArrayList<URI>();
        StringSetMap systemCgMap = cg.getSystemConsistencyGroups();
        if (systemCgMap != null) {
            Iterator<String> cgSystemIdsIter = cg.getSystemConsistencyGroups().keySet().iterator();
            while (cgSystemIdsIter.hasNext()) {
                URI cgSystemUri = URI.create(cgSystemIdsIter.next());
                if (URIUtil.isType(cgSystemUri, StorageSystem.class)) {
                    StorageSystem cgSystem = dbClient.queryObject(StorageSystem.class, cgSystemUri);
                    // TODO: If we add support for new block systems, add the same in the
                    // isBlockStorageSystem
                    if (Type.isBlockStorageSystem(cgSystem.getSystemType())) {
                        localSystemUris.add(cgSystemUri);
                    }
                }
            }
        }

        return localSystemUris;
    }
    
    /**
     * Gets all the active volumes in the passed CG.
     * 
     * @param cg A reference to a VPLEX CG
     * @param dbClient A reference to a database client
     * @param personalityType The RecoverPoint personality type. Used to isolate VPlex volumes
     *            in the consistency group to those of that personality type. Optional field
     *            left null if not desired.
     * 
     * @return A list of the active VPLEX volumes in the passed VPLEX CG.
     */
    static public List<Volume> getActiveVolumesInCG(BlockConsistencyGroup cg, DbClient dbClient,
            PersonalityTypes personalityType) {
        List<Volume> volumeList = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (!volume.getInactive()) {
                // If the personalityType is specified, we want to ensure we only consider
                // volumes with that personality type.
                if (personalityType == null ||
                        (volume.getPersonality() != null && volume.getPersonality().equals(personalityType.name()))) {
                    volumeList.add(volume);
                }
            }
        }
        return volumeList;
    }

    /**
     * Gets the active VPLEX volumes in the passed VPLEX CG.
     * 
     * @param cg A reference to a VPLEX CG
     * @param dbClient A reference to a database client
     * @param personalityType The RecoverPoint personality type. Used to isolate VPlex volumes
     *            in the consistency group to those of that personality type. Optional field
     *            left null if not desired.
     * 
     * @return A list of the active VPLEX volumes in the passed VPLEX CG.
     */
    static public List<Volume> getActiveVplexVolumesInCG(BlockConsistencyGroup cg, DbClient dbClient,
            PersonalityTypes personalityType) {
        List<Volume> volumeList = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        if (uriQueryResultList != null) {
            Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                    uriQueryResultList);
            if (volumeIterator != null) {
                while (volumeIterator.hasNext()) {
                    Volume volume = volumeIterator.next();
                    if (!volume.getInactive()) {
                        // When determining the volumes to snap, we want the VPLEX volumes
                        // referencing the consistency group. The backend volumes for the
                        // VPLEX volume will also now reference the consistency group.
                        // At this point we just want the VPLEX volumes.
                        if (volume.isVPlexVolume(dbClient)) {
                            // If the personalityType is specified, we want to ensure we only consider
                            // volumes with that personality type.
                            if (personalityType == null ||
                                    (volume.getPersonality() != null && volume.getPersonality().equals(personalityType.name()))) {
                                volumeList.add(volume);
                            }
                        }
                    }
                }
            }
        }
        return volumeList;
    }

    /**
     * Gets the active non-VPlex volumes in the consistency group.
     * 
     * @param cg A reference to a CG
     * @param dbClient A reference to a database client
     * @param personalityType The RecoverPoint personality type. Used to isolate volumes
     *            in the consistency group to those of that personality type. Optional field
     *            left null if not desired.
     * 
     * @return A list of the active volumes in the passed VPLEX CG.
     */
    static public List<Volume> getActiveNonVplexVolumesInCG(BlockConsistencyGroup cg, DbClient dbClient,
            PersonalityTypes personalityType) {
        List<Volume> volumeList = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (!volume.getInactive()) {
                // We want the non-VPlex volumes, which are those volumes that do not have associated volumes.
                if (volume.getAssociatedVolumes() == null || volume.getAssociatedVolumes().isEmpty()) {
                    // If the personalityType is specified, we want to ensure we only consider
                    // volumes with that personality type.
                    if (personalityType == null ||
                            (volume.getPersonality() != null && volume.getPersonality().equals(personalityType.name()))) {
                        volumeList.add(volume);
                    }
                }
            }
        }
        return volumeList;
    }

    /**
     * Gets the active native, non-VPLEX, non-RP volumes in the consistency group.
     *
     * @param cg        Consistency group.
     * @param dbClient  Database client.
     * @return          A list of native back-end volumes in the given consistency group.
     */
    public static List<Volume> getActiveNativeVolumesInCG(BlockConsistencyGroup cg, DbClient dbClient) {
        List<Volume> volumeList = new ArrayList<>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (!volume.getInactive()) {
                // We want the non-VPlex volumes, which are those volumes that do not have associated volumes.
                if (volume.getAssociatedVolumes() == null || volume.getAssociatedVolumes().isEmpty()) {
                    String personality = volume.getPersonality();
                    if (Volume.isSRDFProtectedVolume(volume) || personality == null
                            || PersonalityTypes.SOURCE.name().equalsIgnoreCase(personality)) {
                        volumeList.add(volume);
                    }
                }
            }
        }
        return volumeList;
    }

    /**
     * Verify that the project for the volume is the same as that for the
     * consistency group. Throws an APIException when the projects are not the
     * same.
     * 
     * @param volume A reference to a volume
     * @param cg A reference to a consistency group
     * @param dbClient A reference to a database client
     */
    public static void verifyProjectForVolumeToBeAddedToCG(Volume volume,
            BlockConsistencyGroup cg, DbClient dbClient) {
        // Object to add to consistency group must be in the same project
        URI cgProjectURI = cg.getProject().getURI();
        URI volumeProjectURI = volume.getProject().getURI();
        if (!volumeProjectURI.equals(cgProjectURI)) {
            List<Project> projects = dbClient.queryObjectField(Project.class, "label",
                    Arrays.asList(cgProjectURI, volumeProjectURI));
            throw APIException.badRequests
                    .consistencyGroupAddVolumeThatIsInDifferentProject(volume.getLabel(),
                            projects.get(0).getLabel(), projects.get(1).getLabel());
        }
    }

    public static List<Volume> getAllCGVolumes(BlockConsistencyGroup cg, DbClient dbClient) {
        List<Volume> result = new ArrayList<>();
        
        if (cg.checkForType(BlockConsistencyGroup.Types.VPLEX) && cg.checkForType(BlockConsistencyGroup.Types.RP)) {
            // VPLEX+RP - Right now application supports taking snap sessions on RP targets too.
            result.addAll(getActiveVplexVolumesInCG(cg, dbClient, null));
        } else if (cg.checkForType(BlockConsistencyGroup.Types.VPLEX) && !cg.checkForType(BlockConsistencyGroup.Types.RP)) {
            // VPLEX
            result.addAll(getActiveVplexVolumesInCG(cg, dbClient, null));
        } else if (cg.checkForType(BlockConsistencyGroup.Types.RP) && !cg.checkForType(BlockConsistencyGroup.Types.VPLEX)) {
            // RP Right now application supports taking snap sessions on RP targets too.
            result.addAll(getActiveNonVplexVolumesInCG(cg, dbClient, null));
        } else {
            // Native (no protection)
            result.addAll(getActiveNativeVolumesInCG(cg, dbClient));
        }

        return result;
    }
    
    /**
     * Given a list of volumes find all unique CG URIs.
     * 
     * @param volumes List of volumes to parse over and find all CGs related to the volumes
     * @return Set of unique CG URIs
     */
    public static Set<URI> getAllCGsFromVolumes(List<Volume> volumes) {
        // Using a Set to store all unique CG URIs collected from the
        // volumes passed in.
        Set<URI> cgURIs = new HashSet<URI>();
        
        if (volumes != null) {
            for (Volume vol : volumes) {
                if (!NullColumnValueGetter.isNullURI(vol.getConsistencyGroup())) {
                    cgURIs.add(vol.getConsistencyGroup());
                }
            }
        }
        
        return cgURIs; 
    }

    /**
     * Method to clean up a consistency group after an operation succeeds or fails.
     * 
     * @param consistencyGroup
     *            consistency group
     * @param storageId
     *            storage system
     * @param replicationGroupName
     *            replication group name
     * @param markInactive
     *            whether you can mark it as inactive as part of cleanup (delete the CG itself)
     * @param dbClient
     *            dbclient
     */
    public static void cleanUpCG(BlockConsistencyGroup consistencyGroup, URI storageId, String replicationGroupName,
            Boolean markInactive, DbClient dbClient) {
        checkNotNull(consistencyGroup, "consistency group must be non-null");
        // Remove the replication group name from the SystemConsistencyGroup field
        if (replicationGroupName != null) {
            consistencyGroup.removeSystemConsistencyGroup(URIUtil.asString(storageId), replicationGroupName);
        }
        /*
         * Verify if the BlockConsistencyGroup references any LOCAL arrays.
         * If we no longer have any references we can remove the 'LOCAL' type from the BlockConsistencyGroup.
         */
        List<URI> referencedArrays = getLocalSystems(consistencyGroup, dbClient);
        boolean cgReferenced = false;
        for (URI storageSystemUri : referencedArrays) {
            StringSet cgs = consistencyGroup.getSystemConsistencyGroups().get(storageSystemUri.toString());
            if (cgs != null && !cgs.isEmpty()) {
                cgReferenced = true;
                break;
            }
        }

        if (!cgReferenced) {
            // Remove the LOCAL type
            StringSet cgTypes = consistencyGroup.getTypes();
            cgTypes.remove(BlockConsistencyGroup.Types.LOCAL.name());
            consistencyGroup.setTypes(cgTypes);

            StringSet requestedTypes = consistencyGroup.getRequestedTypes();
            requestedTypes.remove(BlockConsistencyGroup.Types.LOCAL.name());
            consistencyGroup.setRequestedTypes(requestedTypes);

            // Remove the referenced storage system as well, but only if there are no other types
            // of storage systems associated with the CG.
            if (!BlockConsistencyGroupUtils.referencesNonLocalCgs(consistencyGroup, dbClient)) {
                consistencyGroup.setStorageController(NullColumnValueGetter.getNullURI());
                // Clear any remaining types and requestedTypes
                consistencyGroup.getTypes().clear();
                consistencyGroup.getRequestedTypes().clear();
                // Update the consistency group model
                consistencyGroup.setInactive(markInactive);
            }
        }
    }

    /**
     * Method to clean up a consistency group after an operation succeeds or fails.  Also persists any changes to the
     * database.
     *
     * @param consistencyGroup
     *            consistency group
     * @param storageId
     *            storage system
     * @param replicationGroupName
     *            replication group name
     * @param markInactive
     *            whether you can mark it as inactive as part of cleanup (delete the CG itself)
     * @param dbClient
     *            dbclient
     */
    public static void cleanUpCGAndUpdate(BlockConsistencyGroup consistencyGroup, URI storageId, String replicationGroupName,
                                          Boolean markInactive, DbClient dbClient) {
        if (consistencyGroup != null) {
            cleanUpCG(consistencyGroup, storageId, replicationGroupName, markInactive, dbClient);
            dbClient.updateObject(consistencyGroup);
        }
    }

    /**
     * Return a set of ReplicationGroup names for the given storage system and consistency group.
     *
     * @param consistencyGroup  Consistency group
     * @param storageSystem     Storage system
     * @return                  Set of group names or an empty set if none exist.
     */
    public static Set<String> getGroupNamesForSystemCG(BlockConsistencyGroup consistencyGroup, StorageSystem storageSystem) {
        checkNotNull(consistencyGroup);
        checkNotNull(storageSystem);

        Set<String> result = new HashSet<>();
        StringSetMap systemConsistencyGroups = consistencyGroup.getSystemConsistencyGroups();
        if (systemConsistencyGroups != null) {
            StringSet cgsForSystem = systemConsistencyGroups.get(storageSystem.getId().toString());
            if (cgsForSystem != null || !cgsForSystem.isEmpty()) {
                result.addAll(cgsForSystem);
            }
        }
        return result;
    }
}
