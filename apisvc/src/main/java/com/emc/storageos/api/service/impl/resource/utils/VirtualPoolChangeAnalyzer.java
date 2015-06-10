/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

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
	private static final String HOST_IO_LIMIT_BANDWIDTH="hostIOLimitBandwidth";
    private static final String HOST_IO_LIMIT_IOPS="hostIOLimitIOPs";
    private static final String AUTO_CROSS_CONNECT_EXPORT="autoCrossConnectExport";
    private static final String RP_RPO_VALUE="rpRpoValue";

    private static final String[] INCLUDED_AUTO_TIERING_POLICY_LIMITS_CHANGE = new String[] { AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH,HOST_IO_LIMIT_IOPS };
    private static final String[] EXCLUDED_AUTO_TIERING_POLICY_LIMITS_CHANGE = new String[] {
            AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH,HOST_IO_LIMIT_IOPS,ARRAY_INFO,
            UNIQUE_AUTO_TIERING_POLICY_NAMES, ASSIGNED_STORAGE_POOLS,
            USE_MATCHED_POOLS, THIN_VOLUME_PRE_ALLOCATION_PERCENTAGE };

	private static final String[] generallyExcluded = { ACLS, DESCRIPTION,
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
     */
    public static VirtualPoolChangeOperationEnum getSupportedVPlexVolumeVirtualPoolChangeOperation(Volume volume,
        VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient,
        StringBuffer notSuppReasonBuff) {
        s_logger.info("Running getSupportedVPlexVolumeVirtualPoolChangeOperation...");
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
        if (VirtualPool.vPoolSpecifiesMirrors(currentVpool, dbClient)){
        	include = addElementToArray(include, MIRROR_VPOOL);
        }
        
        if (!analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {
            notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
            int pCount = 0;
            for (String pName : include) {
                notSuppReasonBuff.append(pName);
                if (++pCount < include.length) {
                    notSuppReasonBuff.append(", ");
                }
            }
            return null;
        }

        if (VirtualPool.vPoolSpecifiesMirrors(newVpool, dbClient) &&
        		isSupportedAddMirrorsVirtualPoolChange(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff)) {
        	return VirtualPoolChangeOperationEnum.ADD_MIRRORS;
        }

        return vplexCommonChecks(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff, include);
    }
    
    private static VirtualPoolChangeOperationEnum vplexCommonChecks(Volume volume,
            VirtualPool currentVpool, VirtualPool newVpool, DbClient dbClient,
            StringBuffer notSuppReasonBuff, String[] include) {
        s_logger.info("Running vplexCommonChecks...");
        boolean isRPVPlex = VirtualPool.vPoolSpecifiesHighAvailability(currentVpool) && VirtualPool.vPoolSpecifiesRPVPlex(newVpool);
        
        // If the volume is in a CG and the target vpool does not specify multi
        // volume consistency, then the vpool change is not permitted.
        if ((!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) &&
            (!newVpool.getMultivolumeConsistency())) {
            notSuppReasonBuff.append("The volume is in a consistency group but the target virtual pool does not specify multi-volume consistency");
            return null;
        }
        
        // If highAvailability changed, we do support upgrade from
        // vplex_local to vplex_distributed, with no other significant
        // attributes changed. Otherwise the change is prohibited.
        include = new String[] { HIGH_AVAILABILITY } ;
        if (!analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {            
            // An ingested local VPLEX volume can't be converted to distributed.
            // The backend volumes would need to be migrated first.
            if (volume.isIngestedVolume(dbClient)) {
                notSuppReasonBuff.append("The high availability of an ingested VPLEX volume cannot be modified.");
                return null;
            }
            
            boolean isCovertToDistributed = isVPlexConvertToDistributed(currentVpool, newVpool, notSuppReasonBuff);
            if (isCovertToDistributed) {
                URI haVarrayURI = getHaVarrayURI(newVpool, dbClient);
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
                        .append("The volume is in a consistency group, and all volumes "
                            + " in a consistency group must be of the same type.");
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
            newHaVpool = getNewHaVpool(currentVpool, newVpool, dbClient);
            if (!currentHaVpool.getId().toString().equals(newHaVpool.getId().toString())) {
                s_logger.info("HA varray vpools are different");

                if (isRPVPlex && newVpoolDoesNotSpecifyHaVpool) {
                    // This means that new RPVPlex vpool does not specify
                    // an HA vpool, so we use the new vpool as the HA vpool, so
                    // specifying protection is OK.
                    include = new String[] { TYPE, VARRAYS,
                            REF_VPOOL, MIRROR_VPOOL,
                            FAST_EXPANSION, ACLS, INACTIVE, NUM_PATHS };
                }
                else {                                        
                    include = new String[] { TYPE, VARRAYS,
                            PROTECTION_VARRAY_SETTINGS, REF_VPOOL, MIRROR_VPOOL,
                                FAST_EXPANSION, ACLS, INACTIVE, NUM_PATHS };
                }
                
                if (!analyzeChanges(currentHaVpool, newHaVpool, include, null, null).isEmpty()) {
                    notSuppReasonBuff.append("Changes in the following high availability virtual pool properties are not permitted: ");
                    int pCount = 0;
                    for (String pName : include) {
                        notSuppReasonBuff.append(pName);
                        if (++pCount < include.length) {
                            notSuppReasonBuff.append(", ");
                        }
                    }
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
                + ", " + ASSIGNED_STORAGE_POOLS);
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
        URI haVarrayURI = getHaVarrayURI(currentVpool, dbClient);
        
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
     * @param dbClient A reference to a DB client.
     * 
     * @return The HA virtual array specified by the passed virtual pool
     * or null if the virtual pool does not specify VPLEX distributed HA.
     */
    public static URI getHaVarrayURI(VirtualPool vpool, DbClient dbClient) {
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
        s_logger.info("Running vpoolChangeRequiresMigration...");
        // Verify they are different Vpool.
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
                                        DRIVE_TYPE, AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_IOPS, HOST_IO_LIMIT_BANDWIDTH,
                                        IS_THIN_VOLUME_PRE_ALLOCATION_ENABLED, 
                                        ASSIGNED_STORAGE_POOLS };
       
        return !analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty();
    }

    /**
     * Returns true iff the difference between vpool1 and vpool2 is that vpool2 is
     * requesting highAvailability.
     * 
     * @param vpool1 Reference to Vpool to compare.
     * @param vpool2 Reference to Vpool to compare.
     * @param notImportReasonBuff [OUT] Specifies reason why its not an import.
     * 
     * @return true if the Vpool difference indicates vpool2 adds VPlex high
     *         availability, false otherwise.
     */
    public static boolean isVPlexImport(VirtualPool vpool1, VirtualPool vpool2,
        StringBuffer notImportReasonBuff) {
        s_logger.info("Running isVPlexImport...");
        String[] excluded = new String[] { ACLS, ASSIGNED_STORAGE_POOLS, DESCRIPTION,
            HA_VARRAY_VPOOL_MAP, LABEL, MATCHED_POOLS, INVALID_MATCHED_POOLS, NUM_PATHS,
            STATUS, TAGS, CREATION_TIME, THIN_VOLUME_PRE_ALLOCATION_PERCENTAGE, 
            NON_DISRUPTIVE_EXPANSION, AUTO_CROSS_CONNECT_EXPORT };
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
                    //            it's fine if the target vpool has a value of 0
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
                + "other than high availability: " + changesOutput.toString().trim());
        }
        
        return false;
    }
    
    /**
     * Returns true iff the only difference is converting from a vplex_local to
     * vplex_distributed.
     * 
     * @param vpool1 A reference to a Vpool
     * @param vpool2 A reference to a Vpool
     * @param notSuppReasonBuff [OUT] Specifies the reason a Vpool change is not
     *        supported between the two Vpool.
     * 
     * @return true if the Vpool difference specifies only a change in the high
     *         availability type from local to distributed, false otherwise.
     */
    public static boolean isVPlexConvertToDistributed(VirtualPool vpool1, VirtualPool vpool2,
        StringBuffer notSuppReasonBuff) {
        s_logger.info("Running isVPlexConvertToDistributed...");
        String[] excluded = new String[] { ASSIGNED_STORAGE_POOLS, DESCRIPTION,
            HA_VARRAY_VPOOL_MAP, LABEL, MATCHED_POOLS, INVALID_MATCHED_POOLS, NUM_PATHS,
            STATUS, TAGS, CREATION_TIME, NON_DISRUPTIVE_EXPANSION };
        Map<String, Change> changes = analyzeChanges(vpool1, vpool2, null, excluded, null);
        
        // changes.size() needs to be greater than 1, because at least
        // HIGH_AVAILABILITY change is expected
        if (changes.size() > 1) {
            notSuppReasonBuff.append("Changes in addition to a change in the "
                + "virtual pool high availability property are not permitted: ");
            for (Entry<String, Change> entry : changes.entrySet()) {
                // ignore HIGH_AVAILABILITY change because it is expected here
                if ( entry.getKey().equals(HIGH_AVAILABILITY)) {
                    continue;
                }
                notSuppReasonBuff.append(entry.getKey()+"- "+entry.getValue()+ " ");
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
            DRIVE_TYPE, AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_IOPS, HOST_IO_LIMIT_BANDWIDTH, MATCHED_POOLS, INVALID_MATCHED_POOLS,
            ASSIGNED_STORAGE_POOLS, LABEL, DESCRIPTION, STATUS, TAGS,
            CREATION_TIME, NON_DISRUPTIVE_EXPANSION };

        
        if (!VirtualPoolChangeAnalyzer.analyzeChanges(srcVpool, tgtVpool, null, exclude, null)
                            .isEmpty()) {
            throw APIException.badRequests.vPoolChangeNotValid(srcVpool.getId(),tgtVpool.getId());
        }
    }

    /**
     * Determines if the volume qualifies for RP protection.  (and if not, why not)
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     */
    public static boolean isSupportedRPVolumeVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool, 
                                                                DbClient dbClient, StringBuffer notSuppReasonBuff) {        
        s_logger.info("Running isSupportedRPVolumeVirtualPoolChange...");
        
        // Make sure the Vpool are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }
        
        // Not supported yet, will be in the future
        if (VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            notSuppReasonBuff.append("Add RecoverPoint Protection not supported for MetroPoint at this time.");
            return false;
        }

        // Throw an exception if any of the following properties are different
        // between the current and new Vpool. The check for continuous and protection-
        // based settings change is below. 
        String[] include = new String[] { TYPE, VARRAYS,
                                            REF_VPOOL, MIRROR_VPOOL,
                                            FAST_EXPANSION, ACLS, 
                                            INACTIVE };
        if (!analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {
            notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
            int pCount = 0;
            for (String pName : include) {
                notSuppReasonBuff.append(pName);
                if (++pCount < include.length) {
                    notSuppReasonBuff.append(", ");
                }
            }
            return false;
        }
        
        // If protection changed, we do support upgrade from
        // non-protected to protected.  Make sure that change is there.
        include = new String[] { PROTECTION_VARRAY_SETTINGS } ;
        if (analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {
            notSuppReasonBuff.append("Changes in the following virtual pool properties are required: ");
            int pCount = 0;
            for (String pName : include) {
                notSuppReasonBuff.append(pName);
                if (++pCount < include.length) {
                    notSuppReasonBuff.append(", ");
                }
            }
            return false;
        }
                       
        // If the volume is already protected by RP and we're not upgrading to MetroPoint, then we don't need to protect it again.  
        if (volume.checkForRp() && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            notSuppReasonBuff.append("Volume already has protection, change vpool operations currently not supported.");
            return false;
        }
        
        // Check for RP+VPLEX case where we would protect a VPLEX Virtual Volume as long
        // as the new vpool specifies HA and Protection both.
        if (VirtualPool.vPoolSpecifiesHighAvailability(currentVpool) 
                && VirtualPool.vPoolSpecifiesRPVPlex(newVpool)
                && !VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            VirtualPoolChangeOperationEnum op = vplexCommonChecks(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff, include);
            
            if (op == null || !op.equals(VirtualPoolChangeOperationEnum.RP_PROTECTED)) {
                return false;
            }                
        }
        
        // Not supported yet, will be in the future
        if (VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            return false;
        }

        return true;
    }

    /**
     * Determines if the volume qualifies for SRDF protection.  (and if not, why not)
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     */
    public static boolean isSupportedSRDFVolumeVirtualPoolChange(Volume volume, VirtualPool currentVpool, VirtualPool newVpool, 
                                                                   DbClient dbClient, StringBuffer notSuppReasonBuff) {
        s_logger.info("Running isSupportedSRDFVolumeVirtualPoolChange...");
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
                AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH, HOST_IO_LIMIT_IOPS };
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, include, null, null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
            for (String key : changes.keySet()) {
                notSuppReasonBuff.append(key + " ");
            }
            s_logger.info("Virtual Pool change not supported: {}", notSuppReasonBuff.toString());
            return false;
        }
        
        // TODO: SYSTEM_TYPE can change, but only from ANY to VMAX, but not VMAX to VNX or anything to VNX CTRL-276
        
        // If protection changed, we do support upgrade from
        // non-protected to protected.  Make sure that change is there.
        include = new String[] {REMOTECOPY_VARRAY_SETTINGS} ;
        if (analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {
            notSuppReasonBuff.append("Changes in the following virtual pool properties are required: ");
            int pCount = 0;
            for (String pName : include) {
                notSuppReasonBuff.append(pName);
                if (++pCount < include.length) {
                    notSuppReasonBuff.append(", ");
                }
            }
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
        s_logger.info("Running isSupportedAddMirrorsVirtualPoolChange...");
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
                FAST_EXPANSION, ACLS, INACTIVE, DRIVE_TYPE, ARRAY_INFO, PROVISIONING_TYPE, PROTOCOLS};
        // Throw an exception if any of the following properties values from current vpool 
        // are not contained in new virtual pool
        String[] contain = new String[] {MATCHED_POOLS, ASSIGNED_STORAGE_POOLS };
        if (!analyzeChanges(currentVpool, newVpool, include, null, contain).isEmpty()) {
            notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
            int pCount = 0;
            for (String pName : include) {
                notSuppReasonBuff.append(pName);
                if (++pCount < include.length) {
                    notSuppReasonBuff.append(", ");
                }
            }
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
    	boolean supported = false;
    	
    	// This if section is basically checking source side vpool to make sure its good to be changed with newVpool
    	if(newVpool.getMaxNativeContinuousCopies() > 0 && newVpool.getMirrorVirtualPool() !=null){
    		String[] include = new String[] { TYPE, VARRAYS, REF_VPOOL, HIGH_AVAILABILITY, PROTECTION_VARRAY_SETTINGS,
                    FAST_EXPANSION, ACLS, INACTIVE, DRIVE_TYPE, ARRAY_INFO, PROVISIONING_TYPE, PROTOCOLS};
            // Throw an exception if any of the following properties values from current vpool 
            // are not contained in new virtual pool
            String[] contain = new String[] {MATCHED_POOLS, ASSIGNED_STORAGE_POOLS };
            
            // analyzeChanges will ensure that elements in the include list donot change between
            // currentVpool and newVpool and elements in the contain list must be present in the
            // newVpool plus newVpool can contain extra other storage pools in those two properties.    
            
            if (!analyzeChanges(currentVpool, newVpool, include, null, contain).isEmpty()) {
                notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
                int pCount = 0;
                for (String pName : include) {
                    notSuppReasonBuff.append(pName);
                    if (++pCount < include.length) {
                        notSuppReasonBuff.append(", ");
                    }
                }
                return false;
            }
            supported = true;
    	}
    	
    	// This section is checking HA side vpool to make sure its good to be changed with the newVpool Ha Vpool
    	VirtualPool currentHaVpool = VirtualPool.getHAVPool(currentVpool, dbClient);
    	if(currentHaVpool == null) currentHaVpool = currentVpool;
    	VirtualPool newHaVpool = VirtualPool.getHAVPool(newVpool, dbClient);
    	if(currentHaVpool != null && newHaVpool != null){
    		if(newHaVpool.getMaxNativeContinuousCopies() > 0 && newHaVpool.getMirrorVirtualPool() !=null){
    			String[] include = new String[] { TYPE, VARRAYS, REF_VPOOL, PROTECTION_VARRAY_SETTINGS,
    					FAST_EXPANSION, ACLS, INACTIVE, DRIVE_TYPE, ARRAY_INFO, PROVISIONING_TYPE, PROTOCOLS};
    			// Throw an exception if any of the following properties values from current vpool 
    			// are not contained in new virtual pool
    			String[] contain = new String[] {MATCHED_POOLS, ASSIGNED_STORAGE_POOLS };
    			
    			// If HaVpool for the newVpool is enabled for continuous copies then we need 
    			// to ensure that some of the properties as mentioned in the include list do 
    			// not change for the ha vpool and values for the properties in contain list
    			// are present in the newVpool haVpool plus it can contain extra values too.
    			// Note we don't need to check for HIGH_AVAILABILITY property in case of HA Vpool.
    			// Before user could have ha Vpool with no HA set and now can move to HA set with
    			// continuous copies enabled.
    			
    			if (!analyzeChanges(currentHaVpool, newHaVpool, include, null, contain).isEmpty()) {
    				notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
    				int pCount = 0;
    				for (String pName : include) {
    					notSuppReasonBuff.append(pName);
    					if (++pCount < include.length) {
    						notSuppReasonBuff.append(", ");
    					}
    				}
    				return false;
    			}
    			supported = true;
    		}
    	}
    	
    	return supported;
    }
    
    /**
     * Checks to see if only the Export Path Params have changed.
     * @param volume
     * @param currentVpool
     * @param newVpool
     * @param dbClient
     * @param notSuppReasonBuff
     * @return
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
        excluded.addAll(Arrays.asList(generallyExcluded));
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("These target pool differences are invalid: ");
            for (String key: changes.keySet()) {
                s_logger.info("Unexpected PathParam Vpool attribute change: " + key);
                notSuppReasonBuff.append(key + " ");
            }
            s_logger.info("Virtual Pool change not supported {}", notSuppReasonBuff.toString());
            s_logger.info(String.format("Parameters other than %s were changed",
                                         (Object[]) exclude));
            return false;
        }
        return true;
    }

    /**
     * Check to see if the current VirtualPool is the same as the requested VirtualPool.
     *
     * @param current
     * @param requested
     * @param notSuppReasonBuff
     * @return
     */
    public static boolean isSameVirtualPool(VirtualPool current, VirtualPool requested, StringBuffer notSuppReasonBuff) {         
        if (current.getId().equals(requested.getId())) {
            String msg = "The target virtual pool is the same as current virtual pool.";
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
         *          - System type in both vPools should remain same.
         * Case 2 : from NONE (no Auto-tiering policy in current vPool) to some Auto-tiering policy (new vPool).
         *          - System type should remain same
         *          (take source system type from either volume or current vPool).
         * Case 3 : from some Auto-tiering policy (current vPool) to NONE (no Auto-tiering policy in new vPool).
         *          - no need to check whether system type has changed.
         * Case 4 : from host io limit bandwidth and/or iops fron current vpool are different from new vPool.          
         *
         */

        // Make sure the VirtualPool's are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }

        // First, check that AUTO_TIER_POLICY_NAME changed.
        String[] included = INCLUDED_AUTO_TIERING_POLICY_LIMITS_CHANGE;
        if (analyzeChanges(currentVpool, newVpool, included, null, null).isEmpty()) {
            notSuppReasonBuff.append("Did not change AUTO_TIER_POLICY_NAME, HOST_IO_LIMIT_BANDWIDTH, nor HOST_IO_LIMIT_IOPS");
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
            if (currentSystemType == null) { currentSystemType = new StringSet();}
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
        if (!checkTargetVpoolHasVolumePool(volume, newVpool)) {
            String msg = "Auto-tiering Policy change: Target vPool does not have Volume's Storage Pool in its matched/assigned pools list.";
            notSuppReasonBuff.append(msg);
            s_logger.info("Virtual Pool change not supported: {}", notSuppReasonBuff.toString());
            return false;
        }

        // Last, check that nothing other than the excluded attributes changed.
        List<String> excluded = new ArrayList<String>();
        String[] exclude = EXCLUDED_AUTO_TIERING_POLICY_LIMITS_CHANGE;
        excluded.addAll(Arrays.asList(exclude));
        excluded.addAll(Arrays.asList(generallyExcluded));
        Map<String, Change> changes = analyzeChanges(currentVpool, newVpool, null, excluded.toArray(exclude), null);
        if (!changes.isEmpty()) {
            notSuppReasonBuff.append("These target pool differences are invalid: ");
            for (String key: changes.keySet()) {
                s_logger.info("Unexpected Auto-tiering Policy vPool attribute change: " + key);
                notSuppReasonBuff.append(key + " ");
            }
            s_logger.info("Virtual Pool change not supported {}", notSuppReasonBuff.toString());
            s_logger.info(String.format("Parameters other than %s were changed",
                                         (Object[]) exclude));
            return false;
        }
        return true;
    }
    
    /**
     * Check that target vPool has volume's storage pool in its matched pools list.
     * If target vPool has manual pool selection enabled, then volume's pool should be in assigned pools list.
     */
    private static boolean checkTargetVpoolHasVolumePool(Volume volume,
            VirtualPool newVpool) {
        boolean vPoolHasVolumePool = true;
        if (null != volume.getPool()) {
            if (newVpool.getUseMatchedPools()) {
                if (newVpool.getMatchedStoragePools() == null ||
                        !newVpool.getMatchedStoragePools().contains(volume.getPool().toString())) {
                    vPoolHasVolumePool = false;
                }
            } else {
                if (newVpool.getAssignedStoragePools() == null ||
                        !newVpool.getAssignedStoragePools().contains(volume.getPool().toString())) {
                    vPoolHasVolumePool = false;
                }
            }
        }
        return vPoolHasVolumePool;
    }

    /**
     * Determines if the VPLEX volume qualifies for RP protection.  (and if not, why not)
     * 
     * @param volume
     * @param currentVpool
     * @param newVpool
     * @param dbClient
     * @param notSuppReasonBuff
     * @return
     */
    public static boolean isSupportedRPVPlexVolumeVirtualPoolChange(
            Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            DbClient dbClient, StringBuffer notSuppReasonBuff) {       
        return isSupportedRPVolumeVirtualPoolChange(volume, currentVpool, newVpool, dbClient, notSuppReasonBuff);
    }
    
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
    private static String[] addElementToArray(String[] array, String element){
    	Arrays.copyOf(array, array.length + 1);
    	array [array.length-1] = element;
    	return array;
    }
    
    /**
     * Determines if the volume qualifies for RP protection.  (and if not, why not)
     * 
     * @param volume A reference to the volume.
     * @param currentVpool A reference to the current volume Vpool.
     * @param newVpool The desired new Vpool.
     * @param dbClient A reference to a DB client.
     */
    public static boolean isSupportedRPChangeProtectionVirtualPoolChange(Volume volume,VirtualPool currentVpool, VirtualPool newVpool, 
                                                                                DbClient dbClient, StringBuffer notSuppReasonBuff) {    
        s_logger.info(String.format("Checking if RP change protection operation is supported between vpool [%s] and vpool [%s]...", 
                currentVpool.getLabel(), newVpool.getLabel()));
        
        // Make sure the Vpool are not the same instance.
        if (isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            return false;
        }
        
        if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            // RP+VPLEX upgrade to MetroPoint
            if (!volume.getAssociatedVolumes().isEmpty()
                    && volume.getAssociatedVolumes().size() > 1) {
                // RP+VPLEX Distributed to MetroPoint
                // Supported
                
                // Throw an exception if any of the following properties are different
                // between the current and new Vpool.
                String[] include = new String[] { TYPE, VARRAYS,
                                                    REF_VPOOL, MIRROR_VPOOL,
                                                    HIGH_AVAILABILITY,
                                                    FAST_EXPANSION, ACLS, 
                                                    INACTIVE };
                if (!analyzeChanges(currentVpool, newVpool, include, null, null).isEmpty()) {
                    notSuppReasonBuff.append("Changes in the following virtual pool properties are not permitted: ");
                    int pCount = 0;
                    for (String pName : include) {
                        notSuppReasonBuff.append(pName);
                        if (++pCount < include.length) {
                            notSuppReasonBuff.append(", ");
                        }
                    }
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
                }
                else {
                    // Need to make sure that the new vpool has the same target varray/vpool
                    // defined to make the transition to MetroPoint seemless.
                    boolean supportedVpool = false;
                    for (Map.Entry<String, String> entry : newVpool.getProtectionVarraySettings().entrySet()) {
                        // Make sure they both use the same target varray
                        if (currentVpool.getProtectionVarraySettings().containsKey(entry.getKey())) {
                            // Now make sure they both use the same target vpool, this is pretty
                            // restrictive but our code path has to be precise for now.
                            String newSettingsId = entry.getValue();
                            String currentSettingsId = currentVpool.getProtectionVarraySettings().get(entry.getKey());
                               
                            // Grab the current protection varray settings
                            VpoolProtectionVarraySettings currentProtectionVarraySetting = 
                                    dbClient.queryObject(VpoolProtectionVarraySettings.class, 
                                            URI.create(currentSettingsId));
                            
                            // Grab the new protection varray settings
                            VpoolProtectionVarraySettings newProtectionVarraySetting = 
                                    dbClient.queryObject(VpoolProtectionVarraySettings.class, 
                                            URI.create(newSettingsId));
                            
                            // Make sure the vpools are the same
                            if (currentProtectionVarraySetting.getVirtualPool().equals(
                                    newProtectionVarraySetting.getVirtualPool())) {
                                supportedVpool = true;
                                break;
                            }
                        }                        
                    }
                    
                    if (!supportedVpool) {
                        notSuppReasonBuff.append("Varray and Vpools of source and new vpool do not match for targets.");
                        return false;
                    }
                }
            }
            else {
                // RP+VPLEX Local to MetroPoint
                // Unsupported for now
                notSuppReasonBuff.append("RP+VPLEX Local to MetroPoint change vpool unsupported for now.");
                return false;
            }
        }
        else if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                    && VirtualPool.vPoolSpecifiesRPVPlex(newVpool)) {
            // RP+VPLEX change to new RP+VPLEX vpool
            // Unsupported for now
            notSuppReasonBuff.append("RP+VPLEX to RP+VPLEX change vpool unsupported for now.");
            return false;
        }
        else if (VirtualPool.vPoolSpecifiesMetroPoint(currentVpool)
                    && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            // MetroPoint change to new MetroPoint vpool
            // Unsupported for now
            notSuppReasonBuff.append("MetroPoint to MetroPoint change vpool unsupported for now.");
            return false;
        }
        else {
            // Unsupported
            notSuppReasonBuff.append("Unsupported operation.");
            return false;
        }
        
        s_logger.info("RP change protection operation is supported.");

        return true;
    }
}
