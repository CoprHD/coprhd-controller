/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.AutoTieringPolicyValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.DriveTypeValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.ExpansionValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.HighAvailabilityValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.HostIOLimitValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.ProtectionValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.ProvisioningTypeValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.RaidLevelValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.RemoteMirrorProtectionValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.SystemTypeValidator;
import com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators.ThinVolumePreAllocationValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ObjectVirtualPoolParam;
import com.emc.storageos.model.vpool.ObjectVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.RaidLevelChanges;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.collect.Sets;

/**
 * VirtualPool utilities to check and select VirtualPool related parameters
 */
public class VirtualPoolUtil {
    private static final Logger _log = LoggerFactory.getLogger(VirtualPoolUtil.class);

    private static VirtualPoolValidator autoTieringPolicyValidator = null;
    // private static VirtualPoolValidator blockNameValidator = null;
    // private static VirtualPoolValidator blockDescriptionValidator = null;
    private static VirtualPoolValidator blockDriveTypeValidator = null;
    private static VirtualPoolValidator blockSystemTypeValidator = null;
    private static VirtualPoolValidator blockProvisioningValidator = null;
    private static VirtualPoolValidator blockRaidLevelValidator = null;
    private static VirtualPoolValidator blockExpansionValidator = null;
    private static VirtualPoolValidator highAvailabilityValidator = null;
    private static VirtualPoolValidator blockProtectionValidator = null;
    private static VirtualPoolValidator blockRemoteProtectionValidator = null;
    private static VirtualPoolValidator blockThinVolumePreAllocationValidator = null;
    private static VirtualPoolValidator blockHostLimitValidator = null;

    // private static VirtualPoolValidator fileNameValidator = null;
    // private static VirtualPoolValidator fileDescriptionValidator = null;
    private static VirtualPoolValidator fileProvisioningValidator = null;
    private static VirtualPoolValidator fileSystemTypeValidator = null;
    
    private static VirtualPoolValidator objectTypeValidator = null;

    static {
        // blockNameValidator = new NameValidator();
        // blockDescriptionValidator = new DescriptionValidator();
        blockSystemTypeValidator = new SystemTypeValidator();
        blockProvisioningValidator = new ProvisioningTypeValidator();
        autoTieringPolicyValidator = new AutoTieringPolicyValidator();
        blockDriveTypeValidator = new DriveTypeValidator();
        blockRaidLevelValidator = new RaidLevelValidator();
        blockExpansionValidator = new ExpansionValidator();
        highAvailabilityValidator = new HighAvailabilityValidator();
        blockProtectionValidator = new ProtectionValidator();
        blockThinVolumePreAllocationValidator = new ThinVolumePreAllocationValidator();
        blockRemoteProtectionValidator = new RemoteMirrorProtectionValidator();
        blockHostLimitValidator = new HostIOLimitValidator();

        // blockNameValidator.setNextValidator(blockDescriptionValidator);
        // blockDescriptionValidator.setNextValidator(blockSystemTypeValidator);
        blockSystemTypeValidator.setNextValidator(blockProvisioningValidator);
        blockProvisioningValidator.setNextValidator(autoTieringPolicyValidator);
        autoTieringPolicyValidator.setNextValidator(blockDriveTypeValidator);
        blockDriveTypeValidator.setNextValidator(blockExpansionValidator);
        blockExpansionValidator.setNextValidator(highAvailabilityValidator);
        highAvailabilityValidator.setNextValidator(blockRaidLevelValidator);
        blockRaidLevelValidator.setNextValidator(blockProtectionValidator);
        blockProtectionValidator.setNextValidator(blockThinVolumePreAllocationValidator);
        blockThinVolumePreAllocationValidator.setNextValidator(blockRemoteProtectionValidator);
        blockRemoteProtectionValidator.setNextValidator(blockHostLimitValidator);
    }

    /**
     * Return the FileValidatorChain entry point.
     * 
     * @return
     */
    static {
        // fileNameValidator = new NameValidator();
        // fileDescriptionValidator = new DescriptionValidator();
        fileSystemTypeValidator = new SystemTypeValidator();
        fileProvisioningValidator = new ProvisioningTypeValidator();

        // fileNameValidator.setNextValidator(fileDescriptionValidator);
        // fileDescriptionValidator.setNextValidator(fileSystemTypeValidator);
        fileSystemTypeValidator.setNextValidator(fileProvisioningValidator);
    }
    
    /**
     * Object validator
     */
    static {
        objectTypeValidator = new SystemTypeValidator();
    }

