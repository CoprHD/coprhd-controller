/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class SmisUtils {

    private static final Logger _log = LoggerFactory.getLogger(SmisUtils.class);

    public static final String SPACE_STR = " ";
    public static final String SLO = "SLO";
    public static final String WORKLOAD = "Workload";

    /**
     * Return RemainingManagedSpace of the pool.
     * RemainingManagedSpace always matches with Element Managers in most of the cases.
     * Capacity will not match with Element Managers, when a VNX Pool is created with UnBound raid level
     * as user doesn't know the RAID to choose. FreeCapacity will match once it becomes bound.
     * 
     * @param poolInstance
     * @return
     */
    public static Long getFreeCapacity(CIMInstance poolInstance) {
        String freeCapacityStr = getCIMPropertyValue(poolInstance, SmisConstants.CP_REMAININGMANAGEDSPACE);
        return ControllerUtils.convertBytesToKBytes(freeCapacityStr);
    }

    /**
     * Return TotalManagedSpace of the pool.
     * TotalManagedSpace always matches with Element Managers in most of the cases.
     * Capacity will not match with Element Managers, when a VNX Pool is created with UnBound raid level
     * as user doesn't know the RAID to choose. TotalCapacity will match once it becomes bound.
     * 
     * @param poolInstance
     * @return
     */
    public static Long getTotalCapacity(CIMInstance poolInstance) {
        String totalCapacityStr = getCIMPropertyValue(poolInstance, SmisConstants.CP_TOTALMANAGEDSPACE);
        return ControllerUtils.convertBytesToKBytes(totalCapacityStr);
    }

    /**
     * get Property Value;
     * 
     * @param instance cim instance
     * @param propName name of property
     * @return
     */
    public static String getCIMPropertyValue(CIMInstance instance, String propName) {
        String value = null;
        try {
            value = instance.getPropertyValue(propName).toString();
        } catch (Exception e) {
            _log.debug("Property {} Not found in returned Instance {}", propName, instance.getObjectPath());
        }
        return value;
    }

    public static Volume checkStorageVolumeExistsInDB(String nativeGuid, DbClient dbClient)
            throws IOException {
        @SuppressWarnings("deprecation")
        List<URI> volumeUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeNativeGuidConstraint(nativeGuid));

        if (!volumeUris.isEmpty()) {
            Volume volume = dbClient.queryObject(Volume.class, volumeUris.get(0));
            if (!volume.getInactive()) {
                return volume;
            }
        }
        return null;
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, WBEMClient client, URI storagePoolURI) {
        StorageSystem storageSystem = null;

        try {
            StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
            storageSystem = dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice());
            _log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePoolURI,
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));

            // Get cim object path factory from context
            CIMObjectPathFactory objectPathFactory = null;
            // FIXME Shouldn't have hardcoded bean references here
            if (storageSystem.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
                objectPathFactory = (CIMObjectPathFactory) ControllerServiceImpl.getBean("vmaxCIMObjectPathFactoryAdapter");
            } else if (storageSystem.getSystemType().equals(StorageSystem.Type.vnxblock.toString())) {
                objectPathFactory = (CIMObjectPathFactory) ControllerServiceImpl.getBean("vnxCIMObjectPathFactory");
            } else {
                String msg = String.format("Unexpected storage system type: %s for storage system %s ",
                        storageSystem.getSystemType(), storageSystem.getId());
                _log.error(msg);
                throw new RuntimeException(msg);
            }
            CIMObjectPath poolPath = objectPathFactory.getStoragePoolPath(storageSystem, storagePool);
            CIMInstance poolInstance = client.getInstance(poolPath, true, false, null);

            // Get capacity properties.
            Long freePoolCapacity = getFreeCapacity(poolInstance);
            String subscribedCapacity = getCIMPropertyValue(poolInstance, SmisConstants.CP_SUBSCRIBEDCAPACITY);

            // Update storage pool and save to data base
            storagePool.setFreeCapacity(freePoolCapacity);
            if (null != subscribedCapacity) {
                storagePool.setSubscribedCapacity(ControllerUtils.convertBytesToKBytes(subscribedCapacity));
            }

            _log.info(String.format("New storage pool capacity data for pool %n  %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePoolURI,
                    storagePool.getFreeCapacity(),
                    storagePool.getSubscribedCapacity()));

            dbClient.persistObject(storagePool);
        } catch (Exception e) {
            _log.error(
                    String.format(
                            "Failed to update capacity of storage pool after volume provisioning operation. %n  Storage system: %s, storage pool %s .",
                            storageSystem.getId(), storagePoolURI), e);
        }

    }

    /**
     * Access state of a volume is derived from the "Access" field, with an exception from the contents of the "StatusDescription" field.
     * 
     * @param accessState simple access state, see Volume VolumeAccessState enum.
     * @param statusDescriptions a string of fields that may contain "NOT_READY", and if it does, we want to mark the volume as such.
     * @return the access state. Defaults to read/write
     */
    public static String generateAccessState(String accessState, Collection<String> statusDescriptions) {
        if ((accessState != null) && (statusDescriptions != null) && (statusDescriptions.contains(SmisConstants.NOT_READY))) {
            return Volume.VolumeAccessState.NOT_READY.name();
        } else if (accessState != null) {
            String displayName = Volume.VolumeAccessState.getVolumeAccessStateDisplayName(accessState);
            if (displayName.equals(Volume.VolumeAccessState.UNKNOWN.name())) {
                return accessState;
            } else {
                return displayName;
            }
        }
        return Volume.VolumeAccessState.READWRITE.name();
    }

    /**
     * This method checks if the storage sytem is using SMIS 8.0 then translates
     * the delimiter from "+" to "-+-".
     * 
     * @param storageDevice The reference to storage system
     * @param translateString The string to be translated
     * @return returns translatedString if V3 provider or same string.
     */
    public static String translate(StorageSystem storageDevice, String translateString) {
        if (storageDevice.getUsingSmis80()) {
            translateString = translateString.replaceAll(Constants.SMIS_PLUS_REGEX, Constants.SMIS80_DELIMITER_REGEX);
        }
        return translateString;
    }

    /*
     * Parse target group name
     */
    public static String getTargetGroupName(String instanceId, Boolean isUsingSMIS80) {
        if (isUsingSMIS80) {
            // for VMAX V3 instanceId, e.g., 000196700567+EMC_SMI_RG1415737386866
            return instanceId.split(Constants.SMIS_PLUS_REGEX)[1];
        } else {
            // for VMAX V2 using 4.6.2, instanceId, e.g., 557B5BBA+1+SYMMETRIX+000195701573
            return instanceId.split(Constants.SMIS_PLUS_REGEX)[0];
        }

    }

    public static String getSLOPolicyName(CIMInstance instance) {
        Object sloNameObj = instance.getPropertyValue(SmisConstants.CP_EMC_SLO);
        String sloName = null, emcWorkload = null;

        if (null != sloNameObj) {
            sloName = String.valueOf(sloNameObj);
        }
        Object emcWorkloadObj = instance.getPropertyValue(SmisConstants.CP_EMC_WORKLOAD);
        if (null != emcWorkloadObj) {
            emcWorkload = String.valueOf(emcWorkloadObj);
        }
        _log.info("sloName {} emcWorkload {}", sloName, emcWorkload);
        String emcFastSetting = String.valueOf(instance.getPropertyValue(SmisConstants.CP_FAST_SETTING));
        _log.debug("EMCFastSetting: {}", emcFastSetting);

        return formatSGSLOName(sloName, emcWorkload);
    }

    public static boolean checkPolicyMatchForVMAX3(String storageGroupPolicyName, String autoTierPolicyName) {
        if (autoTierPolicyName.contains(storageGroupPolicyName) &&
                !(autoTierPolicyName.contains(Constants.WORKLOAD) && !storageGroupPolicyName.contains(Constants.WORKLOAD))) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Format the given EMCSLOName & EMCWorkload to understand the
     * AutoTieringPolicy persisted in DB.
     * 
     * Ex. Bronze, OLTP => Bronze SLO OLTP Workload
     * 
     * @param sloName - SLO Name
     * @param emcWorkload - Workload
     * @return - formatted SLO Name.
     */
    public static String formatSGSLOName(String sloName, String emcWorkload) {
        if (null != sloName && sloName.length() > 0) {
            StringBuffer fastSetting = new StringBuffer(sloName).append(SPACE_STR).append(SLO);
            if (null != emcWorkload && emcWorkload.length() > 0) {
                fastSetting.append(SPACE_STR).append(emcWorkload).append(SPACE_STR).append(WORKLOAD);
            }
            return fastSetting.toString();
        }
        return null;
    }

    /*
     * Set settings instance for VMAX V3 only.
     *
     * @param StorageSytem      storage
     * @param sourceElementId   String of source volume (or source group) ID
     * @param elementName       String used as ElementName when creating ReplicationSettingData during single snapshot
     *                          creation, or RelationshipName used in CreateGroupReplica for group snapshot.
     *
     * @see com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxSnapshotOperations#getReplicationSettingData
     *
     * Note elementName should be target device's DeviceID or target group ID.
     */
    public static String generateVmax3SettingsInstance(StorageSystem storage, String sourceElementId, String elementName) {
        // SYMMETRIX-+-000196700567-+-<sourceElementId>-+-<elementName>-+-0
        StringBuilder sb = new StringBuilder("SYMMETRIX");
        sb.append(Constants.SMIS80_DELIMITER)
                .append(storage.getSerialNumber())
                .append(Constants.SMIS80_DELIMITER).append(sourceElementId)
                .append(Constants.SMIS80_DELIMITER).append(elementName)
                .append(Constants.SMIS80_DELIMITER).append("0");
        return sb.toString();
    }
}
