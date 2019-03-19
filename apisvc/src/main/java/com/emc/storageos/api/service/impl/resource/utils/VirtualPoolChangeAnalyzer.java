/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Joiner;

/**
 * Compares VirtualPool for differences.
 */
public class VirtualPoolChangeAnalyzer extends DataObjectChangeAnalyzer {

    // Various fields to compare with a VirtualPool
    private static final String PROTECTION_VARRAY_SETTINGS = "protectionVarraySettings";
    private static final String INACTIVE = "inactive";
    private static final String ACLS = "acls";
    private static final String FAST_EXPANSION = "fastExpansion";
    private static final String NON_DISRUPTIVE_EXPANSION = "nonDisruptiveExpansion";
    private static final String HIGH_AVAILABILITY = "highAvailability";
    private static final String MIRROR_VPOOL = "mirrorVirtualPool";
    private static final String REF_VPOOL = "refVirtualPool";
    private static final String VARRAYS = "virtualArrays";
    private static final String TYPE = "type";
    private static final String IS_THIN_VOLUME_PRE_ALLOCATION_ENABLED = "isThinVolumePreAllocationEnabled";
    private static final String THIN_VOLUME_PRE_ALLOCATION_PERCENTAGE = "thinVolumePreAllocationPercentage";
    private static final String AUTO_TIER_POLICY_NAME = "autoTierPolicyName";
    private static final String VMAX_COMPRESSION_ENABLED = "compressionEnabled";
    private static final String UNIQUE_AUTO_TIERING_POLICY_NAMES = "uniquePolicyNames";
    private static final String DRIVE_TYPE = "driveType";
    private static final String ARRAY_INFO = "arrayInfo";
    private static final String USE_MATCHED_POOLS = "useMatchedPools";
    private static final String PROVISIONING_TYPE = "provisioningType";
    private static final String PROTOCOLS = "protocols";
    private static final String CREATION_TIME = "creationTime";
    private static final String TAGS = "tags";
    private static final String STATUS = "status";
    private static final String NUM_PATHS = "numPaths";
    private static final String INVALID_MATCHED_POOLS = "invalidMatchedPools";
    private static final String MATCHED_POOLS = "matchedPools";
    private static final String LABEL = "label";
    private static final String HA_VARRAY_VPOOL_MAP = "haVarrayVpoolMap";
    private static final String DESCRIPTION = "description";
    private static final String ASSIGNED_STORAGE_POOLS = "assignedStoragePools";
    private static final String REMOTECOPY_VARRAY_SETTINGS = "remoteProtectionSettings";
    private static final String PATHS_PER_INITIATOR = "pathsPerInitiator";
    private static final String MIN_PATHS = "minPaths";
    private static final String NONE = "NONE";
    private static final String HOST_IO_LIMIT_BANDWIDTH = "hostIOLimitBandwidth";
    private static final String HOST_IO_LIMIT_IOPS = "hostIOLimitIOPs";
    private static final String AUTO_CROSS_CONNECT_EXPORT = "autoCrossConnectExport";
    private static final String RP_RPO_VALUE = "rpRpoValue";
    private static final String RP_RPO_TYPE = "rpRpoType";
    private static final String RP_COPY_MODE = "rpCopyMode";
    private static final String HA_CONNECTED_TO_RP = "haVarrayConnectedToRp";
    private static final String JOURNAL_SIZE = "journalSize";
    private static final String JOURNAL_VARRAY = "journalVarray";
    private static final String JOURNAL_VPOOL = "journalVpool";
    private static final String MULTI_VOLUME_CONSISTENCY = "multivolumeconsistency";
    private static final String METROPOINT = "metroPoint";
    private static final String FILE_REPLICATION_TYPE = "fileReplicationType";
    private static final String FILE_REPLICATION_COPIES = "fileRemoteCopySettings";
    private static final String FILE_REPLICATION_RPO_VALUE = "frRpoValue";
    private static final String FILE_REPLICATION_RPO_TYPE = "frRpoType";
    private static final String FILE_REPLICATION_COPY_MODE = "replicationCopyMode";
    private static final String FILE_REPLICATION_AT_PROJECT_LEVEL = "allowFilePolicyAtProjectLevel";
    private static final String FILE_REPLICATION_AT_FS_LEVEL = "allowFilePolicyAtFSLevel";
    private static final String FILE_REPLICATION_SUPPORTED = "fileReplicationSupported";
    private static final String FILE_SNAPSHOT_SCHEDULE_SUPPORTED = "scheduleSnapshot";

    private static final String STANDBY_JOURNAL_VARRAY = "standbyJournalVarray";
    private static final String STANDBY_JOURNAL_VPOOL = "standbyJournalVpool";

    private static final String[] INCLUDED_AUTO_TIERING_POLICY_LIMITS_COMPRESSION_CHANGE = new String[] { AUTO_TIER_POLICY_NAME,
            HOST_IO_LIMIT_BANDWIDTH, HOST_IO_LIMIT_IOPS, VMAX_COMPRESSION_ENABLED };

    private static final String[] EXCLUDED_AUTO_TIERING_POLICY_LIMITS_CHANGE = new String[] {
            AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH, HOST_IO_LIMIT_IOPS,
            VMAX_COMPRESSION_ENABLED, ARRAY_INFO,
            UNIQUE_AUTO_TIERING_POLICY_NAMES, ASSIGNED_STORAGE_POOLS,
            USE_MATCHED_POOLS, THIN_VOLUME_PRE_ALLOCATION_PERCENTAGE };

    private static final String[] GENERALLY_EXCLUDED = { ACLS, DESCRIPTION,
            LABEL, STATUS, TAGS, CREATION_TIME, INVALID_MATCHED_POOLS, MATCHED_POOLS, NON_DISRUPTIVE_EXPANSION };

    private static final Logger s_logger = LoggerFactory
            .getLogger(VirtualPoolChangeAnalyzer.class);
    private static boolean newVpoolDoesNotSpecifyHaVpool = false;

