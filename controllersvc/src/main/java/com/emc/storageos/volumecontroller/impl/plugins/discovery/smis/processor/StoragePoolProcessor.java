/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePool.SupportedDriveTypeValues;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedProvisioningTypes;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger16;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processor responsible for handling Provider response data and creates StoragePools.
 */
public class StoragePoolProcessor extends PoolProcessor {
    private static Logger _logger = LoggerFactory.getLogger(StoragePoolProcessor.class);
    private static final String OPERATIONAL_STATUS = "OperationalStatus";
    private static final String DEVICE_STORAGE_POOL = "DeviceStoragePool";
    private static final String EMC_DRIVE_TYPE = "EMCDiskDriveType";
    private static final String MIXED_DRIVE_TYPE = "Mixed";
    private static final String SPACE_STR_DELIM = " ";
    private static final String POOL_ID = "PoolID";
    private static final String TWO = "2";
    private DbClient _dbClient;
    private WBEMClient _cimClient;
    private CoordinatorClient _coordinator;
    private RecordableEventManager _eventManager;
    private AccessProfile profile = null;
    private List<StoragePool> _newPoolList = null;
    private List<StoragePool> _updatePoolList = null;

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        try {
            _newPoolList = new ArrayList<StoragePool>();
            _updatePoolList = new ArrayList<StoragePool>();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            _cimClient = SMICommunicationInterface.getCIMClient(keyMap);
            _coordinator = (CoordinatorClient) keyMap.get(Constants.COORDINATOR_CLIENT);
            _eventManager = (RecordableEventManager) keyMap.get(Constants.EVENT_MANAGER);
            _logger.info("StoragePoolProcessor --- event manager: " + _eventManager);
            StorageSystem device = getStorageSystem(_dbClient, profile.getSystemId());
            if (SupportedProvisioningTypes.NONE.toString().equalsIgnoreCase(
                    device.getSupportedProvisioningType())) {
                _logger.info("Storage System doesn't support volume creations :"
                        + device.getSerialNumber());
                return;
            }
            Set<String> protocols = (Set<String>) keyMap.get(Constants.PROTOCOLS);
            Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap.get(Constants.MODIFIED_STORAGEPOOLS);
            while (it.hasNext()) {
                CIMInstance poolInstance = null;
                try {
                    poolInstance = it.next();

                    // Supporting both thick and thin pools
                    String[] poolClassNameAndSupportedVolumeTypes = determinePoolClassNameAndSupportedVolumeTypes(poolInstance, device);
                    if (null != poolClassNameAndSupportedVolumeTypes) {
                        String instanceID = getCIMPropertyValue(poolInstance,
                                Constants.INSTANCEID);
                        addPath(keyMap, operation.getResult(),
                                poolInstance.getObjectPath());
                        StoragePool pool = checkStoragePoolExistsInDB(
                                getNativeIDFromInstance(instanceID), _dbClient, device);
                        createStoragePool(pool, poolInstance, profile, poolClassNameAndSupportedVolumeTypes[0],
                                poolClassNameAndSupportedVolumeTypes[1], protocols, poolsToMatchWithVpool, device);

                        if (DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(device.getSystemType())) {
                            addPath(keyMap, Constants.VNXPOOLS,
                                    poolInstance.getObjectPath());
                        }

                        if (DiscoveredDataObject.Type.vmax.toString().equalsIgnoreCase(device.getSystemType())) {
                            addPath(keyMap, Constants.VMAXPOOLS,
                                    poolInstance.getObjectPath());
                            if (!device.checkIfVmax3()) {
                                addPath(keyMap, Constants.VMAX2POOLS,
                                        poolInstance.getObjectPath());
                            }
                        }
                        // This approach deviates from the existing built plugin framework for plugin
                        // Discovery
                        // To follow the existing pattern, we need to have different SMI-S calls
                        // 1st to get Device StoragePools alone ,and 2nd to get Thin Pools.
                        // Its a tradeoff between whether to go with the current plugin design or
                        // reduce the number of calls to SMI Provider.
                        // I chose the 2nd option.
                        if (!poolClassNameAndSupportedVolumeTypes[0].contains(DEVICE_STORAGE_POOL)) {
                            addPath(keyMap, Constants.THINPOOLS,
                                    poolInstance.getObjectPath());
                        }

                        addPath(keyMap, Constants.DEVICEANDTHINPOOLS,
                                poolInstance.getObjectPath());
                    } else {
                        _logger.debug("Skipping Pools other than Unified & Virtual & Device : {}",
                                poolInstance.getObjectPath().toString());
                    }
                } catch (Exception e) {
                    _logger.warn("StoragePool Discovery failed for {}",
                            getCIMPropertyValue(poolInstance, Constants.INSTANCEID), e);
                }
            }

            _dbClient.createObject(_newPoolList);
            _dbClient.updateAndReindexObject(_updatePoolList);

            // find the pools not visible in this discovery
            List<StoragePool> discoveredPools = new ArrayList<StoragePool>(_newPoolList);
            discoveredPools.addAll(_updatePoolList);
            List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(discoveredPools, _dbClient, device.getId());
            for (StoragePool notVisiblePool : notVisiblePools) {
                poolsToMatchWithVpool.put(notVisiblePool.getId(), notVisiblePool);
            }
            // If any storage ports on the storage system are in a transport
            // zone, there is an implicit connection to the transport zone
            // varray. We need to add these implicit varray
            // connections for the new storage pool.
            StoragePoolAssociationHelper.setStoragePoolVarrays(device.getId(), _newPoolList, _dbClient);
        } catch (Exception e) {
            _logger.error("StoragePool Discovery failed --> {}", getMessage(e));
        } finally {
            _newPoolList = null;
            _updatePoolList = null;
        }
    }

    /**
     * Include only Unified,Virtual [Thin] and Device Storage Pools (Thick Pool)
     * 
     * @param poolInstance
     * @return String [] array of pool class name (as a first element) and supported volume types (as a second element)
     */
    private String[] determinePoolClassNameAndSupportedVolumeTypes(CIMInstance poolInstance, StorageSystem system) {

        if (StoragePool.PoolClassNames.Clar_DeviceStoragePool.toString().
                equalsIgnoreCase(poolInstance.getClassName())) {
            return new String[] { StoragePool.PoolClassNames.Clar_DeviceStoragePool.toString(),
                    StoragePool.SupportedResourceTypes.THICK_ONLY.toString() };
        } else if (StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString().
                equalsIgnoreCase(poolInstance.getClassName())) {
            return new String[] { StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString(),
                    StoragePool.SupportedResourceTypes.THIN_AND_THICK.toString() };
        }

        if (!system.checkIfVmax3()) {
            if (StoragePool.PoolClassNames.Symm_DeviceStoragePool.toString().
                    equalsIgnoreCase(poolInstance.getClassName())
                    && !SupportedProvisioningTypes.THIN.toString().equalsIgnoreCase(system.getSupportedProvisioningType())) {
                return new String[] { StoragePool.PoolClassNames.Symm_DeviceStoragePool.toString(),
                        StoragePool.SupportedResourceTypes.THICK_ONLY.toString() };
            } else if (StoragePool.PoolClassNames.Symm_VirtualProvisioningPool.toString().
                    equalsIgnoreCase(poolInstance.getClassName())
                    && !SupportedProvisioningTypes.THICK.toString().equalsIgnoreCase(system.getSupportedProvisioningType())) {
                return new String[] { StoragePool.PoolClassNames.Symm_VirtualProvisioningPool.toString(),
                        StoragePool.SupportedResourceTypes.THIN_ONLY.toString() };
            }
        } else {
            // VMAX3 has StorageResourcePools (SRP). These are composed of ThinPools, which we can
            // discover, but would not have write access to. So, we will only discovery SRP pools
            // and skip over other pool discoveries.
            if (StoragePool.PoolClassNames.Symm_SRPStoragePool.toString().
                    equalsIgnoreCase(poolInstance.getClassName())) {
                return new String[] { StoragePool.PoolClassNames.Symm_SRPStoragePool.toString(),
                        StoragePool.SupportedResourceTypes.THIN_ONLY.toString() };
            }
        }
        return null;
    }

    /**
     * Create StoragePool Record, if not present already, else update only the properties.
     * 
     * @param pool
     * @param poolInstance
     * @param profile
     * @param poolClassName
     * @param supportedVolumeTypes
     * @param protocols
     * @param poolsToMatchWithVpool
     * @throws URISyntaxException
     * @throws IOException
     */
    private void createStoragePool(StoragePool pool, CIMInstance poolInstance, AccessProfile profile,
            String poolClassName, String supportedVolumeTypes, Set<String> protocols,
            Map<URI, StoragePool> poolsToMatchWithVpool, StorageSystem device) throws URISyntaxException,
            IOException {
        boolean newPool = false;
        boolean modifiedPool = false;   // indicates whether to add to modified pools list or not
        if (null == pool) {
            String instanceID = getCIMPropertyValue(poolInstance, Constants.INSTANCEID);
            String nativeIdFromInstance = getNativeIDFromInstance(instanceID);
            newPool = true;
            pool = new StoragePool();
            pool.setId(URIUtil.createId(StoragePool.class));
            pool.setPoolName(getCIMPropertyValue(poolInstance, POOL_ID));
            pool.setNativeId(nativeIdFromInstance);
            pool.setStorageDevice(profile.getSystemId());
            pool.setPoolServiceType(PoolServiceType.block.toString());
            String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(_dbClient, pool);
            pool.setNativeGuid(poolNativeGuid);
            pool.setLabel(poolNativeGuid);
            // setting default values on Pool Creation for VMAX and VNX
            pool.setMaximumThickVolumeSize(0L);
            pool.setMinimumThickVolumeSize(0L);
            pool.setMaximumThinVolumeSize(0L);
            pool.setMinimumThinVolumeSize(0L);
            if (device.getAutoTieringEnabled()) {
                pool.setAutoTieringEnabled(Boolean.TRUE);
            } else {
                pool.setAutoTieringEnabled(Boolean.FALSE);
            }
            _logger.info(String.format("Maximum default limits for volume capacity in storage pool %s / %s : \n   " +
                    "max thin volume capacity: %s, max thick volume capacity: %s ",
                    pool.getPoolName(), pool.getId(), pool.getMaximumThinVolumeSize(), pool.getMaximumThickVolumeSize()));
        }

        String maxSubscriptionPercent = getCIMPropertyValue(poolInstance, SmisConstants.CP_EMCMAXSUBSCRIPTIONPERCENT);
        _logger.info(String.format("Discovered maximum subscription percent of storage pool %s from array : %s ", pool.getPoolName(),
                maxSubscriptionPercent));
        // null,0 values indicate "not available".
        Integer newMaxSubscriptionPercentFromArray = maxSubscriptionPercent == null ? null : new Integer(maxSubscriptionPercent);
        _logger.info(String.format("New maximum subscription percent of storage pool %s from array : %s ", pool.getPoolName(),
                newMaxSubscriptionPercentFromArray));
        processMaxSubscriptionPercent(newMaxSubscriptionPercentFromArray, pool);
        _logger.info(String.format("StoragePool %s subscription/utilization percent limits after processing: %s / %s",
                pool.getPoolName(), pool.getMaxThinPoolSubscriptionPercentage(), pool.getMaxPoolUtilizationPercentage()));

        String subscribedCapacity = getCIMPropertyValue(poolInstance, SmisConstants.CP_SUBSCRIBEDCAPACITY);
        if (null != subscribedCapacity) {
            pool.setSubscribedCapacity(ControllerUtils.convertBytesToKBytes(subscribedCapacity));
        }
        pool.setFreeCapacity(SmisUtils.getFreeCapacity(poolInstance));
        pool.setTotalCapacity(SmisUtils.getTotalCapacity(poolInstance));
        pool.setPoolClassName(poolClassName);
        pool.setSupportedResourceTypes(supportedVolumeTypes);
        String operationalStatus = determineOperationalStatus(poolInstance);
        if (!newPool
                && (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getOperationalStatus(), operationalStatus)
                        ||
                        ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getProtocols(), protocols)
                        ||
                        ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                                DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()) ||
                ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(),
                        DiscoveredDataObject.DiscoveryStatus.VISIBLE.name()))) {
            modifiedPool = true;
        }
        pool.addProtocols(protocols);
        pool.setOperationalStatus(operationalStatus);
        pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        pool.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());

        Set<String> diskDrives = new HashSet<String>();
        String driveTypes = getCIMPropertyValue(poolInstance, EMC_DRIVE_TYPE);
        if (null != driveTypes) {
            String driveTypesArr[] = driveTypes.split(SPACE_STR_DELIM);
            if (device.checkIfVmax3() && driveTypesArr.length == 1 && driveTypesArr[0].equals(MIXED_DRIVE_TYPE)) {
                driveTypesArr = getVMAX3PoolDriveTypes(device, poolInstance);
            }
            for (String driveType : driveTypesArr) {
                String driveDisplayName = SupportedDriveTypeValues.getDiskDriveDisplayName(driveType);
                if (null == driveDisplayName) {
                    _logger.warn(
                            "UnSupported DiskDrive Type : {} resulting in drives not getting discovered for this pool: {}",
                            driveType, getCIMPropertyValue(poolInstance, Constants.INSTANCEID));
                    continue;
                }

                diskDrives.add(driveDisplayName);
            }
            if (!newPool && !modifiedPool &&
                    ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getSupportedDriveTypes(), diskDrives)) {
                modifiedPool = true;
            }
            pool.addDriveTypes(diskDrives);
        }
        _logger.info("Discovered disk drives:[{}] for pool id:{}", driveTypes, pool.getId());

        if (newPool) {
            _newPoolList.add(pool);
            // add new pools to modified pools list to consider them for implicit pool matching.
            if (!poolsToMatchWithVpool.containsKey(pool.getId())) {
                poolsToMatchWithVpool.put(pool.getId(), pool);
            }
        }
        else {
            _updatePoolList.add(pool);
            // add to modified pool list if pool's property which is required for vPool matcher, has changed.
            // No need to check whether the pool is already there in the list here
            // because this processor is the first to discover pools.
            if (modifiedPool && !poolsToMatchWithVpool.containsKey(pool.getId())) {
                poolsToMatchWithVpool.put(pool.getId(), pool);
            }
        }
    }

    private void processMaxSubscriptionPercent(Integer newMaxSubscriptionPercentFromArray, StoragePool pool) {

        // get default limits from coordinator
        int maxSubscriptionPercent = (int)CapacityMatcher.getMaxPoolSubscriptionPercentage(pool, _coordinator);
        int maxUtilizationPercent = (int)CapacityMatcher.getMaxPoolUtilizationPercentage(pool, _coordinator);
        _logger.info(String.format("Default max subscription/utilization percent limits in vipr: %s / %s",
                maxSubscriptionPercent, maxUtilizationPercent));

        // get pool limits in vipr
        int poolSubscriptionPercent = pool.getMaxThinPoolSubscriptionPercentage() == null ? 0 : pool.getMaxThinPoolSubscriptionPercentage();
        int poolUtilizationPercent = pool.getMaxPoolUtilizationPercentage() == null ? 0 : pool.getMaxPoolUtilizationPercentage();
        _logger.info(String.format("StoragePool %s subscription/utilization percent limits in vipr: %s / %s",
                pool.getPoolName(), poolSubscriptionPercent, poolUtilizationPercent));

        // check if we need to set pool max limits based on array limit for max subscription
        if (isArrayLimitDefined(newMaxSubscriptionPercentFromArray)) {
            // array limit is defined
            if(poolSubscriptionPercent == 0 && newMaxSubscriptionPercentFromArray < maxSubscriptionPercent) {
                // array limit is less than system default limit
                pool.setMaxThinPoolSubscriptionPercentage(newMaxSubscriptionPercentFromArray);
            }
            if(poolUtilizationPercent == 0 && newMaxSubscriptionPercentFromArray < maxUtilizationPercent) {
                // array limit is less than system default limit
                pool.setMaxPoolUtilizationPercentage(newMaxSubscriptionPercentFromArray);
            }
        }

        Integer currentMaxSubscriptionPercentFromArray = pool.getMaxThinPoolSubscriptionPercentageFromArray();
        _logger.info(String.format("Current maximum subscription percent of storage pool %s from array in vipr : %s ",
                pool.getPoolName(), currentMaxSubscriptionPercentFromArray));

        // Currently smis uses value of 0 as indication that MaxSubscriptionPercent is not defined.
        // Some array clients explicitly set this array limit to 0 to indicate that the value is 0%.
        // The OPT was filed 448553 and it targeted for 4.6.2
        // Based on the OPT resolution, we use both, 0 and null, values as indication that the property is not defined.
        if (isArrayLimitDefined(newMaxSubscriptionPercentFromArray) &&
                newMaxSubscriptionPercentFromArray < poolSubscriptionPercent) {
            // reset vipr limit and send alert
            pool.setMaxThinPoolSubscriptionPercentage(newMaxSubscriptionPercentFromArray);
            recordBourneStoragePoolEvent(RecordableEventManager.EventType.StoragePoolUpdated,
                    pool, "Discovered pool max subscription percent is below current pool subscription limit. The limit will be reset.",
                    RecordType.Alert, _dbClient, _eventManager);
            // check if we need to reset max utilization percent in vipr
            // pool max utilization percent is always less or equal to pool max subscription percent,
            // so we do this check in this 'if' statement
            if (newMaxSubscriptionPercentFromArray < poolUtilizationPercent) {
                // reset vipr utilization limit and send alert
                pool.setMaxPoolUtilizationPercentage(newMaxSubscriptionPercentFromArray);
                recordBourneStoragePoolEvent(RecordableEventManager.EventType.StoragePoolUpdated,
                        pool, "Discovered pool max subscription percent is below current pool utilization limit. The limit will be reset.",
                        RecordType.Alert, _dbClient, _eventManager);
            }
        } else if (isArrayLimitDefined(currentMaxSubscriptionPercentFromArray) &&
                currentMaxSubscriptionPercentFromArray == poolSubscriptionPercent &&
                (!isArrayLimitDefined(newMaxSubscriptionPercentFromArray)||
                currentMaxSubscriptionPercentFromArray < newMaxSubscriptionPercentFromArray)) {
            // In this case array limit went up from previous value and max pool subscription percent is using old array value ---
            // send event that array value was increased so client may increase vipr limits if needed.
            recordBourneStoragePoolEvent(RecordableEventManager.EventType.StoragePoolUpdated,
                    pool, "Discovered pool max subscription percent is above current pool subscription limit",
                    RecordType.Event, _dbClient, _eventManager);
        }

        // set array subscription percent in the pool
        pool.setMaxThinPoolSubscriptionPercentageFromArray(newMaxSubscriptionPercentFromArray);
    }

    private boolean isArrayLimitDefined(Integer maxSubscriptionPercentFromArray) {
        return (maxSubscriptionPercentFromArray != null && maxSubscriptionPercentFromArray != 0);
    }

    /*
     * determine Operational Status of Pool
     * READY , if any of the status Value is 2 (OK).
     * otherwise NOTREADY
     */
    private String determineOperationalStatus(CIMInstance poolInstance) {
        String operationalStatus = StoragePool.PoolOperationalStatus.NOTREADY.toString();
        try {
            UnsignedInteger16[] opStatus = (UnsignedInteger16[]) poolInstance
                    .getPropertyValue(OPERATIONAL_STATUS);
            for (UnsignedInteger16 status : opStatus) {
                if (status.compareTo(new UnsignedInteger16(TWO)) == 0) {
                    operationalStatus = StoragePool.PoolOperationalStatus.READY
                            .toString();
                }
            }
        } catch (Exception ex) {
            _logger.error("Discovering Pool Operational Status failed : {}-->",
                    getCIMPropertyValue(poolInstance, Constants.INSTANCEID), ex);
        }
        return operationalStatus;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }

    /**
     * Record storage pool alert/event
     * 
     * @param storagePoolEventType
     * @param pool
     * @param description
     * @param eventType
     */
    private static void recordBourneStoragePoolEvent(RecordableEventManager.EventType storagePoolEventType,
            StoragePool pool, String description,
            RecordType eventType, DbClient dbClient, RecordableEventManager eventManager) {

        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(pool, storagePoolEventType.toString(),
                description, "", dbClient, ControllerUtils.BLOCK_EVENT_SERVICE, eventType.toString(),
                ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _logger.info("ViPR {} event recorded. Description: {}", event.getType(), event.getDescription());
        } catch (Exception ex) {
            _logger.error(String.format("Failed to record event %s. Event description: %s.",
                    event.getType(), event.getDescription()), ex);
        }

    }

    private String[] getVMAX3PoolDriveTypes(StorageSystem storageDevice, CIMInstance poolInstance) {
        Set<String> driveTypes = new HashSet<String>();
        CloseableIterator<CIMInstance> virtualProvisioningPoolItr = null;

        _logger.info("Trying to get all VirtualProvisioningPools for storage pool {}",
                poolInstance.getProperty(SmisConstants.CP_INSTANCE_ID).toString());
        CIMObjectPath poolPath = poolInstance.getObjectPath();
        try {
            virtualProvisioningPoolItr = getAssociatorInstances(poolPath, null, SmisConstants.SYMM_VIRTUAL_PROVISIONING_POOL,
                    null, null, SmisConstants.PS_V3_VIRTUAL_PROVISIONING_POOL_PROPERTIES);
            while (virtualProvisioningPoolItr != null && virtualProvisioningPoolItr.hasNext()) {
                CIMInstance virtualProvisioningPoolInstance = virtualProvisioningPoolItr.next();
                String diskDriveType = CIMPropertyFactory.getPropertyValue(virtualProvisioningPoolInstance,
                        SmisConstants.CP_DISK_DRIVE_TYPE);
                if (diskDriveType != null) {
                    driveTypes.add(diskDriveType);
                }
            }
        } catch (WBEMException e) {
            _logger.error("Error getting VirtualProvisioningPools", e);
        } finally {
            if (virtualProvisioningPoolItr != null) {
                virtualProvisioningPoolItr.close();
            }
        }
        String[] driveTypesArr = driveTypes.toArray(new String[driveTypes.size()]);
        return driveTypesArr;
    }

    public CloseableIterator<CIMInstance>
            getAssociatorInstances(CIMObjectPath path,
                    String assocClass, String resultClass, String role, String resultRole, String[] prop)
                    throws WBEMException {
        return _cimClient.associatorInstances(path, null, resultClass, null, null, false, prop);
    }

}