    /**
     * Check if the VirtualPool contains the all the passed protocols.
     * 
     * @param cos A reference to a VirtualPool.
     * @param protocols The protocols to verify.
     * @return true if the vpool contains the passed protocols, false otherwise.
     */
    public static boolean checkVirtualPoolProtocols(VirtualPool cos, HashSet<String> protocols) {
        return checkVirtualPoolProtocols(cos, protocols, true);
    }

    public static boolean checkRaidLevelsChanged(
            StringSetMap arrayInfo, RaidLevelChanges raidLevelChanges) {
        /**
         * If arrayInfo is null (or) raidLevel key is null
         * then
         * if changedRaidLevels is null, no modification, return false
         * else
         * if the changedRaidLevels or changedRaidLevels.add is !=null, then modified true
         */
        if (null == arrayInfo
                || null == arrayInfo.get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL)) {
            if (null != raidLevelChanges) {
                return true;
            }
        }
        /**
         * ArrayInfo.getRaidRevel is not null, then check
         * if
         * raidLevel Changes is not null && raidLevel.Changes.add is !null
         * then if raid Levels Added contains entries after doing RemoveAll, then it means, we are trying to
         * remove
         * existing items from list, hence return true, else false
         * 
         **/
        else if ((null != raidLevelChanges) && (null != raidLevelChanges.getAdd())) {
            StringSet expectedRaidLevels = arrayInfo
                    .get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL);
            Set<String> raidLevelsAdded = raidLevelChanges.getAdd().getRaidLevels();
            raidLevelsAdded.removeAll(expectedRaidLevels);
            // if raid Levels Added contains entries, then it means, we are trying to add new items into list,
            // hence return true.
            if (!raidLevelsAdded.isEmpty()) {
                return true;
            }
        }
        /**
         * ArrayInfo.getRaidRevel is not null, then check
         * if
         * raidLevel Changes is not null && raidLevel.Changes.add is !null
         * then if raid Levels Removed contains entries after doing retainAll, then it means, we are trying to
         * add new items into list,
         * hence return true, else false
         * 
         **/
        else if ((null != raidLevelChanges) && (null != raidLevelChanges.getRemove())) {
            StringSet expectedRaidLevels = arrayInfo
                    .get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL);
            Set<String> raidLevelsRemoved = raidLevelChanges.getRemove().getRaidLevels();
            raidLevelsRemoved.retainAll(expectedRaidLevels);
            // if raid Levels Removed contains entries, then it means, we are trying to add remove existing
            // items from list, hence return true.
            if (!raidLevelsRemoved.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkForVirtualPoolAttributeModification(String expected, String changed) {
        // Values in Expected is null & changed is !null, then modified true
        if (null == expected && null == changed) {
            return false;
        }
        if (null == expected && null != changed) {
            return true;
        }
        if (null != expected && null == changed) {
            return false;
        }
        // Values in Expected is != changed, then modified true
        if (!expected.equals(changed)) {
            return true;
        }
        return false;
    }

    public static boolean checkSystemTypeChanged(
            StringSetMap arrayInfo, String changedSystemType) {
        if (null == arrayInfo
                || null == arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            if (null != changedSystemType) {
                return true;
            }
        } else if (null != changedSystemType) {
            StringSet systemType = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (!systemType.contains(changedSystemType)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static void validateBlockVirtualPoolCreateParams(BlockVirtualPoolParam param,
            DbClient dbClient) throws DatabaseException {
        // Starts block VirtualPool validation chain using blockNameValidator.
        blockSystemTypeValidator.validateVirtualPoolCreateParam(param, dbClient);
    }

    @SuppressWarnings("unchecked")
    public static void validateBlockVirtualPoolUpdateParams(VirtualPool cos,
            BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) throws DatabaseException {
        // Starts block VirtualPool validation chain using blockNameValidator.
        blockSystemTypeValidator.validateVirtualPoolUpdateParam(cos, updateParam, dbClient);
    }

    @SuppressWarnings("unchecked")
    public static void validateFileVirtualPoolCreateParams(FileVirtualPoolParam param,
            DbClient dbClient) throws DatabaseException {
        // Starts file VirtualPool validation chain using fileNameValidator.
        fileSystemTypeValidator.validateVirtualPoolCreateParam(param, dbClient);
    }

    @SuppressWarnings("unchecked")
    public static void validateFileVirtualPoolUpdateParams(VirtualPool cos,
            FileVirtualPoolUpdateParam updateParam, DbClient dbClient) throws DatabaseException {
        // Starts file VirtualPool validation chain using fileNameValidator.
        fileSystemTypeValidator.validateVirtualPoolUpdateParam(cos, updateParam, dbClient);
    }

    @SuppressWarnings("unchecked")
    public static void validateObjectVirtualPoolCreateParams(ObjectVirtualPoolParam param,
            DbClient dbClient) throws DatabaseException {
        // Starts object VirtualPool validation chain using objectNameValidator.
        objectTypeValidator.validateVirtualPoolCreateParam(param, dbClient);
    }

    @SuppressWarnings("unchecked")
    public static void validateObjectVirtualPoolUpdateParams(VirtualPool cos,
            ObjectVirtualPoolUpdateParam updateParam, DbClient dbClient) throws DatabaseException {
        // Starts object VirtualPool validation chain using objectNameValidator.
        objectTypeValidator.validateVirtualPoolUpdateParam(cos, updateParam, dbClient);
    }
    
    public static boolean isAutoTieringPolicyValidForDeviceType(
            String autoTierPolicyName, String systemType, DbClient dbClient) {
        if (null == autoTierPolicyName || autoTierPolicyName.equalsIgnoreCase("NONE")) {
            return true;
        }
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getFASTPolicyByNameConstraint(autoTierPolicyName),
                result);
        while (result.iterator().hasNext()) {
            AutoTieringPolicy policy = dbClient.queryObject(AutoTieringPolicy.class, result
                    .iterator().next());
            if (policy == null) {
                continue;
            }
            if (systemType.equalsIgnoreCase(policy.getSystemType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the VirtualPool contains the passed protocols. If the matchAll parameter
     * is true, the call returns true only if the VirtualPool contains all the passed
     * protocols. Otherwise, the call returns true if the VirtualPool contains at least
     * one of the passed protocols.
     * 
     * @param cos
     *            A reference to a VirtualPool.
     * @param protocols
     *            The protocols to verify.
     * @param matchAll
     *            true, if vpool must contain all passed protocols.
     * 
     * @return true if the vpool contains the passed protocols, false otherwise.
     */
    public static boolean checkVirtualPoolProtocols(
            VirtualPool cos, HashSet<String> protocols, boolean matchAll) {
        if ((protocols != null) && !protocols.isEmpty()) {
            StringSet cosProtocols = cos.getProtocols();
            if ((matchAll) && (!cosProtocols.containsAll(protocols))) {
                _log.info("vpool does not support all requested protocol(s): {}", protocols);
                return false;
            } else if (!matchAll) {
                boolean hasMatch = false;
                Iterator<String> protocolIter = protocols.iterator();
                while (protocolIter.hasNext()) {
                    String protocol = protocolIter.next();
                    if (cosProtocols.contains(protocol)) {
                        _log.debug("vpool supports protocol {}", protocol);
                        hasMatch = true;
                        break;
                    }
                }
                return hasMatch;
            }
        }
        return true;
    }

    /**
     * Return the common protocols of vpool & pool if VirtualPool is defined with protocols.
     * Else return pool protocols.
     * 
     * @param cosProtocols : Protocols defined in VirtualPool.
     * @param poolProtocols : Protocols supported by Pool.
     * @return : matching protocols.
     */
    public static Set<String> getMatchingProtocols(StringSet cosProtocols, StringSet poolProtocols) {
        Set<String> protocols = new HashSet<String>();
        if (null != cosProtocols && !cosProtocols.isEmpty()) {
            protocols = Sets.intersection(cosProtocols, poolProtocols);
            if (protocols.isEmpty()) {
                protocols = getMatchingProtocolsForFilePool(cosProtocols, poolProtocols);
            }
        } else {
            protocols = poolProtocols;
        }
        return protocols;
    }

    /**
     * Check whether thinVolumePreAllocationPercentage attribute is changed or not.
     * 
     * @param thinVolumePreAllocationPercentage
     * @param thinVolumePreAllocationUpdateParam
     * @return
     */
    public static boolean checkThinVolumePreAllocationChanged(Integer thinVolumePreAllocationPercentage,
            Integer thinVolumePreAllocationUpdateParam) {
        boolean isModified = false;
        if (null != thinVolumePreAllocationUpdateParam) {
            if (null == thinVolumePreAllocationPercentage) {
                isModified = true;
            } else if (thinVolumePreAllocationPercentage != thinVolumePreAllocationUpdateParam) {
                isModified = true;
            }
        }
        if (null == thinVolumePreAllocationPercentage && null != thinVolumePreAllocationUpdateParam) {
            isModified = true;
        }
        return isModified;
    }

    /**
     * Check whether the protection attributes have changed.
     * 
     * @param from the source virutal pool without updates
     * @param to the updated virtual pool RP copies
     * @return true if the virtual pool has changed, false otherwise
     */
    public static boolean checkProtectionChanged(VirtualPool from, BlockVirtualPoolProtectionUpdateParam to) {

        // If the update object is null there are no updates
        if (to == null) {
            _log.info("No virtual pool protection changes have been made");
            return false;
        }

        // Check for mirrored protection updates
        if (to.getContinuousCopies() != null) {

            // Max native continuous copies can be changed any time -- so do not check it

            // Check if the mirror virtual pool has been updated
            if (to.getContinuousCopies().getVpool() != null
                    && from.getMirrorVirtualPool() != null
                    && !from.getMirrorVirtualPool().equals(String.valueOf(to.getContinuousCopies().getVpool()))) {
                _log.info("Protection mirror virtual pool is being updated on virtual pool {}", from.getId());
                return true;
            }
        }

        // Check for SRDF protection
        // this condition will be triggered only if there are volumes already associated with source vpool.
        if (to.getRemoteCopies() != null) {
            if ((null != to.getRemoteCopies().getAdd() && !to.getRemoteCopies().getAdd().isEmpty())
                    || (null != to.getRemoteCopies().getRemove() && !to.getRemoteCopies().getRemove().isEmpty())) {
                // SRDF protection Copies are being modified on a vpool with volumes already in place.
                // irrespective of whether source vpool has srdf protection,any changes to remote copies is not permitted on
                // virtual pools with provisioned volumes.
                _log.info("SRDF Protection cannot be added to a vpool with provisioned volumes ", from.getId());
                return true;
            }
        }

        // Check for any RP protection updates
        if (to.getRecoverPoint() != null) {
            // Has RP protection been removed?
            if (to.getRecoverPoint().getAdd().isEmpty() && to.getRecoverPoint().getRemove().isEmpty()
                    && to.getRecoverPoint().getSourcePolicy() == null) {
                if (VirtualPool.vPoolSpecifiesProtection(from)) {
                    _log.info("RP protection is being removed from virtual pool {}", from.getId());
                    return true;
                }
            }

            // Check for source journal size updates
            if (to.getRecoverPoint().getSourcePolicy() != null && to.getRecoverPoint().getSourcePolicy().getJournalSize() != null) {
                if (!to.getRecoverPoint().getSourcePolicy().getJournalSize().equals(from.getJournalSize())) {
                    _log.info("The source policy journal size is being updated on virtual pool {}", from.getId());
                    return true;
                }
            }

            // Check if the source policy has been removed
            if (to.getRecoverPoint().getSourcePolicy() != null && to.getRecoverPoint().getSourcePolicy().getJournalSize() == null) {
                if (from.getJournalSize() != null && !from.getJournalSize().equals(NullColumnValueGetter.getNullStr())) {
                    _log.info("The source policy is being removed from virtual pool {}", from.getId());
                    return true;
                }
            }

            // Check if there are RP protection copies being added
            if (to.getRecoverPoint().getAdd() != null && !to.getRecoverPoint().getAdd().isEmpty()) {
                _log.info("Adding/updating RP protection copies to virtual pool {}", from.getId());
                return true;
            }

            // Check if there are RP protection copies being removed
            if (to.getRecoverPoint().getRemove() != null && !to.getRecoverPoint().getRemove().isEmpty()) {
                _log.info("Removing RP protection copies from virtual pool {}", from.getId());
                return true;
            }
        }

        _log.info("No protection changes");
        return false;
    }

    /**
     * On an update of a block virtual pool, determines if the update request
     * modifies the high availability parameters for the virtual pool.
     * 
     * @param vPool A reference to the virtual pool being updated..
     * @param haParam The high availability param from the update request.
     * 
     * @return true if the high availability parameters for the virtual pool are
     *         being modified, false otherwise.
     */
    public static boolean checkHighAvailabilityChanged(VirtualPool vPool,
            VirtualPoolHighAvailabilityParam haParam) {

        // If the HA param is null, then no HA changes were requested.
        if (haParam == null) {
            _log.info("No HA changes");
            return false;
        }

        // Check if the virtual pool currently specify high availability.
        if (VirtualPool.vPoolSpecifiesHighAvailability(vPool)) {
            _log.info("Virtual pool specifies HA");
            // The virtual pool does specify HA, check to see if it is being
            // changed or removed.
            if ((haParam.getType() == null) && (haParam.getHaVirtualArrayVirtualPool() == null)) {
                _log.info("Removing HA");
                // The update request specifies an empty high availability
                // parameter, so the caller is attempting to remove high
                // availability from the virtual pool.
                return true;
            }

            // Check if the update request changes the high availability type.
            _log.info("HA type is {}", haParam.getType());
            if ((haParam.getType() != null)
                    && (!haParam.getType().equals(vPool.getHighAvailability()))) {
                _log.info("HA type changed");
                return true;
            }

            // If the HA vArray/vPool param is null, this is not being
            // updated. Otherwise, we need to check if these are being
            // changed or removed.
            if (haParam.getHaVirtualArrayVirtualPool() != null) {
                _log.info("Update specifies HA virtual array/pool changes.");
                // Get the current HA vArray and vPool for the vPool being
                // updated.
                String haVarray = null;
                String haVpool = null;
                String haVarrayConnectedToRp = vPool.getHaVarrayConnectedToRp();
                StringMap haVarrayVpoolMap = vPool.getHaVarrayVpoolMap();
                if (haVarrayVpoolMap != null && !haVarrayVpoolMap.isEmpty()) {
                    haVarray = haVarrayVpoolMap.keySet().iterator().next();
                    haVpool = haVarrayVpoolMap.get(haVarray);
                    if (haVpool.equals(NullColumnValueGetter.getNullURI().toString())) {
                        haVpool = null;
                    }
                }

                _log.info("Current vArray is {}", haVarray);
                _log.info("Current vPool is {}", haVpool);
                _log.info("HA vArray set as {}", haVarrayConnectedToRp);

                if ((haParam.getHaVirtualArrayVirtualPool().getVirtualArray() == null)
                        && (haParam.getHaVirtualArrayVirtualPool().getVirtualPool() == null)
                        && ((haVarray != null || haVpool != null))) {
                    _log.info("Removing HA vArray/vPool params");
                    // The update request specifies an empty HA vArray/vPool
                    // param, the request is to remove these. If the virtual
                    // pool currently has a value for one of these, then
                    // there is a change.
                    return true;
                }

                _log.info("Update vArray is {}", haParam.getHaVirtualArrayVirtualPool().getVirtualArray());
                if ((haParam.getHaVirtualArrayVirtualPool().getVirtualArray() != null)
                        && (!String.valueOf(haParam.getHaVirtualArrayVirtualPool().getVirtualArray()).equals(haVarray))) {
                    // If the update param specifies an HA vArray, see if
                    // it's the same as the current value for the virtual
                    // pool.
                    _log.info("Changing vArray");
                    return true;
                }

                _log.info("Update vPool is {}", haParam.getHaVirtualArrayVirtualPool().getVirtualPool());
                if ((haParam.getHaVirtualArrayVirtualPool().getVirtualPool() != null) &&
                        (!(String.valueOf(haParam.getHaVirtualArrayVirtualPool().getVirtualPool()).isEmpty() && haVpool == null)
                        && !String.valueOf(haParam.getHaVirtualArrayVirtualPool().getVirtualPool()).equals(haVpool))) {
                    // If the update param specifies an HA vPool, see if
                    // it's the same as the current value for the virtual
                    // pool. If the existing HA vpool is null and the updated HA vpool is an
                    // empty String, there are no changes.
                    _log.info("Changing vPool");
                    return true;
                }

                if ((haVarrayConnectedToRp != null && !haVarrayConnectedToRp.isEmpty()
                        && !haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite())
                        || ((haVarrayConnectedToRp == null || haVarrayConnectedToRp.isEmpty())
                        && haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite())) {
                    return true;
                }
            }
        } else if (haParam.getType() != null) {
            // The virtual pool does not currently specify high availability,
            // but the update request specifies a non-null high availability
            // type, so the request is attempting to add high availability
            // to the virtual pool.
            _log.info("Adding HA type {}", haParam.getType());
            return true;
        }
        return false;
    }

    /**
     * Returns the Thin Volume Preallocation size based on size of the volume &
     * its percentage defined in the CoS.
     * 
     * @param thinVolumePreAllocationPercentage
     * @param volumeSize
     * @return
     */
    public static Long getThinVolumePreAllocationSize(Integer thinVolumePreAllocationPercentage, Long volumeSize) {
        return (thinVolumePreAllocationPercentage * volumeSize) / 100;
    }

    /**
     * TODO : Need to move all these DB checks to DBSvc project check FileSystem
     * exists in DB
     * 
     * @param filesystemNativeGuid
     * @param dbClient
     * @return
     */
    public static boolean checkIfFileSystemExistsInDB(
            String filesystemNativeGuid, DbClient dbClient) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemNativeGUIdConstraint(filesystemNativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    /**
     * TODO : Need to move all these DB checks to DBSvc project
     * check Volume exists in DB
     * 
     * @param volumeNativeGuid
     * @param dbClient
     * @return
     */
    public static Volume checkIfVolumeExistsInDB(String volumeNativeGuid,
            DbClient dbClient) {
        List<Volume> activeVolumes = CustomQueryUtility.getActiveVolumeByNativeGuid(
                dbClient, volumeNativeGuid);
        Iterator<Volume> volumeItr = activeVolumes.iterator();
        return volumeItr.next();
    }

    /**
     * Returns the protocols supported by both of vpool protocols and storage pool protocols when
     * the storage pool's protocols is set to NFS_OR_CIFS
     * 
     * @param cosProtocols
     * @param poolProtocols
     * @return
     */
    private static Set<String> getMatchingProtocolsForFilePool(StringSet vpoolProtocols, StringSet poolProtocols) {
        Set<String> protocols = new HashSet<String>();
        // file pool protocols could be set to NFS_OR_CIFS
        if (poolProtocols != null && !poolProtocols.isEmpty()) {
            if (poolProtocols.contains(StorageProtocol.File.NFS_OR_CIFS.name())) {
                if (vpoolProtocols.size() == 1 &&
                        (vpoolProtocols.contains(StorageProtocol.File.NFS.name()) ||
                        vpoolProtocols.contains(StorageProtocol.File.CIFS.name()))) {
                    protocols = vpoolProtocols;
                }
            }
        }
        return protocols;
    }

    /**
     * Validate the DriveType for HDS systems when tiering policy selected.
     * 
     * @param policy - AutoTierPolicy Name
     * @param sysType - SystemType
     * @param driveType - DriveType
     * @return
     */
    public static boolean validateNullDriveTypeForHDSSystems(String policy, StringSet sysTypeSet, String driveType) {
        return (null != policy && sysTypeSet.contains(VirtualPool.SystemType.hds.toString()) && (driveType == null));
    }
    
    /**
     * Checks that the two virtual pools have matching RemoteCopyVarraySettings
     * @param vpoolA - Virtual Pool A
     * @param vpoolB - Virtual Pool B
     * @param dbClient - database handle
     * @return true if the VpoolRemoteCopyProtectionSettings are the same for both
     */
    public static boolean checkMatchingRemoteCopyVarraysettings(VirtualPool vpoolA, VirtualPool vpoolB, DbClient dbClient) {
        Map<URI, VpoolRemoteCopyProtectionSettings> settingsA = VirtualPool.getRemoteProtectionSettings(vpoolA, dbClient);
        Map<URI, VpoolRemoteCopyProtectionSettings> settingsB = VirtualPool.getRemoteProtectionSettings(vpoolB, dbClient);
        if (settingsA.isEmpty() && settingsB.isEmpty()) {
            // Both have no settings, this is the same
            return true;
        }
        if (!settingsA.isEmpty() && !settingsB.isEmpty() && 
                settingsA.keySet().containsAll(settingsB.keySet()) && settingsB.keySet().containsAll(settingsA.keySet())) {
            // Both have settings with matching key sets, now just check the values
            for (Map.Entry<URI, VpoolRemoteCopyProtectionSettings> entryA : settingsA.entrySet()) {
                VpoolRemoteCopyProtectionSettings copySettingsA = entryA.getValue();
                VpoolRemoteCopyProtectionSettings copySettingsB = settingsB.get(entryA.getKey());
                if (copySettingsA == null || copySettingsB == null) {
                    // For some reason, one doesn't have a copy setting
                    return false;
                }
                if (copySettingsA.getCopyMode()==null || copySettingsB.getCopyMode()==null 
                        || !copySettingsA.getCopyMode().equals(copySettingsB.getCopyMode())) {
                    return false;
                }
                if (copySettingsA.getVirtualPool()==null || copySettingsB.getVirtualPool()==null 
                        || !copySettingsA.getVirtualPool().equals(copySettingsB.getVirtualPool())) {
                    return false;
                }
            }
            // Checked all the copy settings, they are equal
            return true;
        }
        return false;
    }
}