    /**
     * Determines if the VPlex virtual volume vpool change is supported.
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume vpool.
     * @param newVpool The desired new vpool.
     * @param dbClient A reference to a DB client.
     * @return Supported change vpool operations
     */
    public static VirtualPoolChangeOperationEnum getSupportedVPlexVolumeVirtualPoolChangeOperation(Volume volume,
            VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient,
            StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking getSupportedVPlexVolumeVirtualPoolChangeOperation from [%s] to [%s]...",
                currentVpool.getLabel(), newVpool.getLabel()));
        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return null;
        }

        // Throw an exception if any of the following properties are different
        // between the current and new vpool. The check for a highAvailability
        // change is below. Going from local to distributed or distributed to
        // local is not supported through this vpool change.
        String[] include = new String[] { TYPE, VARRAYS,
                REF_VPOOL,
                FAST_EXPANSION, ACLS,
                INACTIVE, NUM_PATHS };
        // If current vpool specifies mirror then add MIRROR_VPOOL to include.
        if (VirtualPool.vPoolSpecifiesMirrors(currentVpool, dbClient)) {
            include = addElementToArray(include, MIRROR_VPOOL);
        }

        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, null);
        if (!changes.isEmpty()) {
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            return null;
        }

        if (VirtualPool.vPoolSpecifiesMirrors(newVpool, dbClient) &&
                isSupportedAddMirrorsVirtualPoolChange(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff)) {
            return VirtualPoolChangeOperationEnum.ADD_MIRRORS;
        }

        return vplexCommonChecks(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff, include);
    }

    /**
     * Common checks for change vpool for all VPLEX volumes
     * 
     * @param volume The volume in question
     * @param currentVpool The current vpool of the volume
     * @param newVpool The new/target vpool to change to
     * @param dbClient DBClient ref
     * @param notSuppReasonBuff String buffer to store reasons why a target vpool is excluded
     * @param include A list of changes to include in the checks
     * @return The supported vpool change operations
     */
    private static VirtualPoolChangeOperationEnum vplexCommonChecks(Volume volume,
            VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient,
            StringBuffer notSuppReasonBuff, String[] include) {
        s_logger.info(String.format("Checking vplexCommonChecks from [%s] to [%s]...", currentVpool.getLabel(), newVpool.getLabel()));
        boolean isRPVPlex = VirtualPool.vPoolSpecifiesHighAvailability(currentVpool) && VirtualPool.vPoolSpecifiesRPVPlex(newVpool);

        // If the volume is in a CG and the target vpool does not specify multi
        // volume consistency, then the vpool change is not permitted.
        if ((!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) &&
                (!newVpool.getMultivolumeConsistency())) {
            notSuppReasonBuff
                    .append("The volume is in a consistency group but the target virtual pool does not specify multi-volume consistency");
            return null;
        }

        // If highAvailability changed, we do support upgrade from
        // vplex_local to vplex_distributed, with no other significant
        // attributes changed. Otherwise the change is prohibited.
        include = new String[] { HIGH_AVAILABILITY };
        if (!analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {
            // An ingested local VPLEX volume can't be converted to distributed.
            // The backend volumes would need to be migrated first.
            if (volume.isIngestedVolumeWithoutBackend(dbClient)) {
                notSuppReasonBuff.append("The high availability of an ingested VPLEX volume cannot be modified.");
                return null;
            }

            boolean isConvertToDistributed = isVPlexConvertToDistributed(currentVpool, newVpool, notSuppReasonBuff);
            if (isConvertToDistributed && !VirtualPoolUtil.checkMatchingRemoteCopyVarraysettings(currentVpool, newVpool, dbClient)) {
                isConvertToDistributed = false;
                notSuppReasonBuff.append("Incompatible Remote Copy Varray Settings");
            }
            if (isConvertToDistributed) {
                URI haVarrayURI = getHaVarrayURI(newVpool);
                URI volumeVarray = volume.getVirtualArray();
                URI cgURI = volume.getConsistencyGroup();
                if (haVarrayURI.equals(volumeVarray)) {
                    // This is an upgrade from VPLEX local to VPLEX distributed.
                    // However, we only allow the upgrade if the HA varray
                    // specified in the new vpool is not the same as the varray
                    // for the volume. If they are the same, making this vpool
                    // change would cause us to try and create a distributed
                    // VPLEX volume with both legs of the distributed volume
                    // in the same cluster of the VPLEX, which would instead
                    // create a local mirror for the local VPLEX volume.
                    notSuppReasonBuff.append("The High Availability Virtual Array in the target " +
                            "Virtual Pool must be different than the Virtual Array for the volume.");
                    return null;
                } else if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    // We don't allow the upgrade when the volume is in a CG.
                    // The volumes in the CG must be of the same type.
                    // Attempting to upgrade one of them to distributed would
                    // result in an error, and chances are there are multiple
                    // volumes in the CG. We could allow the upgrade if there
                    // is only a single volume in the CG, but we would first
                    // have to remove the volume from the CG, update the CG
                    // properties to support distributed volumes, upgrade the
                    // volume, and then add it back to the CG. For now we are
                    // not supporting this as it seems like an edge use case.
                    // The user can always remove the volume from the CG,
                    // upgrade it to distributed, and then add it back to the
                    // CG manually if this is required.
                    notSuppReasonBuff
                            .append("The volume is in a consistency group");
                    return null;
                } else {
                    if (!isRPVPlex) {
                        return VirtualPoolChangeOperationEnum.VPLEX_LOCAL_TO_DISTRIBUTED;
                    }
                }
            } else {
                return null;
            }
        }

        // For a distributed virtual volume, verify an HA vpool change,
        // if any, also does not include any unsupported changes. If
        // there is not a current HA vpool, it's a local virtual
        // volume.
        VirtualPool newHaVpool = null;
        VirtualPool currentHaVpool = getHaVpool(currentVpool, dbClient);
        if (currentHaVpool != null) {
            // Get the new HA varray vpool.
            try {
                newHaVpool = getNewHaVpool(currentVpool, newVpool, dbClient);
            } catch (Exception e) {
                s_logger.error(e.getMessage());
                notSuppReasonBuff.append(String.format("Could not get new HA vpool from [%s]", newVpool.getLabel()));
                return null;
            }
            if (!currentHaVpool.getId().toString().equals(newHaVpool.getId().toString())) {
                s_logger.info("HA varray vpools are different");

                if (isRPVPlex && newVpoolDoesNotSpecifyHaVpool) {
                    // This means that new RPVPlex vpool does not specify
                    // an HA vpool, so we use the new vpool as the HA vpool, so
                    // specifying protection is OK.
                    include = new String[] { TYPE, VARRAYS,
                            REF_VPOOL, MIRROR_VPOOL,
                            FAST_EXPANSION, ACLS, INACTIVE, NUM_PATHS };
                } else {
                    include = new String[] { TYPE, VARRAYS,
                            PROTECTION_VARRAY_SETTINGS, REF_VPOOL, MIRROR_VPOOL,
                            FAST_EXPANSION, ACLS, INACTIVE, NUM_PATHS };
                }

                Map<String, Change> changes = analyzeChanges(currentHaVpool, newHaVpool, include, null, null);
                if (!changes.isEmpty()) {
                    notSuppReasonBuff.append("Changes in the following high availability virtual pool properties are not permitted: ");
                    fillInNotSupportedReasons(changes, notSuppReasonBuff);
                    return null;
                }

            }
        }

        // Finally, we check to see if any of the vpool changes will actually
        // require data on the volume to be migrated. It's OK if the new vpool
        // has a new label or description, but it must be accompanied by
        // say a drive type for example. The purpose is not to support trivial
        // changes, but instead those that would result in an actual change of
        // the VPlex backend volumes.
        boolean migrateHAVolume = false;
        boolean migrateSourceVolume = VirtualPoolChangeAnalyzer
                .vpoolChangeRequiresMigration(currentVpool, newVpool);
        if (currentHaVpool != null) {
            migrateHAVolume = VirtualPoolChangeAnalyzer.vpoolChangeRequiresMigration(
                    currentHaVpool, newHaVpool);
        }

        if (!migrateSourceVolume && !migrateHAVolume) {
            notSuppReasonBuff.append("The virtual pool does not specify a change in any of the "
                    + "following virtual pool properties: " + PROTOCOLS
                    + ", " + PROVISIONING_TYPE
                    + ", " + USE_MATCHED_POOLS
                    + ", " + ARRAY_INFO
                    + ", " + DRIVE_TYPE
                    + ", " + AUTO_TIER_POLICY_NAME
                    + ", " + HOST_IO_LIMIT_IOPS
                    + ", " + HOST_IO_LIMIT_BANDWIDTH
                    + ", " + IS_THIN_VOLUME_PRE_ALLOCATION_ENABLED
                    + ", " + ASSIGNED_STORAGE_POOLS
                    + ", " + VMAX_COMPRESSION_ENABLED);

            if (!isRPVPlex) {
                // For VPLEX, return null because the migrate operation is not valid.
                // For RP+VPLEX, all this means is that there is no migration required
                // when adding protection, continue on.
                return null;
            }
        }

        if (!isRPVPlex) {
            return VirtualPoolChangeOperationEnum.VPLEX_DATA_MIGRATION;
        }

        return VirtualPoolChangeOperationEnum.RP_PROTECTED;
    }

    /**
     * Gets the HA virtual pool for a VPLEX volume, if any, given the volume's
     * virtual pool.
     * 
     * @param vpool A reference to the volume's virtual pool.
     * @param dbClient A reference to a DB client.
     * 
     * @return The HA virtual pool for a VPLEX volume, if any, given the
     *         volume's virtual pool. Will be null if the volume's virtual pool
     *         does not specify VPLEX distributed HA.
     */
    public static VirtualPool getHaVpool(VirtualPool vpool, DbClient dbClient) {
        VirtualPool haVpool = null;
        // The vpool must specify distributed HA.
        if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(vpool.getHighAvailability())) {
            // The HA varray is required, so there should be an entry in the
            // map. If a vpool was also specified the value in the map
            // will not be a null URI.
            StringMap haVpoolMap = vpool.getHaVarrayVpoolMap();
            URI haVpoolURI = URI.create(haVpoolMap.values().iterator().next());
            if (!NullColumnValueGetter.isNullURI(haVpoolURI)) {
                haVpool = dbClient.queryObject(VirtualPool.class, haVpoolURI);
            } else {
                // When not specified, the same vpool is used on the HA side.
                haVpool = vpool;
            }
        }

        return haVpool;
    }

    /**
     * Gets the new HA virtual pool for a VPLEX volume, if any, given the
     * volume's current virtual pool and a new virtual pool to which it is being
     * changed.
     * 
     * @param currentVpool The current virtual pool for a VPLEX volume.
     * @param newVpool A new virtual pool to which the volume will be changed.
     * @param dbClient A reference to a DB client.
     * 
     * @return The new HA virtual pool for a VPLEX volume, if any, that will
     *         result from changing the volume for its current virtual pool to
     *         the passed new virtual pool. Will return null if the current
     *         virtual pool does not specify VPLEX distributed HA.
     */
    public static VirtualPool getNewHaVpool(VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient) {
        newVpoolDoesNotSpecifyHaVpool = false;
        VirtualPool newHaVpool = null;
        // Get the HA varray specified by the current vpool. If it's
        // null the current vpool does not specify VPLEX distributed HA.
        URI haVarrayURI = getHaVarrayURI(currentVpool);

        if (haVarrayURI != null) {
            // RP+VPLEX only
            if (NullColumnValueGetter.isNotNullValue(newVpool.getHaVarrayConnectedToRp())) {
                haVarrayURI = URI.create(newVpool.getHaVarrayConnectedToRp());
            }

            StringMap newHaVpoolMap = newVpool.getHaVarrayVpoolMap();
            // The new vpool must specify an entry for the HA varray.
            if (newHaVpoolMap.containsKey(haVarrayURI.toString())) {
                URI newHaVpoolURI = URI.create(newHaVpoolMap.get(haVarrayURI.toString()));
                if (!NullColumnValueGetter.isNullURI(newHaVpoolURI)) {
                    newHaVpool = dbClient.queryObject(VirtualPool.class, newHaVpoolURI);
                } else {
                    // When no HA vpool is specified in the new vpool,
                    // the new HA vpool is the new vpool itself.
                    newHaVpool = newVpool;
                    newVpoolDoesNotSpecifyHaVpool = true;
                }
            } else {
                throw APIException.badRequests.wrongHighAvailabilityVArrayInVPool(haVarrayURI.toString());
            }
        }

        return newHaVpool;
    }

    /**
     * Gets the HA virtual array specified by the passed virtual pool.
     * 
     * @param vpool A reference to the virtual pool.
     * 
     * @return The HA virtual array specified by the passed virtual pool
     *         or null if the virtual pool does not specify VPLEX distributed HA.
     */
    public static URI getHaVarrayURI(VirtualPool vpool) {
        URI haVarrayURI = null;
        if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(vpool.getHighAvailability())) {
            StringMap newHaVpoolMap = vpool.getHaVarrayVpoolMap();
            // The HA virtual array is required, so there should be an
            // entry in the map.
            haVarrayURI = URI.create(newHaVpoolMap.keySet().iterator().next());
        }

        return haVarrayURI;
    }

    /**
     * Determines if the two Vpool are equivalent by examining the modified
     * fields.
     * 
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * 
     * @return true if equivalent, false otherwise.
     */
    public static boolean vpoolChangeRequiresMigration(VirtualPool currentVpool, VirtualPool newVpool) {
        s_logger.info(String.format("Checking vpoolChangeRequiresMigration from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        // Verify they are different vpools
        if (isSameVirtualPool(currentVpool, newVpool)) {
            return false;
        }

        // Analyze the changes to determine if there are any changes that
        // require a migration. We assume that the following changes would
        // require a migration of the data: protocols,
        // provisioningType, useMatchedPools, arrayInfo, driveType,
        // autoTierPolicyName, host io limits, host io bandwidth,
        // thin volume allocation, assigned storage pools.
        String[] include = new String[] { PROTOCOLS, PROVISIONING_TYPE,
                USE_MATCHED_POOLS, ARRAY_INFO,
                DRIVE_TYPE, AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_IOPS, HOST_IO_LIMIT_BANDWIDTH, VMAX_COMPRESSION_ENABLED,
                IS_THIN_VOLUME_PRE_ALLOCATION_ENABLED,
                ASSIGNED_STORAGE_POOLS };

        return !analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty();
    }

    /**
     * Returns true if the difference between vpool1 and vpool2 is that vpool2 is
     * requesting highAvailability. Note that remoteProtectionSettings are not checked.
     * 
     * @param vpool1 Reference to Vpool to compare.
     * @param vpool2 Reference to Vpool to compare.
     * @param notImportReasonBuff [OUT] Specifies reason why its not an import.
     * 
     * @return true if the Vpool difference indicates vpool2 adds VPlex high
     *         availability, false otherwise.
     */
    public static boolean isVPlexImport(Volume volume, VirtualPool vpool1, VirtualPool vpool2,
            StringBuffer notImportReasonBuff) {
        s_logger.info(String.format("Checking isVPlexImport from [%s] to [%s]...", vpool1.getLabel(), vpool2.getLabel()));

        if (null != volume.getMirrors() && !volume.getMirrors().isEmpty()) {
            notImportReasonBuff.append(String.format("Volume [%s] has continuous copies attached. "
                    + "Change vpool for a volume which has continuous copies is not allowed.", volume.getLabel()));
            return false;
        }

        String[] excluded = new String[] { ACLS, ASSIGNED_STORAGE_POOLS, DESCRIPTION,
                HA_VARRAY_VPOOL_MAP, LABEL, MATCHED_POOLS, INVALID_MATCHED_POOLS, NUM_PATHS,
                STATUS, TAGS, CREATION_TIME, THIN_VOLUME_PRE_ALLOCATION_PERCENTAGE,
                NON_DISRUPTIVE_EXPANSION, AUTO_CROSS_CONNECT_EXPORT, MIRROR_VPOOL, REMOTECOPY_VARRAY_SETTINGS };
        Map<String, Change> changes = analyzeChanges(vpool1, vpool2, null, excluded, null);

        // Note that we assume vpool1 is for a non-vplex volume and
        // does not specify a value for high availability. This is
        // the case when the function is called.
        if ((changes.size() == 1) && changes.get(HIGH_AVAILABILITY) != null) {
            return true;
        }

        int changeCount = changes.size();

        // Set the reason why the vpool change is not a VPlex import.
        if ((changeCount == 0) || (changes.get(HIGH_AVAILABILITY) == null)) {
            notImportReasonBuff.append("The target virtual pool does not specify a value for high "
                    + "availability");
        } else if (changeCount > 1) {

            // render output for error message
            StringBuffer changesOutput = new StringBuffer();
            for (Change change : changes.values()) {
                if (!change._key.equals(HIGH_AVAILABILITY)) {

                    // CTRL-12010 if the source vpool's rpo value is null,
                    // it's fine if the target vpool has a value of 0
                    if (change._key.equals(RP_RPO_VALUE) && (change._left == null)) {
                        if (change._right != null && (Long.valueOf(change._right.toString()) == 0)) {
                            s_logger.info("rpRpoValue diff is okay");
                            changeCount--;
                            continue;
                        }
                    }

                    changesOutput.append(change.toString() + " ");
                }
            }

            if (changeCount == 1) {
                s_logger.info("there were some differences, but changes that don't "
                        + "matter were filtered out, so this vpool is okay for VPLEX");
                return true;
            }

            notImportReasonBuff.append("The target virtual pool contains differences in properties "
                    + "other than high availability.");
            s_logger.info("The target virtual pool contains differences in properties "
                    + "other than high availability.: " + changesOutput.toString().trim());
        }

        return false;
    }

    /**
     * Returns true if the new virtual pool contains the storage pool of the
     * volume requested for virtual pool change.
     * 
     * @param volume the volume requested for virtual pool change
     * @param newVpool the target virtual pool
     * @param notSuppReasonBuff [OUT] contains the reasons a virtual pool
     *            change is not supported
     * 
     * @return true if the target virtual pool contains the storage pool of
     *         the volume requested for virtual pool change
     */
    public static boolean doesVplexVpoolContainVolumeStoragePool(Volume volume,
            VirtualPool newVpool, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking doesVplexVpoolContainVolumeStoragePool for [%s]...", newVpool.getLabel()));
        StringSet poolsToCheck = newVpool.getUseMatchedPools() ? newVpool.getMatchedStoragePools() : newVpool.getAssignedStoragePools();

        if ((null == poolsToCheck) || !poolsToCheck.contains(volume.getPool().toString())) {
            notSuppReasonBuff.append("The target virtual pool ").append(newVpool.getLabel());
            notSuppReasonBuff.append(" does not contain the volume's storage pool (");
            notSuppReasonBuff.append(volume.getPool()).append(") ");
            return false;
        }

        return true;
    }

    /**
     * Returns true iff the only difference is converting from a vplex_local to
     * vplex_distributed. Note that remoteProtectionSettings are not checked.
     * 
     * @param vpool1 A reference to a Vpool
     * @param vpool2 A reference to a Vpool
     * @param notSuppReasonBuff [OUT] Specifies the reason a Vpool change is not
     *            supported between the two Vpool.
     * 
     * @return true if the Vpool difference specifies only a change in the high
     *         availability type from local to distributed, false otherwise.
     */
    public static boolean isVPlexConvertToDistributed(VirtualPool vpool1, VirtualPool vpool2,
            StringBuffer notSuppReasonBuff) {
        s_logger.info("Running isVPlexConvertToDistributed...");
        s_logger.info(String.format("Checking isVPlexConvertToDistributed from [%s] to [%s]...", vpool1.getLabel(), vpool2.getLabel()));
        String[] excluded = new String[] { ASSIGNED_STORAGE_POOLS, DESCRIPTION,
                HA_VARRAY_VPOOL_MAP, LABEL, MATCHED_POOLS, INVALID_MATCHED_POOLS, NUM_PATHS,
                STATUS, TAGS, CREATION_TIME, NON_DISRUPTIVE_EXPANSION, REMOTECOPY_VARRAY_SETTINGS };
        Map<String, Change> changes = analyzeChanges(vpool1, vpool2, null, excluded, null);

        // changes.size() needs to be greater than 1, because at least
        // HIGH_AVAILABILITY change is expected
        if (changes.size() > 1) {
            notSuppReasonBuff.append("Changes in addition to a change in the "
                    + "virtual pool high availability property are not permitted.");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            s_logger.info("Changes in addition to a change in the "
                    + "virtual pool high availability property are not permitted: ");
            for (Entry<String, Change> entry : changes.entrySet()) {
                // ignore HIGH_AVAILABILITY change because it is expected here
                if (entry.getKey().equals(HIGH_AVAILABILITY)) {
                    continue;
                }
                s_logger.info(entry.getKey() + "- " + entry.getValue() + " ");
            }
            return false;
        }

        Change change = changes.get(HIGH_AVAILABILITY);
        if (change != null
                && change._left != null
                && change._left.equals(VirtualPool.HighAvailabilityType.vplex_local.name())
                && change._right != null
                && change._right.equals(VirtualPool.HighAvailabilityType.vplex_distributed.name())) {
            return true;
        } else {
            notSuppReasonBuff.append(String.format(
                    "The virtual pool high availability property can only be changed from %s to %s",
                    VirtualPool.HighAvailabilityType.vplex_local.name(),
                    VirtualPool.HighAvailabilityType.vplex_distributed.name()));
            return false;
        }
    }

    /**
     * Verifies the Vpool change for a tech refresh of a VPlex virtual volume.
     * The Vpool should only specify a simple change such as the type of disk
     * drive.
     * 
     * @param srcVpool The Vpool of the migration source
     * @param tgtVpool The proposed Vpool of the migration target.
     */
    public static void verifyVirtualPoolChangeForTechRefresh(VirtualPool srcVpool, VirtualPool tgtVpool) {
        // Exclude things that we don't mind changing in the Vpool
        // and check if there are any other changes. If so, the
        // Vpool requested is not valid. What I really want to allow
        // for tech refresh are changes such as driveType,
        // protocols, provisioningType, and arrayInfo. However,
        // there will be other benign changes that can occur.
        String[] exclude = new String[] { PROTOCOLS, PROVISIONING_TYPE, ARRAY_INFO,
                DRIVE_TYPE, AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_IOPS, HOST_IO_LIMIT_BANDWIDTH, VMAX_COMPRESSION_ENABLED, MATCHED_POOLS,
                INVALID_MATCHED_POOLS,
                ASSIGNED_STORAGE_POOLS, LABEL, DESCRIPTION, STATUS, TAGS,
                CREATION_TIME, NON_DISRUPTIVE_EXPANSION };

        if (!VirtualPoolChangeAnalyzer.analyzeChanges(srcVpool, tgtVpool, null, exclude, null)
                .isEmpty()) {
            throw APIException.badRequests.vPoolChangeNotValid(srcVpool.getId(), tgtVpool.getId());
        }
    }

    /**
     * Determines if the volume qualifies for RP protection. (and if not, why not)
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     * @param notSuppReasonBuff [OUT] Specifies the reason a Vpool change is not
     *            supported between the two Vpool.
     * @return true if the add RP protection operation is allowed, false otherwise.
     */
    public static boolean isSupportedAddRPProtectionVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            DbClient dbClient, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedAddRPProtectionVirtualPoolChange from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        // Make sure the Vpool are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // RP protection already exists
        if (volume.checkForRp() || VirtualPool.vPoolSpecifiesProtection(currentVpool)) {
            notSuppReasonBuff.append("Can't add RecoverPoint Protection since it already exists.");
            return false;
        }

        // Adding RP+VPLEX/MetroPoint protection to a non-VPLEX volume is not supported
        if (!VirtualPool.vPoolSpecifiesHighAvailability(currentVpool) && VirtualPool.vPoolSpecifiesRPVPlex(newVpool)) {
            notSuppReasonBuff.append("Can't add RecoverPoint+VPLEX Protection directly to non-VPLEX volume. Import to VPLEX first.");
            return false;
        }

        // Adding MetroPoint protection to a VPLEX Local volume is not supported
        if (VirtualPool.vPoolSpecifiesHighAvailability(currentVpool)
                && !VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(currentVpool)
                && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            notSuppReasonBuff.append("Can't add MetroPoint Protection directly to VPLEX Local volume. "
                    + "Upgrade from VPLEX Local to VPLEX Distributed first.");
            return false;
        }

        // Throw an exception if any of the following properties are different
        // between the current and new Vpool. The check for continuous and protection-
        // based settings change is below.
        String[] include = new String[] { TYPE, VARRAYS,
                REF_VPOOL, MIRROR_VPOOL,
                FAST_EXPANSION, ACLS,
                INACTIVE, PROTOCOLS, PROVISIONING_TYPE,
                USE_MATCHED_POOLS, ARRAY_INFO,
                DRIVE_TYPE, AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_IOPS,
                HOST_IO_LIMIT_BANDWIDTH, VMAX_COMPRESSION_ENABLED,
                IS_THIN_VOLUME_PRE_ALLOCATION_ENABLED,
                ASSIGNED_STORAGE_POOLS };
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            return false;
        }

        // If protection changed, we do support upgrade from
        // non-protected to protected. Make sure that change is there.
        include = new String[] { PROTECTION_VARRAY_SETTINGS };
        changes = analyzeChanges(currentVpool, newVpool, include, null, null);
        if (changes.isEmpty()) {
            notSuppReasonBuff.append("These target virtual pool differences are required: ");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            return false;
        }

        // Check for RP+VPLEX/MP case where we would protect a VPLEX Virtual Volume as long
        // as the new vpool specifies HA and Protection both.
        if (VirtualPool.vPoolSpecifiesHighAvailability(currentVpool)
                && (VirtualPool.vPoolSpecifiesRPVPlex(newVpool)
                        || VirtualPool.vPoolSpecifiesMetroPoint(newVpool))) {
            VirtualPoolChangeOperationEnum op = vplexCommonChecks(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff, include);

            if (op == null || !op.equals(VirtualPoolChangeOperationEnum.RP_PROTECTED)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines if the volume qualifies for SRDF protection. (and if not, why not)
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     * @return true if SRDF virtual pool change is allowed
     */
    public static boolean isSupportedSRDFVolumeVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            DbClient dbClient, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedSRDFVolumeVirtualPoolChange from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        // Make sure that the volume is vmax volume. Only vmax supports srdf protection.
        URI storageDeviceURI = volume.getStorageController();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageDeviceURI);
        if (!(DiscoveredDataObject.Type.vmax.name().equals(storageSystem.getSystemType()))) {
            notSuppReasonBuff.append("Volume is not VMAX volume.");
            return false;
        }

        // Make sure the Vpool are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // Throw an exception if any of the following properties are different
        // between the current and new Vpool. The check for continuous and protection-
        // based settings change is below.
        String[] include = new String[] { TYPE, VARRAYS,
                REF_VPOOL, MIRROR_VPOOL, HIGH_AVAILABILITY, PROTECTION_VARRAY_SETTINGS,
                FAST_EXPANSION, ACLS, INACTIVE,
                NUM_PATHS, PATHS_PER_INITIATOR, MIN_PATHS,
                AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH, HOST_IO_LIMIT_IOPS, VMAX_COMPRESSION_ENABLED };
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            s_logger.info("Virtual Pool change not supported: {}", notSuppReasonBuff.toString());
            return false;
        }

        // TODO: SYSTEM_TYPE can change, but only from ANY to VMAX, but not VMAX to VNX or anything to VNX CTRL-276

        // If protection changed, we do support upgrade from
        // non-protected to protected. Make sure that change is there.
        include = new String[] { REMOTECOPY_VARRAY_SETTINGS };
        changes = analyzeChanges(currentVpool, newVpool, include, null, null);
        if (changes.isEmpty()) {
            notSuppReasonBuff.append("These target virtual pool differences are required: ");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            return false;
        }

        return true;
    }

    /**
     * Determine if the volume qualifies for the addition of continuous copies.
     * 
     * @param volume
     * @param currentVpool
     * @param newVpool
     * @param dbClient
     * @param notSuppReasonBuff
     * @return
     */
    public static boolean isSupportedAddMirrorsVirtualPoolChange(Volume volume, VirtualPool currentVpool,
            VirtualPool newVpool, DbClient dbClient,
            StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedAddMirrorsVirtualPoolChange from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(currentVpool.getHighAvailability()) &&
                VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(newVpool.getHighAvailability())) {
            return isSupportedAddMirrorsVirtualPoolChangeForVplexDistributed(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff);
        }

        // Throw an exception if any of the following properties are different
        // between the current and new VirtualPool. The check for continuous and protection-
        // based settings change is below.
        String[] include = new String[] { TYPE, VARRAYS, REF_VPOOL, HIGH_AVAILABILITY, PROTECTION_VARRAY_SETTINGS,
                FAST_EXPANSION, ACLS, INACTIVE, DRIVE_TYPE, ARRAY_INFO, PROVISIONING_TYPE, PROTOCOLS };
        // Throw an exception if any of the following properties values from current vpool
        // are not contained in new virtual pool
        String[] contain = new String[] { MATCHED_POOLS, ASSIGNED_STORAGE_POOLS };

        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, contain);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            return false;
        }

        return true;
    }

    /**
     * This method is used for the VPLEX Distributed volume to check if add mirror(s) is
     * supported by changing vpool to newVpool.
     * 
     * @param volume The reference to the volume
     * @param currentVpool The reference to the current virtual pool for the volume
     * @param newVpool The reference to new virtual pool
     * @param dbClient an instance of {@link DbClient}
     * @param notSuppReasonBuff out param for not supported reason
     * @return true if add mirrors supported else false
     */
    private static boolean isSupportedAddMirrorsVirtualPoolChangeForVplexDistributed(Volume volume, VirtualPool currentVpool,
            VirtualPool newVpool, DbClient dbClient,
            StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedAddMirrorsVirtualPoolChangeForVplexDistributed from [%s] to [%s]...",
                currentVpool.getLabel(), newVpool.getLabel()));
        boolean supported = false;

        // This if section is basically checking source side vpool to make sure its good to be changed with newVpool
        if (newVpool.getMaxNativeContinuousCopies() > 0 && newVpool.getMirrorVirtualPool() != null) {
            String[] include = new String[] { TYPE, VARRAYS, REF_VPOOL, HIGH_AVAILABILITY, PROTECTION_VARRAY_SETTINGS,
                    FAST_EXPANSION, ACLS, INACTIVE, DRIVE_TYPE, ARRAY_INFO, PROVISIONING_TYPE, PROTOCOLS };
            // Throw an exception if any of the following properties values from current vpool
            // are not contained in new virtual pool
            String[] contain = new String[] { MATCHED_POOLS, ASSIGNED_STORAGE_POOLS };

            // analyzeChanges will ensure that elements in the include list donot change between
            // currentVpool and newVpool and elements in the contain list must be present in the
            // newVpool plus newVpool can contain extra other storage pools in those two properties.
            Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, contain);
            if (!changes.isEmpty()) {
                notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
                fillInNotSupportedReasons(changes, notSuppReasonBuff);
                return false;
            }

            supported = true;
        }

        // This section is checking HA side vpool to make sure its good to be changed with the newVpool Ha Vpool
        VirtualPool currentHaVpool = VirtualPool.getHAVPool(currentVpool, dbClient);
        if (currentHaVpool == null) {
            currentHaVpool = currentVpool;
        }
        VirtualPool newHaVpool = VirtualPool.getHAVPool(newVpool, dbClient);
        if (currentHaVpool != null && newHaVpool != null) {
            if (newHaVpool.getMaxNativeContinuousCopies() > 0 && newHaVpool.getMirrorVirtualPool() != null) {
                String[] include = new String[] { TYPE, VARRAYS, REF_VPOOL, PROTECTION_VARRAY_SETTINGS,
                        FAST_EXPANSION, ACLS, INACTIVE, DRIVE_TYPE, ARRAY_INFO, PROVISIONING_TYPE, PROTOCOLS };
                // Throw an exception if any of the following properties values from current vpool
                // are not contained in new virtual pool
                String[] contain = new String[] { MATCHED_POOLS, ASSIGNED_STORAGE_POOLS };

                // If HaVpool for the newVpool is enabled for continuous copies then we need
                // to ensure that some of the properties as mentioned in the include list do
                // not change for the ha vpool and values for the properties in contain list
                // are present in the newVpool haVpool plus it can contain extra values too.
                // Note we don't need to check for HIGH_AVAILABILITY property in case of HA Vpool.
                // Before user could have ha Vpool with no HA set and now can move to HA set with
                // continuous copies enabled.

                Map<String, Change> changes = analyzeChanges(currentHaVpool, newHaVpool, include, null, contain);
                if (!changes.isEmpty()) {
                    notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
                    fillInNotSupportedReasons(changes, notSuppReasonBuff);
                    return false;
                }
                supported = true;
            }
        }

        return supported;
    }

    /**
     * Checks to see if only the Export Path Params have changed.
     * 
     * @param volume The volume to check
     * @param currentVpool The volume's current vpool
     * @param newVpool The target vpool
     * @param dbClient DBClient reference
     * @param notSuppReasonBuff Buffer to store reasons for not being supported
     * @return true is path params changes are allowed, false otherwise
     */
    public static boolean isSupportedPathParamsChange(Volume volume,
            VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient, StringBuffer notSuppReasonBuff) {
        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // First, check that either NUM_PATHS or PATHS_PER_INITIATOR changed
        String[] included = new String[] { NUM_PATHS, PATHS_PER_INITIATOR };
        if (analyzeChanges(currentVpool, newVpool, included, null, null).isEmpty()) {
            notSuppReasonBuff.append("Did not change MAX_PATHS or PATHS_PER_INITIATOR. ");
            return false;
        }
        // Second, check that nothing other than the excluded attributes changed.
        List<String> excluded = new ArrayList<String>();
        String[] exclude = new String[] { NUM_PATHS, PATHS_PER_INITIATOR, MIN_PATHS,
                THIN_VOLUME_PRE_ALLOCATION_PERCENTAGE };
        excluded.addAll(Arrays.asList(exclude));
        excluded.addAll(Arrays.asList(GENERALLY_EXCLUDED));
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            s_logger.info("Virtual Pool change not supported, "
                    + "these target pool differences are invalid: {}", notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Check to see if the current VirtualPool is the same as the requested VirtualPool.
     * 
     * @param current The current vpool
     * @param requested The target vpool
     * @param notSuppReasonBuff Buffer to store reasons for not being supported
     * @return true if the vpools are the same, false otherwise
     */
    public static boolean isSameVirtualPool(VirtualPool current, VirtualPool requested, StringBuffer notSuppReasonBuff) {
        if (current.getId().equals(requested.getId())) {
            String msg = String.format("The target virtual pool [%s] is the same as current virtual pool.", requested.getLabel());
            s_logger.info(msg);
            if (notSuppReasonBuff != null) {
                notSuppReasonBuff.append(msg);
            }
            return true;
        }
        return false;
    }

    private static boolean isSameVirtualPool(VirtualPool current, VirtualPool requested) {
        return isSameVirtualPool(current, requested, null);
    }

    /**
     * Checks to see if the replication mode change is supported.
     * 
     * @param currentVpool the source virtual pool
     * @param newVpool the target virtual pool
     * @param notSuppReasonBuff the not supported reason string buffer
     * @return true if replication mode change is supported, false otherwise
     */
    public static boolean isSupportedReplicationModeChange(VirtualPool currentVpool, VirtualPool newVpool, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedReplicationModeChange from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // Both the source and target vpools must specify RP protection.
        // NOTE: If support for SRDF is added in the future, we must modify the conditions
        if (!VirtualPool.vPoolSpecifiesProtection(currentVpool) || !VirtualPool.vPoolSpecifiesProtection(newVpool)) {
            notSuppReasonBuff
                    .append(String
                            .format("Replication Mode virtual pool change is not supported for target virtual pool %s.  Cannot modify the replication mode if both the source and target vpools do not specify RP protection.",
                                    newVpool.getLabel()));
            s_logger.info(notSuppReasonBuff.toString());
            return false;
        }

        // First, check that RP_COPY_MODE changed.
        String[] included = new String[] { RP_COPY_MODE };
        if (analyzeChanges(currentVpool, newVpool, included, null, null).isEmpty()) {
            notSuppReasonBuff
                    .append(String
                            .format(
                                    "Replication Mode virtual pool change is not supported for target virtual pool %s. There is no change in replication mode.",
                                    newVpool.getLabel()));
            s_logger.info(notSuppReasonBuff.toString());
            return false;
        }

        // Check that nothing other than the excluded attributes changed.
        List<String> excluded = new ArrayList<String>();
        String[] exclude = new String[] { RP_COPY_MODE, RP_RPO_VALUE, RP_RPO_TYPE, PROTECTION_VARRAY_SETTINGS };
        excluded.addAll(Arrays.asList(exclude));
        excluded.addAll(Arrays.asList(GENERALLY_EXCLUDED));
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append(String.format("These target virtual pool differences are invalid: "));
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            s_logger.info(String.format("Replication Mode virtual pool change not supported. %s. Parameters other than %s were changed.",
                    notSuppReasonBuff.toString(), excluded.toString()));
            return false;
        }

        return true;
    }

    /**
     * Checks to see if only the Auto-tiering policy and/or host io limits (only for vmax) has changed.
     * 
     * @param volume the volume
     * @param currentVpool the current vPool
     * @param newVpool the new vPool
     * @param _dbClient the _db client
     * @param notSuppReasonBuff the not supported reason buff
     * @return true, if it is supported Auto-tiering policy and/or host io limits (only for vmax)change
     */
    public static boolean isSupportedAutoTieringPolicyAndLimitsChange(Volume volume,
            VirtualPool currentVpool, VirtualPool newVpool, DbClient _dbClient,
            StringBuffer notSuppReasonBuff) {
        /**
         * Case 1 : from one Auto-tiering Policy (current vPool) to another Auto-tiering policy (new vPool).
         * - System type in both vPools should remain same.
         * Case 2 : from NONE (no Auto-tiering policy in current vPool) to some Auto-tiering policy (new vPool).
         * - System type should remain same
         * (take source system type from either volume or current vPool).
         * Case 3 : from some Auto-tiering policy (current vPool) to NONE (no Auto-tiering policy in new vPool).
         * - no need to check whether system type has changed.
         * Case 4 : from host io limit bandwidth and/or iops fron current vpool are different from new vPool.
         * 
         */
        s_logger.info(String.format("Checking isSupportedAutoTieringPolicyAndLimitsChange from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // First, check that AUTO_TIER_POLICY_NAME changed.
        String[] included = INCLUDED_AUTO_TIERING_POLICY_LIMITS_COMPRESSION_CHANGE;
        if (analyzeChanges(currentVpool, newVpool, included, null, null).isEmpty()) {
            notSuppReasonBuff
                    .append("Did not change AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH, HOST_IO_LIMIT_IOPS or VMAX_AF_COMPRESSION");
            return false;
        }

        // Second, check system type field.
        StringSet currentSystemType = null;
        StringSet newSystemType = null;
        if (currentVpool.getArrayInfo() != null) {
            currentSystemType = (StringSet) currentVpool.getArrayInfo()
                    .get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE).clone();
        }
        // if current vPool is not associated with any system type, get it from volume.
        if (currentSystemType == null || currentSystemType.isEmpty() || currentSystemType.contains(NONE)) {
            URI systemURI = volume.getStorageController();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
            if (currentSystemType == null) {
                currentSystemType = new StringSet();
            }
            currentSystemType.remove(NONE);
            currentSystemType.add(system.getSystemType());
        }
        if (newVpool.getArrayInfo() != null) {
            newSystemType = newVpool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
        }
        // compare system type
        if (newSystemType != null && !newSystemType.contains(NONE)) {
            if (!currentSystemType.equals(newSystemType)) {
                notSuppReasonBuff
                        .append("Auto-tiering Policy change: system_type between source vPool/Volume and target vPool is not same.");
                return false; // system type not same
            }
        }

        // Third, check that target vPool has volume's storage pool in its matched pools list.
        // if target vPool has manual pool selection enabled, then volume's pool should be in assigned pools list.
        if (!checkTargetVpoolHasVolumePool(volume, currentVpool, newVpool, _dbClient)) {
            String msg = "Auto-tiering Policy change: Target vPool does not have Volume's Storage Pool in its matched/assigned pools list.";
            if (VirtualPool.vPoolSpecifiesHighAvailability(currentVpool)
                    && VirtualPool.vPoolSpecifiesHighAvailability(newVpool)) {
                msg = "Auto-tiering Policy change: Target Vplex/Vplex HA vPool does not have Volume's Storage Pool in its matched/assigned pools list.";
            }
            notSuppReasonBuff.append(msg);
            s_logger.info("Virtual Pool change not supported: {}", notSuppReasonBuff.toString());
            return false;
        }

        // Last, check that nothing other than the excluded attributes changed.
        List<String> excluded = new ArrayList<String>();
        String[] exclude = EXCLUDED_AUTO_TIERING_POLICY_LIMITS_CHANGE;
        excluded.addAll(Arrays.asList(exclude));
        excluded.addAll(Arrays.asList(GENERALLY_EXCLUDED));
        // PROTECTION_VARRAY_SETTINGS changes every time a vpool is duplicated so we will ignore it, otherwise
        // this change vpool operation is blocked.
        // RP_RPO_VALUE will be updated from null to 0 if any vpool update is performed so we will ignore it,
        // otherwise this change vpool operation is blocked.
        excluded.addAll(Arrays.asList(RP_RPO_VALUE, PROTECTION_VARRAY_SETTINGS));
        if (VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(currentVpool)
                && VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(newVpool)) {
            // get current & new HA vPools and compare
            VirtualPool currentHAVpool = getHaVpool(currentVpool, _dbClient);
            VirtualPool newHAVpool = getHaVpool(newVpool, _dbClient);
            if (!isSameVirtualPool(currentHAVpool, newHAVpool)) {
                s_logger.info("Comparing HA vPool attributes {} {}", currentHAVpool.getLabel(), newHAVpool.getLabel());
                Map<String, Change> changes = analyzeChanges(currentHAVpool, newHAVpool, null, excluded.toArray(exclude), null);
                if (!changes.isEmpty()) {
                    logNotSupportedReasonForTieringPolicyChange(changes, notSuppReasonBuff, exclude, "HA vPool");
                    return false;
                }
            }

            // ignore VPLEX HA vArray/vPool settings difference when the new vPool satisfies Tiering Policy change
            excluded.add(HA_VARRAY_VPOOL_MAP);
        }

        // If both vPools specifies SRDF then we should exclude REMOTECOPY_VARRAY_SETTINGS at this point
        // because this value will have VpoolRemoteCopyProtectionSettings URI, which will be unique for
        // any newly created SRDF vPool.Preventing the shallow comparison here but will be doing deep comparison later.
        boolean vpoolsSpecifiesSRDF = VirtualPool.vPoolSpecifiesSRDF(currentVpool) && VirtualPool.vPoolSpecifiesSRDF(newVpool);
        if (vpoolsSpecifiesSRDF) {
            s_logger.info("Excluding {} for vpool comparison, will handle it later.", REMOTECOPY_VARRAY_SETTINGS);
            excluded.add(REMOTECOPY_VARRAY_SETTINGS);
        }

        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
        if (!changes.isEmpty()) {
            logNotSupportedReasonForTieringPolicyChange(changes, notSuppReasonBuff, exclude, "vPool");
            return false;
        }

        // Check for REMOTECOPY_VARRAY_SETTINGS in case of SRDF vpool
        if (vpoolsSpecifiesSRDF) {
            return checkForRemoteProtectionSettings(currentVpool, newVpool, _dbClient, notSuppReasonBuff);
        }
        return true;

    }

    /**
     * For Auto-tiering policy change check, it logs the not supported reasons.
     * 
     * @param changes Changes found
     * @param notSuppReasonBuff Buffer containing current unsupported reasons
     * @param exclude Checks that are excluded
     * @param vPoolType The vpool type
     */
    private static void logNotSupportedReasonForTieringPolicyChange(Map<String, Change> changes, StringBuffer notSuppReasonBuff,
            String[] exclude, String vPoolType) {
        notSuppReasonBuff.append(String.format("These target %s differences are invalid: ", vPoolType));
        for (String key : changes.keySet()) {
            s_logger.info("Unexpected Auto-tiering Policy {} attribute change: {}", vPoolType, key);
            notSuppReasonBuff.append(key + " ");
        }
        s_logger.info("Virtual Pool change not supported {}", notSuppReasonBuff.toString());
        s_logger.info(String.format("Parameters other than %s were changed",
                Arrays.toString(exclude)));
    }

    /**
     * Check for for any differences between two vpool's 'remoteProtectionSettings' field.
     * 
     * @param currentVpool the current vPool
     * @param newVpool new vPool
     * @param _dbClient the dbclient
     * @param notSuppReasonBuff Buffer containing current unsupported reasons
     * @return true if there is no difference else false
     */
    private static boolean checkForRemoteProtectionSettings(VirtualPool currentVpool, VirtualPool newVpool, DbClient _dbClient,
            StringBuffer notSuppReasonBuff) {

        Map<URI, VpoolRemoteCopyProtectionSettings> currentSettingMap = VirtualPool.getRemoteProtectionSettings(currentVpool,
                _dbClient);
        Map<URI, VpoolRemoteCopyProtectionSettings> newSettingMap = VirtualPool.getRemoteProtectionSettings(newVpool,
                _dbClient);
        for (URI varrayURI : currentSettingMap.keySet()) {
            if (!newSettingMap.keySet().contains(varrayURI)) {
                s_logger.info("Unexpected Auto-tiering Policy {} attribute change: {}", "SRDF vPool", REMOTECOPY_VARRAY_SETTINGS);
                notSuppReasonBuff.append(String.format("These target %s differences are invalid: ", "SRDF vPool"));
                notSuppReasonBuff.append(REMOTECOPY_VARRAY_SETTINGS + " ");
                return false;
            } else {
                Map<String, Change> change = analyzeChanges(currentSettingMap.get(varrayURI),
                        newSettingMap.get(varrayURI), null, GENERALLY_EXCLUDED, null);
                if (!change.isEmpty()) {
                    logNotSupportedReasonForTieringPolicyChange(change, notSuppReasonBuff, null, "SRDF vPool");
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check that target vPool has volume's storage pool in its matched pools list.
     * If target vPool has manual pool selection enabled, then volume's pool should be in assigned pools list.
     * 
     * In case of VPLEX Distributed vPool, the check is also done for HA vPool.
     * 
     * @param volume Volume involved in change vpool operation
     * @param currentVpool The current volume's vpool
     * @param newVpool The target vpool
     * @param dbClient DBClient reference
     * @return true if the target vpool has volume's storage pool in its matched pools list, false otherwise.
     */
    private static boolean checkTargetVpoolHasVolumePool(Volume volume,
            VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient) {
        boolean vPoolHasVolumePool = false;
        if (!NullColumnValueGetter.isNullURI(volume.getPool())) {
            vPoolHasVolumePool = doesNewVpoolContainsVolumePool(volume.getPool(), newVpool);
        } else if (VirtualPool.vPoolSpecifiesHighAvailability(currentVpool)
                && VirtualPool.vPoolSpecifiesHighAvailability(newVpool)) {
            // check backend volume's pool with new vPool's pools
            Volume backendSrcVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient, false);
            if (backendSrcVolume != null) {
                s_logger.info("VPLEX backend Source Volume {}, new vPool {}", backendSrcVolume.getId(), newVpool.getId());
                vPoolHasVolumePool = doesNewVpoolContainsVolumePool(backendSrcVolume.getPool(), newVpool);
            } else {
                s_logger.warn("backend source volume could not be found for VPLEX volume " + volume.forDisplay());
            }
            // check backend distributed volume's pool with new HA vPool's pools
            if (VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(currentVpool)
                    && VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(newVpool)) {
                Volume backendDistVolume = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient, false);
                VirtualPool newHAvPool = getHaVpool(newVpool, dbClient);
                s_logger.info("VPLEX backend Distributed Volume {}, new HA vPool {}", backendDistVolume.getId(), newHAvPool.getId());
                if (newHAvPool != null && backendDistVolume != null) {
                    vPoolHasVolumePool = doesNewVpoolContainsVolumePool(backendDistVolume.getPool(), newHAvPool);
                }
            }
        }
        return vPoolHasVolumePool;
    }

    /**
     * Returns true if the vPool contains the given storage pool in its valid pools list.
     * 
     * @param volumePool Volume's vpool
     * @param vPool Vpool to check
     * @return true if the vPool contains the given storage pool in its valid pools list, false otherwise
     */
    private static boolean doesNewVpoolContainsVolumePool(URI volumePool, VirtualPool vPool) {
        boolean vPoolHasVolumePool = false;
        if (volumePool != null && vPool != null) {
            StringSet poolsToCheck = vPool.getUseMatchedPools() ? vPool.getMatchedStoragePools() : vPool.getAssignedStoragePools();
            if (poolsToCheck != null && poolsToCheck.contains(volumePool.toString())) {
                vPoolHasVolumePool = true;
            }
        }
        return vPoolHasVolumePool;
    }

    /**
     * Gets the class variable indicating if the new vpool specified HA or not
     * 
     * @return class variable indicating if the new vpool specified HA or not
     */
    public static boolean getNewVpoolDoesNotSpecifyHaVpool() {
        return newVpoolDoesNotSpecifyHaVpool;
    }

    /**
     * Convenience method to add extra element to an array
     * 
     * @param array reference to an array
     * @param element new element to be added to the array
     * @return the array update with new element
     */
    private static String[] addElementToArray(String[] array, String element) {
        Arrays.copyOf(array, array.length + 1);
        array[array.length - 1] = element;
        return array;
    }

    /**
     * Determines if the volume qualifies for an upgrade to Metropoint.
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     * @param notSuppReasonBuff [OUT] Specifies the reason a Vpool change is not
     *            supported between the two Vpool.
     * @return true if the upgrade to MP operation is allowed, false otherwise.
     */
    public static boolean isSupportedUpgradeToMetroPointVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            DbClient dbClient, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedUpgradeToMetroPointVirtualPoolChange from [%s] to [%s]...",
                currentVpool.getLabel(), newVpool.getLabel()));

        // Make sure the Vpool are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // RP+VPLEX Distributed to MetroPoint is supported
        if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {

            if (null != volume.getAssociatedVolumes()
                    && !volume.getAssociatedVolumes().isEmpty()
                    && volume.getAssociatedVolumes().size() > 1) {

                // Return false if any of the following properties are different
                // between the current and new vpool.
                String[] include = new String[] { TYPE, VARRAYS,
                        REF_VPOOL, MIRROR_VPOOL,
                        HIGH_AVAILABILITY,
                        FAST_EXPANSION, ACLS,
                        INACTIVE, HA_CONNECTED_TO_RP, JOURNAL_SIZE };
                Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, null);
                if (!changes.isEmpty()) {
                    notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
                    fillInNotSupportedReasons(changes, notSuppReasonBuff);
                    return false;
                }

                if (currentVpool.getProtectionVarraySettings() == null
                        && currentVpool.getProtectionVarraySettings().isEmpty()) {
                    notSuppReasonBuff.append(String.format("Vpool [%s] does not specify protection",
                            currentVpool.getLabel()));
                    return false;
                }

                if (newVpool.getProtectionVarraySettings() == null
                        && newVpool.getProtectionVarraySettings().isEmpty()) {
                    notSuppReasonBuff.append(String.format("Vpool [%s] does not specify protection",
                            newVpool.getLabel()));
                    return false;
                }

                // Multiple existing targets not supported for now
                // TODO BH: Maybe at some point we can consume existing targets and try to add
                // copies as long as they line up. AND/OR support the newVpool having 1 - 3 targets
                // as long as one target lines up and is CRR. Could get complex.
                if (currentVpool.getProtectionVarraySettings().size() > 1
                        || newVpool.getProtectionVarraySettings().size() > 1) {
                    notSuppReasonBuff.append("Multiple targets not supported for upgrade to MetroPoint (for now).");
                    return false;
                } else {
                    // Check the Targets...
                    // Need to make sure that the new vpool has the same target varray/vpool
                    // defined to make the transition to MetroPoint seemless.
                    // Also applies to target journal varray/vpool.
                    for (Map.Entry<String, String> entry : newVpool.getProtectionVarraySettings().entrySet()) {
                        // Make sure they both use the same target varray
                        if (currentVpool.getProtectionVarraySettings().containsKey(entry.getKey())) {
                            // Now make sure they both use the same target vpool, this is pretty
                            // restrictive but our code path has to be precise for now.
                            String newSettingsId = entry.getValue();
                            String currentSettingsId = currentVpool.getProtectionVarraySettings().get(entry.getKey());

                            // Grab the current protection varray settings
                            VpoolProtectionVarraySettings currentProtectionVarraySetting = dbClient.queryObject(
                                    VpoolProtectionVarraySettings.class,
                                    URI.create(currentSettingsId));

                            // Grab the new protection varray settings
                            VpoolProtectionVarraySettings newProtectionVarraySetting = dbClient.queryObject(
                                    VpoolProtectionVarraySettings.class,
                                    URI.create(newSettingsId));

                            String currentTargetVpool = NullColumnValueGetter.getStringValue(currentProtectionVarraySetting
                                    .getVirtualPool());
                            String currentTargetJournalVarray = NullColumnValueGetter.getStringValue(currentProtectionVarraySetting
                                    .getJournalVarray());
                            String currentTargetJournalVpool = NullColumnValueGetter.getStringValue(currentProtectionVarraySetting
                                    .getJournalVpool());

                            String newTargetVpool = NullColumnValueGetter.getStringValue(newProtectionVarraySetting.getVirtualPool());
                            String newTargetJournalVarray = NullColumnValueGetter.getStringValue(newProtectionVarraySetting
                                    .getJournalVarray());
                            String newTargetJournalVpool = NullColumnValueGetter.getStringValue(newProtectionVarraySetting
                                    .getJournalVpool());

                            // Make sure the target vpools are the same
                            if (!currentTargetVpool.equals(newTargetVpool)) {
                                notSuppReasonBuff.append("Target virtual pools do not match.");
                                return false;
                            }

                            // Make sure the target journal varrays are the same
                            if (!currentTargetJournalVarray.equals(newTargetJournalVarray)) {
                                notSuppReasonBuff.append("Target journal virtual arrays do not match.");
                                return false;
                            }

                            // Make sure the target journal vpools are the same
                            if (!currentTargetJournalVpool.equals(newTargetJournalVpool)) {
                                notSuppReasonBuff.append("Target journal virtual vpools do not match.");
                                return false;
                            }
                        } else {
                            notSuppReasonBuff.append("Target virtual arrays do not match.");
                            return false;
                        }
                    }

                    // Targets are OK if we reach here so on to the next check for Source/HA journals...
                    //
                    // The current RP+VPLEX vpool is either protecting the Source site or HA site.
                    // This will make a difference in checking which journal side should be added when
                    // upgrading to MetroPoint.
                    // If we were protecting the Source site, then the HA journal changes are OK and we need to check the Source Journal
                    // settings for changes.
                    // If we were protecting the HA site, then the Source journal changes are OK and we need to check the HA Journal
                    // settings for changes.
                    String currentJournalVarray = null;
                    String currentJournalVpool = null;
                    String newJournalVarray = null;
                    String newJournalVpool = null;

                    if (VirtualPool.isRPVPlexProtectHASide(currentVpool)) {
                        currentJournalVarray = NullColumnValueGetter.getStringValue(currentVpool.getStandbyJournalVarray());
                        currentJournalVpool = NullColumnValueGetter.getStringValue(currentVpool.getStandbyJournalVpool());
                        newJournalVarray = NullColumnValueGetter.getStringValue(newVpool.getStandbyJournalVarray());
                        newJournalVpool = NullColumnValueGetter.getStringValue(newVpool.getStandbyJournalVpool());
                    } else {
                        currentJournalVarray = NullColumnValueGetter.getStringValue(currentVpool.getJournalVarray());
                        currentJournalVpool = NullColumnValueGetter.getStringValue(currentVpool.getJournalVpool());
                        newJournalVarray = NullColumnValueGetter.getStringValue(newVpool.getJournalVarray());
                        newJournalVpool = NullColumnValueGetter.getStringValue(newVpool.getJournalVpool());
                    }

                    // Source journals need to match up
                    if (!currentJournalVarray.equals(newJournalVarray)) {
                        notSuppReasonBuff.append("Source journal virtual arrays do not match.");
                        return false;
                    }

                    if (!currentJournalVpool.equals(newJournalVpool)) {
                        notSuppReasonBuff.append("Source journal virtual pools do not match.");
                        return false;
                    }
                }
            } else {
                // RP+VPLEX Local to MetroPoint
                // Unsupported for now
                notSuppReasonBuff.append("RP+VPLEX Local to MetroPoint change vpool unsupported for now.");
                return false;
            }
        } else {
            // Unsupported
            notSuppReasonBuff.append("Upgrade to Metropoint operation is not supported.");
            return false;
        }

        s_logger.info("Upgrade to Metropoint operation is supported.");

        return true;
    }

    /**
     * Checks to see if the remove protection operation is supported.
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     * @param notSuppReasonBuff Buffer for error messages
     * @return true if remove protection is supported
     */
    public static boolean isSupportedRPRemoveProtectionVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            DbClient dbClient, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedRPRemoveProtectionVirtualPoolChange from [%s] to [%s]...",
                currentVpool.getLabel(), newVpool.getLabel()));

        // Make sure the Vpools are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        if (volume.checkForRp()
                && VirtualPool.vPoolSpecifiesProtection(currentVpool)
                && !VirtualPool.vPoolSpecifiesProtection(newVpool)) {
            // Check that nothing other than the excluded attributes changed.
            List<String> excluded = new ArrayList<String>();
            String[] exclude = new String[] { PROTECTION_VARRAY_SETTINGS, RP_RPO_VALUE, RP_RPO_TYPE,
                    RP_COPY_MODE, ARRAY_INFO, DRIVE_TYPE, JOURNAL_SIZE, JOURNAL_VARRAY, JOURNAL_VPOOL,
                    MULTI_VOLUME_CONSISTENCY, METROPOINT, STANDBY_JOURNAL_VARRAY, STANDBY_JOURNAL_VPOOL };
            excluded.addAll(Arrays.asList(exclude));
            excluded.addAll(Arrays.asList(GENERALLY_EXCLUDED));
            Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
            if (!changes.isEmpty()) {
                notSuppReasonBuff.append("These target virtual pool differences are invalid: ");
                fillInNotSupportedReasons(changes, notSuppReasonBuff);
                s_logger.info("Remove Protection change not supported, "
                        + "these target pool differences are invalid: {}", notSuppReasonBuff.toString());
                return false;
            }
        } else {
            s_logger.warn("RP remove protection operation is NOT supported.");
            return false;
        }

        s_logger.info("RP remove protection operation is supported.");
        return true;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param currentVpool the source virtual pool
     * @param newVpool the target virtual pool
     * @param notSuppReasonBuff the not supported reason string buffer
     * @return true if file replication change is supported
     */
    public static boolean isSupportedFileReplicationChange(VirtualPool currentVpool, VirtualPool newVpool, StringBuffer notSuppReasonBuff) {
        s_logger.info(String.format("Checking isSupportedFileReplicationChange from [%s] to [%s]...", currentVpool.getLabel(),
                newVpool.getLabel()));
        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        if (!newVpool.getVirtualArrays().containsAll(currentVpool.getVirtualArrays())) {
            notSuppReasonBuff
                    .append(String
                            .format("Virtual pool change not supported, Because Target Varray is different %s.",
                                    newVpool.getLabel()));
            s_logger.info(notSuppReasonBuff.toString());
            return false;
        }

        // Check that nothing other than the excluded attributes changed.
        List<String> excluded = new ArrayList<String>();
        String[] exclude = new String[] { FILE_REPLICATION_TYPE, FILE_REPLICATION_COPY_MODE,
                FILE_REPLICATION_RPO_TYPE, FILE_REPLICATION_RPO_VALUE, FILE_REPLICATION_COPIES, VARRAYS, FILE_REPLICATION_AT_PROJECT_LEVEL,
                FILE_REPLICATION_AT_FS_LEVEL, FILE_REPLICATION_SUPPORTED, FILE_SNAPSHOT_SCHEDULE_SUPPORTED };
        excluded.addAll(Arrays.asList(exclude));
        excluded.addAll(Arrays.asList(GENERALLY_EXCLUDED));
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append(String.format("These target virtual pool [%s] differences are invalid: ", newVpool.getLabel()));
            fillInNotSupportedReasons(changes, notSuppReasonBuff);
            s_logger.info(String.format("Virtual pool change not supported. %s. Parameters other than %s were changed.",
                    notSuppReasonBuff.toString(), excluded.toString()));
            return false;
        }

        return true;
    }

    /**
     * From the changes map passed in, assemble all the changes into a comma delimited format.
     * 
     * @param changes A map with all the changes found that are allowed/not allowed
     * @param notSuppReasonBuff Buffer with all the change messages appended.
     */
    private static void fillInNotSupportedReasons(Map<String, Change> changes, StringBuffer notSuppReasonBuff) {
        Set<String> allChanges = new HashSet<String>();
        for (Change foundChange : changes.values()) {
            // Use the plain name field from the change object
            allChanges.add(foundChange.name);
        }
        notSuppReasonBuff.append(Joiner.on(", ").join(allChanges));
        notSuppReasonBuff.append(". ");
    }

    /**
     * Checks to see if the migration operation is supported for RP+VPLEX or MetroPoint.
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     * @param notSuppReasonBuff Buffer for error messages
     * @return true if remove protection is supported
     */
    public static boolean isSupportedRPVPlexMigrationVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            DbClient dbClient, StringBuffer notSuppReasonBuff, List<RPVPlexMigration> validMigrations) {
        s_logger.info(String.format("Checking isSupportedRPVPlexMigrationVirtualPoolChange from [%s] to [%s]...",
                currentVpool.getLabel(), newVpool.getLabel()));

        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // Flag to indicate that at least one valid migration has been found
        boolean validMigrationsFound = false;

        if (volume.checkForRp()
                && VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                && VirtualPool.vPoolSpecifiesRPVPlex(newVpool)) {

            // Number of targets can not be different between the current and new vpool.
            if (currentVpool.getProtectionVarraySettings().size() != newVpool.getProtectionVarraySettings().size()) {
                notSuppReasonBuff.append("Target virtual arrays do not match.");
                return false;
            }

            // Keep track of all the potential migrations
            List<RPVPlexMigration> potentialMigrations = new ArrayList<RPVPlexMigration>();

            // The Source is always a potential candidate for migration
            potentialMigrations.add(new RPVPlexMigration(Volume.PersonalityTypes.SOURCE,
                    volume.getVirtualArray(), currentVpool, newVpool));

            // Source Journal
            boolean invalidMigration = determineRPSourceJournalMigration(volume, currentVpool, newVpool, potentialMigrations,
                    notSuppReasonBuff, dbClient);
            if (invalidMigration) {
                return false;
            }

            // Only MetroPoint configurations will have Standby Journals
            if (VirtualPool.vPoolSpecifiesMetroPoint(currentVpool)
                    && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
                // Standby Journal
                invalidMigration = determineRPStandbyJournalMigration(volume, currentVpool, newVpool, potentialMigrations,
                        notSuppReasonBuff, dbClient);
                if (invalidMigration) {
                    return false;
                }
            }

            // Targets
            invalidMigration = determineRPTargetMigration(volume, currentVpool, newVpool, potentialMigrations,
                    notSuppReasonBuff, dbClient);
            if (invalidMigration) {
                return false;
            }

            // Iterate over all the potential for migrations (SOURCE, TARGET, METADATA).
            for (RPVPlexMigration migration : potentialMigrations) {
                Volume.PersonalityTypes type = migration.getType();
                VirtualPool candidateCurrentVpool = migration.getMigrateFromVpool();
                VirtualPool candidateNewVpool = migration.getMigrateToVpool();

                // Same vpool automatically excludes this entry
                if (isSameVirtualPool(candidateCurrentVpool, candidateNewVpool, notSuppReasonBuff)) {
                    continue;
                }

                String[] include = null;

                if (type.equals(Volume.PersonalityTypes.SOURCE)) {
                    // Ensure these values have NOT changed between ANY of the Source candidate vpools. If they have,
                    // do not allow the RP+VPLEX migration operation to occur.
                    include = new String[] { TYPE, VARRAYS,
                            REF_VPOOL, MIRROR_VPOOL, FAST_EXPANSION, ACLS, INACTIVE, NUM_PATHS,
                            METROPOINT, HIGH_AVAILABILITY, RP_RPO_TYPE, RP_RPO_VALUE, RP_COPY_MODE };
                } else {
                    // Targets/Journals could be using the Source/Target vpool as their vpool
                    // and the user could be looking to migrate to another vpool which is valid
                    // but may have different a HA type / varrays.
                    if (VirtualPool.vPoolSpecifiesHighAvailability(candidateCurrentVpool)
                            && VirtualPool.vPoolSpecifiesHighAvailability(candidateNewVpool)
                            && candidateNewVpool.getVirtualArrays().contains(migration.getVarray().toString())) {
                        include = new String[] { TYPE, REF_VPOOL, MIRROR_VPOOL, FAST_EXPANSION, ACLS, INACTIVE, NUM_PATHS };
                    } else {
                        // Can not consider this migration if the current and new vpool
                        // does not specify HA and the new vpool does not have the varray used in
                        // the migration.
                        s_logger.info(String.format("Vpool [%s](%s) is NOT valid for RP+VPLEX %s migrations",
                                candidateNewVpool.getLabel(),
                                candidateNewVpool.getId()), type.name());
                        continue;
                    }
                }

                // Ensure no unwanted changes are present.
                Map<String, Change> changes = analyzeChanges(candidateCurrentVpool, candidateNewVpool, include, null, null);
                if (!changes.isEmpty()) {
                    notSuppReasonBuff
                            .append(String.format("Changes in the following %s virtual pool are not permitted: ", type.name()));
                    fillInNotSupportedReasons(changes, notSuppReasonBuff);
                    return false;
                }

                // Determine if VPLEX source side will be migrated.
                boolean migrateSourceVolume = VirtualPoolChangeAnalyzer
                        .vpoolChangeRequiresMigration(candidateCurrentVpool, candidateNewVpool);

                // Determine if VPLEX HA side will be migrated.
                boolean migrateHAVolume = false;
                // Ignore HA for Journals as ViPR provisioned RP+VPLEX Journals are always forced to VPLEX Local.
                if (!type.equals(Volume.PersonalityTypes.METADATA)) {
                    VirtualPool candidateCurrentHaVpool = VirtualPoolChangeAnalyzer
                            .getHaVpool(candidateCurrentVpool, dbClient);
                    if (candidateCurrentHaVpool != null) {
                        VirtualPool candidateNewHaVpool = VirtualPoolChangeAnalyzer
                                .getNewHaVpool(candidateCurrentVpool, candidateNewVpool, dbClient);
                        migrateHAVolume = VirtualPoolChangeAnalyzer
                                .vpoolChangeRequiresMigration(candidateCurrentHaVpool, candidateNewHaVpool);
                    }
                }

                if (migrateSourceVolume || migrateHAVolume) {
                    // There is at least one migration candidate, so we can proceed.
                    validMigrationsFound = true;

                    s_logger.info(String.format("Vpool [%s](%s) is valid for RP+VPLEX %s migrations",
                            candidateNewVpool.getLabel(),
                            candidateNewVpool.getId(),
                            type.name()));

                    // If the validMigrations param is not null then keep track of the valid
                    // migrations found.
                    if (validMigrations != null) {
                        validMigrations.add(migration);
                    } else {
                        break;
                    }
                } else {
                    s_logger.info(String.format("Vpool [%s](%s) is NOT valid for RP+VPLEX %s migrations",
                            candidateNewVpool.getLabel(),
                            candidateNewVpool.getId(),
                            type.name()));
                }
            }
        }

        s_logger.info(String.format("RP+VPLEX migration operation is%s supported.",
                validMigrationsFound ? "" : " NOT"));
        return validMigrationsFound;
    }

    /**
     * Determines if there are any Source Journal migrations and adds them to the
     * potentialMigrations container.
     * 
     * @param volume The change vpool volume
     * @param currentVpool The change vpool volume's current vpool
     * @param newVpool The target vpool to move to
     * @param potentialMigrations Container for migrations
     * @param notSuppReasonBuff Buffer for not supported reasons
     * @param dbClient DbClient instance
     * @return true if invalid migration, false otherwise
     */
    private static boolean determineRPSourceJournalMigration(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            List<RPVPlexMigration> potentialMigrations, StringBuffer notSuppReasonBuff, DbClient dbClient) {
        boolean invalidMigration = false;
        // Current Source Journal varray/vpool
        String currentSourceJournalVarrayId = NullColumnValueGetter.getStringValue(currentVpool
                .getJournalVarray());
        String currentSourceJournalVpoolId = NullColumnValueGetter.getStringValue(currentVpool
                .getJournalVpool());
        // New Source Journal varray/vpool
        String newSourceJournalVarrayId = NullColumnValueGetter.getStringValue(newVpool
                .getJournalVarray());
        String newSourceJournalVpoolId = NullColumnValueGetter.getStringValue(newVpool
                .getJournalVpool());

        // If the current and new Source Journal varray/vpool are not set, default them to known values.
        if (currentSourceJournalVarrayId.equals(NullColumnValueGetter.getNullStr())) {
            currentSourceJournalVarrayId = volume.getVirtualArray().toString();
        }

        if (currentSourceJournalVpoolId.equals(NullColumnValueGetter.getNullStr())) {
            currentSourceJournalVpoolId = currentVpool.getId().toString();
        }

        VirtualPool currentSourceJournalVpool = dbClient.queryObject(VirtualPool.class, URI.create(currentSourceJournalVpoolId));

        if (newSourceJournalVpoolId.equals(NullColumnValueGetter.getNullStr())) {
            newSourceJournalVpoolId = newVpool.getId().toString();
        }

        VirtualPool newSourceJournalVpool = dbClient.queryObject(VirtualPool.class, URI.create(newSourceJournalVpoolId));

        if (newSourceJournalVarrayId.equals(NullColumnValueGetter.getNullStr())) {
            if (newSourceJournalVpool.getVirtualArrays() != null
                    && newSourceJournalVpool.getVirtualArrays().contains(volume.getVirtualArray().toString())) {
                newSourceJournalVarrayId = volume.getVirtualArray().toString();
            }
        }

        // Only consider the Source Journal migration if the varrays are the same and the vpools
        // are different and both specify HA.
        if (!currentSourceJournalVpoolId.equals(newSourceJournalVpoolId)) {
            if (currentSourceJournalVarrayId.equals(newSourceJournalVarrayId)
                    && VirtualPool.vPoolSpecifiesHighAvailability(currentSourceJournalVpool)
                    && VirtualPool.vPoolSpecifiesHighAvailability(newSourceJournalVpool)) {
                // Add Source Journal for potential migration
                potentialMigrations.add(new RPVPlexMigration(Volume.PersonalityTypes.METADATA, Volume.PersonalityTypes.SOURCE,
                        URI.create(currentSourceJournalVarrayId), currentSourceJournalVpool, newSourceJournalVpool));
            } else {
                // If the Source Journal vpools are different and both of the Source Journal vpools do not specify HA,
                // then exclude this new vpool for RP+VPLEX migration.
                notSuppReasonBuff.append("Not valid for migration due to changes in RP Source Journal virtual pool / virtual array.");
                invalidMigration = true;
            }
        } else {
            if (!currentSourceJournalVarrayId.equals(newSourceJournalVarrayId)) {
                notSuppReasonBuff.append("Not valid for migration due to changes in RP Source Journal virtual array.");
                invalidMigration = true;
            }
        }

        return invalidMigration;
    }

    /**
     * Determines if there are any Standby Journal migrations and adds them to the
     * potentialMigrations container.
     * 
     * @param volume The change vpool volume
     * @param currentVpool The change vpool volume's current vpool
     * @param newVpool The target vpool to move to
     * @param potentialMigrations Container for migrations
     * @param notSuppReasonBuff Buffer for not supported reasons
     * @param dbClient DbClient instance
     * @return true if invalid migration, false otherwise
     */
    private static boolean determineRPStandbyJournalMigration(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            List<RPVPlexMigration> potentialMigrations, StringBuffer notSuppReasonBuff, DbClient dbClient) {
        boolean invalidMigration = false;

        // Current Standby Journal varray/vpool
        String currentStandbyJournalVarrayId = NullColumnValueGetter.getStringValue(currentVpool
                .getStandbyJournalVarray());
        String currentStandbyJournalVpoolId = NullColumnValueGetter.getStringValue(currentVpool
                .getStandbyJournalVpool());
        // New Standby Journal varray/vpool
        String newStandbyJournalVarrayId = NullColumnValueGetter.getStringValue(newVpool
                .getStandbyJournalVarray());
        String newStandbyJournalVpoolId = NullColumnValueGetter.getStringValue(newVpool
                .getStandbyJournalVpool());

        // If the current Standby Journal varray/vpool are not set, default them to known values.
        if (currentStandbyJournalVarrayId.equals(NullColumnValueGetter.getNullStr())) {
            URI haVarrayURI = getHaVarrayURI(currentVpool);
            currentStandbyJournalVarrayId = (haVarrayURI != null ? haVarrayURI.toString() : NullColumnValueGetter.getNullStr());
        }

        if (currentStandbyJournalVpoolId.equals(NullColumnValueGetter.getNullStr())) {
            VirtualPool currentHaVpool = VirtualPool.getHAVPool(currentVpool, dbClient);
            currentStandbyJournalVpoolId = (currentHaVpool != null ? currentHaVpool.getId().toString() : currentVpool.getId().toString());
        }

        VirtualPool currentStandbyJournalVpool = dbClient.queryObject(VirtualPool.class, URI.create(currentStandbyJournalVpoolId));

        if (newStandbyJournalVarrayId.equals(NullColumnValueGetter.getNullStr())) {
            URI haVarrayURI = getHaVarrayURI(newVpool);
            newStandbyJournalVarrayId = (haVarrayURI != null ? haVarrayURI.toString() : NullColumnValueGetter.getNullStr());
        }

        if (newStandbyJournalVpoolId.equals(NullColumnValueGetter.getNullStr())) {
            VirtualPool newHaVpool = VirtualPool.getHAVPool(newVpool, dbClient);
            newStandbyJournalVpoolId = (newHaVpool != null ? newHaVpool.getId().toString() : newVpool.getId().toString());
        }

        VirtualPool newStandbyJournalVpool = dbClient.queryObject(VirtualPool.class, URI.create(newStandbyJournalVpoolId));

        // Only consider the Standby Journal migration if the varrays are the same and the vpools
        // are different and both specify HA.
        if (!currentStandbyJournalVpoolId.equals(newStandbyJournalVpoolId)) {
            if (currentStandbyJournalVarrayId.equals(newStandbyJournalVarrayId)
                    && VirtualPool.vPoolSpecifiesHighAvailability(currentStandbyJournalVpool)
                    && VirtualPool.vPoolSpecifiesHighAvailability(newStandbyJournalVpool)) {
                // Add Standby Journal for potential migration
                potentialMigrations.add(new RPVPlexMigration(Volume.PersonalityTypes.METADATA, Volume.PersonalityTypes.SOURCE,
                        URI.create(currentStandbyJournalVarrayId), currentStandbyJournalVpool, newStandbyJournalVpool));
            } else {
                // If the Standby Journal vpools are different and both of the Standby Journal vpools do not specify HA,
                // then exclude this new vpool for RP+VPLEX migration.
                notSuppReasonBuff.append("Not valid for migration due to changes in RP Standby Journal virtual pool / virtual array.");
                invalidMigration = true;
            }
        } else {
            if (!currentStandbyJournalVarrayId.equals(newStandbyJournalVarrayId)) {
                notSuppReasonBuff.append("Not valid for migration due to changes in RP Standby Journal virtual array.");
                invalidMigration = true;
            }
        }

        return invalidMigration;
    }

    /**
     * Determines if there are any Target migrations and adds them to the
     * potentialMigrations container.
     * 
     * @param volume The change vpool volume
     * @param currentVpool The change vpool volume's current vpool
     * @param newVpool The target vpool to move to
     * @param potentialMigrations Container for migrations
     * @param notSuppReasonBuff Buffer for not supported reasons
     * @param dbClient DbClient instance
     * @return true if invalid migration, false otherwise
     */
    private static boolean determineRPTargetMigration(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            List<RPVPlexMigration> potentialMigrations, StringBuffer notSuppReasonBuff, DbClient dbClient) {
        boolean invalidMigration = false;

        if (currentVpool.getProtectionVarraySettings() == null
                || newVpool.getProtectionVarraySettings() == null
                || (currentVpool.getProtectionVarraySettings().size() != newVpool.getProtectionVarraySettings().size())) {
            notSuppReasonBuff.append("RP Targets are mismatched.");
            return true;
        }

        // Check the Targets for potential candidates for migration
        for (Map.Entry<String, String> entry : currentVpool.getProtectionVarraySettings().entrySet()) {
            String targetVarrayId = entry.getKey();
            // Make sure we find the same varray in the new vpool, otherwise we can immediately
            // exclude the migration operation.
            if (newVpool.getProtectionVarraySettings().containsKey(targetVarrayId)) {
                String currentProtectionVarraySettingsId = entry.getValue();
                String newProtectionVarraySettingsId = newVpool.getProtectionVarraySettings().get(targetVarrayId);

                // Get the current protection varray settings
                VpoolProtectionVarraySettings currentProtectionVarraySetting = dbClient.queryObject(VpoolProtectionVarraySettings.class,
                        URI.create(currentProtectionVarraySettingsId));

                // Get the new protection varray settings
                VpoolProtectionVarraySettings newProtectionVarraySetting = dbClient.queryObject(VpoolProtectionVarraySettings.class,
                        URI.create(newProtectionVarraySettingsId));

                // Current Target vpool
                String currentTargetVpoolId = NullColumnValueGetter.getStringValue(currentProtectionVarraySetting
                        .getVirtualPool());
                if (currentTargetVpoolId.equals(NullColumnValueGetter.getNullStr())) {
                    currentTargetVpoolId = currentVpool.getId().toString();
                }
                VirtualPool currentTargetVpool = dbClient.queryObject(VirtualPool.class, URI.create(currentTargetVpoolId));

                // New Target vpool
                String newTargetVpoolId = NullColumnValueGetter.getStringValue(newProtectionVarraySetting.getVirtualPool());
                if (newTargetVpoolId.equals(NullColumnValueGetter.getNullStr())) {
                    newTargetVpoolId = newVpool.getId().toString();
                }
                VirtualPool newTargetVpool = dbClient.queryObject(VirtualPool.class, URI.create(newTargetVpoolId));

                // Only allow migrations when both vpools specify HA.
                if (!currentTargetVpoolId.equals(newTargetVpoolId)) {
                    if (VirtualPool.vPoolSpecifiesHighAvailability(currentTargetVpool)
                            && VirtualPool.vPoolSpecifiesHighAvailability(newTargetVpool)) {
                        // Add Target for potential migration
                        potentialMigrations.add(new RPVPlexMigration(Volume.PersonalityTypes.TARGET,
                                URI.create(targetVarrayId), currentTargetVpool, newTargetVpool));
                    } else {
                        // This Target is not a candidate for migration and the vpools are not the exact
                        // same so we can not allow the migration. The Targets could potentially get misaligned
                        // in the new vpool and we can not allow that.
                        notSuppReasonBuff.append("No RP Target migration detected, so RP Target virtual pools must match.");
                        invalidMigration = true;
                        break;
                    }
                }

                // Target Journal
                invalidMigration = determineRPTargetJournalMigration(volume, currentTargetVpool, newTargetVpool, potentialMigrations,
                        notSuppReasonBuff, dbClient, currentProtectionVarraySetting, newProtectionVarraySetting, targetVarrayId);
                if (invalidMigration) {
                    break;
                }
            } else {
                notSuppReasonBuff.append("Target virtual arrays do not match.");
                invalidMigration = true;
                break;
            }
        }

        return invalidMigration;
    }

    /**
     * Determines if there are any Target Journal migrations and adds them to the
     * potentialMigrations container.
     * 
     * @param volume The change vpool volume
     * @param currentVpool The change vpool volume's current vpool
     * @param newVpool The target vpool to move to
     * @param potentialMigrations Container for migrations
     * @param notSuppReasonBuff Buffer for not supported reasons
     * @param dbClient DbClient instance
     * @param currentProtectionVarraySetting Current vpool protection settings
     * @param newProtectionVarraySetting New vpool protection settings
     * @param targetVarrayId Current target varray ID
     * @return true if invalid migration, false otherwise
     */
    private static boolean determineRPTargetJournalMigration(Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            List<RPVPlexMigration> potentialMigrations, StringBuffer notSuppReasonBuff, DbClient dbClient,
            VpoolProtectionVarraySettings currentProtectionVarraySetting, VpoolProtectionVarraySettings newProtectionVarraySetting,
            String targetVarrayId) {
        boolean invalidMigration = false;

        // Current Target Journal varray/vpool
        String currentTargetJournalVarrayId = NullColumnValueGetter.getStringValue(currentProtectionVarraySetting
                .getJournalVarray());
        String currentTargetJournalVpoolId = NullColumnValueGetter.getStringValue(currentProtectionVarraySetting
                .getJournalVpool());

        // New Target Journal varray/vpool
        String newTargetJournalVarrayId = NullColumnValueGetter.getStringValue(newProtectionVarraySetting
                .getJournalVarray());
        String newTargetJournalVpoolId = NullColumnValueGetter.getStringValue(newProtectionVarraySetting
                .getJournalVpool());

        // If the current and new Target Journal varray/vpool are not set, default them to known values.
        if (currentTargetJournalVarrayId.equals(NullColumnValueGetter.getNullStr())) {
            currentTargetJournalVarrayId = targetVarrayId;
        }

        if (currentTargetJournalVpoolId.equals(NullColumnValueGetter.getNullStr())) {
            currentTargetJournalVpoolId = currentVpool.getId().toString();
        }

        VirtualPool currentTargetJournalVpool = dbClient.queryObject(VirtualPool.class, URI.create(currentTargetJournalVpoolId));

        if (newTargetJournalVpoolId.equals(NullColumnValueGetter.getNullStr())) {
            newTargetJournalVpoolId = newVpool.getId().toString();
        }

        VirtualPool newTargetJournalVpool = dbClient.queryObject(VirtualPool.class, URI.create(newTargetJournalVpoolId));

        if (newTargetJournalVarrayId.equals(NullColumnValueGetter.getNullStr())) {
            if (newTargetJournalVpool.getVirtualArrays() != null
                    && newTargetJournalVpool.getVirtualArrays().contains(targetVarrayId)) {
                newTargetJournalVarrayId = targetVarrayId;
            }
        }

        // Only consider the Target Journal migration if the varrays are the same and the vpools
        // are different and both specify HA.
        if (!currentTargetJournalVpoolId.equals(newTargetJournalVpoolId)) {
            if (currentTargetJournalVarrayId.equals(newTargetJournalVarrayId)
                    && VirtualPool.vPoolSpecifiesHighAvailability(currentTargetJournalVpool)
                    && VirtualPool.vPoolSpecifiesHighAvailability(newTargetJournalVpool)) {
                // Add Target Journal for potential migration
                potentialMigrations.add(new RPVPlexMigration(Volume.PersonalityTypes.METADATA, Volume.PersonalityTypes.TARGET,
                        URI.create(currentTargetJournalVarrayId), currentTargetJournalVpool, newTargetJournalVpool));
            } else {
                // If the Target Journal vpools are different and both of the Target Journal vpools do not specify HA,
                // then exclude this new vpool for RP+VPLEX migration.
                notSuppReasonBuff.append("Not valid for migration due to changes in RP Target Journal virtual pool / virtual array.");
                invalidMigration = true;
            }
        } else {
            if (!currentTargetJournalVarrayId.equals(newTargetJournalVarrayId)) {
                notSuppReasonBuff.append("Not valid for migration due to changes in RP Target Journal virtual array.");
                invalidMigration = true;
            }
        }

        return invalidMigration;
    }
}
