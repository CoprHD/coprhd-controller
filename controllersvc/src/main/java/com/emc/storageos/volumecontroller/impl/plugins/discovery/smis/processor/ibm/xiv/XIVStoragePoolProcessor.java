/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolClassNames;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePool.SupportedDriveTypeValues;
import com.emc.storageos.db.client.model.StoragePool.SupportedResourceTypes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedProvisioningTypes;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.PoolProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;

/**
 * Processor responsible for handling Provider response data and creates
 * StoragePools.
 */
public class XIVStoragePoolProcessor extends PoolProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(XIVStoragePoolProcessor.class);
    private static final String HARD_SIZE = "HardSize";
    private static final String SOFT_SIZE = "SoftSize";
    private static final String POOL_ID = "PoolID";    
    private static final String OPERATIONAL_STATUS = "OperationalStatus";
    private static final String TWO = "2";
    private static final String IBM_SUBSCRIBEDCAPACITY = "VirtualSpaceConsumed";
    		
    private DbClient _dbClient = null;
    private AccessProfile _profile = null;
    private List<StoragePool> _newPoolList = null;
    private List<StoragePool> _updatePoolList = null;

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        _profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);

        try {
            _newPoolList = new ArrayList<StoragePool>();
            _updatePoolList = new ArrayList<StoragePool>();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);

            StorageSystem device = getStorageSystem(_dbClient,
                    _profile.getSystemId());
            if (SupportedProvisioningTypes.NONE.name().equalsIgnoreCase(
                    device.getSupportedProvisioningType())) {
                _logger.info("Storage System doesn't support volume creations :"
                        + device.getLabel());
                return;
            }

            Set<String> protocols = (Set<String>) keyMap
                    .get(Constants.PROTOCOLS);
            Map<URI, StoragePool> poolsToMatchWithVpool = new HashMap<URI, StoragePool>();
            while (it.hasNext()) {
                CIMInstance poolInstance = it.next();
                try {
                    addPath(keyMap, operation.get_result(),
                            poolInstance.getObjectPath());
                    
                    String hardSizeStr = getCIMPropertyValue(poolInstance,
                            HARD_SIZE);
                    long hardSize = Long.parseLong(hardSizeStr);

                    String softSizeStr = getCIMPropertyValue(poolInstance,
                            SOFT_SIZE);
                    long softSize = Long.parseLong(softSizeStr);

                    SupportedResourceTypes type = SupportedResourceTypes.THICK_ONLY;
                    if (hardSize < softSize) {
                        type = SupportedResourceTypes.THIN_ONLY;
                    }

                    createStoragePool(_dbClient, device, poolInstance,
                            PoolClassNames.IBMTSDS_VirtualPool.name(),
                            type.name(), protocols, poolsToMatchWithVpool,
                            _newPoolList, _updatePoolList);
                } catch (Exception e) {
                    _logger.warn(
                            "StoragePool Discovery failed for {}",
                            getCIMPropertyValue(poolInstance,
                                    Constants.INSTANCEID), getMessage(e));
                }
            }

            // set the pools whose properties got changed.
            keyMap.put(Constants.MODIFIED_STORAGEPOOLS, poolsToMatchWithVpool);
            _dbClient.createObject(_newPoolList);
            _dbClient.updateAndReindexObject(_updatePoolList);
            
            //find the pools not visible in this discovery
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
            StoragePoolAssociationHelper.setStoragePoolVarrays(device.getId(),
                    _newPoolList, _dbClient);
        } catch (Exception e) {
            _logger.error("StoragePool Discovery failed", e);
        } finally {
            _newPoolList = null;
            _updatePoolList = null;
        }
    }
    
    /*
     * Determine operational status of pool
     * READY, if any of the status value is 2 (OK).
     * otherwise NOTREADY
     */
    protected String determineOperationalStatus(CIMInstance poolInstance) {
        String operationalStatus = StoragePool.PoolOperationalStatus.NOTREADY.name();
        try {
            UnsignedInteger16[] opStatus = (UnsignedInteger16[]) poolInstance
                    .getPropertyValue(OPERATIONAL_STATUS);
            for (UnsignedInteger16 status : opStatus) {
                if (status.compareTo(new UnsignedInteger16(TWO)) == 0) {
                    operationalStatus = StoragePool.PoolOperationalStatus.READY
                            .name();
                }
            }
        } catch (Exception ex) {
            _logger.error("Discovering Pool Operational Status failed : {}-->",
                    getCIMPropertyValue(poolInstance, Constants.INSTANCEID), ex);
        }
        
        return operationalStatus;
    } 

    /**
     * Create StoragePool, if not present already, else only update the properties
     *
     * @param dbClient
     * @param device
     * @param poolInstance
     * @param poolClassName
     * @param supportedVolumeTypes
     * @param protocols
     * @param poolsToMatchWithVpool
     * @param newPoolList
     * @param updatePoolList
     * @throws URISyntaxException
     * @throws IOException
     */
    private void createStoragePool(DbClient dbClient, StorageSystem device,
            CIMInstance poolInstance, String poolClassName,
            String supportedVolumeTypes, Set<String> protocols,
            Map<URI, StoragePool> poolsToMatchWithVpool, List<StoragePool> newPoolList,
            List<StoragePool> updatePoolList) throws URISyntaxException,
            IOException {
        boolean newPool = false;
        boolean modifiedPool = false;   // indicates whether to add to modified pools list or not    
        String nativeId = getCIMPropertyValue(poolInstance, POOL_ID);
        String poolName = getCIMPropertyValue(poolInstance, SmisConstants.CP_ELEMENT_NAME);    
        
        StoragePool pool = checkStoragePoolExistsInDB(nativeId, dbClient, device);        
        if (null == pool) {
            newPool = true;
            pool = new StoragePool();
            pool.setId(URIUtil.createId(StoragePool.class));
            pool.setPoolClassName(poolClassName);
            pool.setNativeId(nativeId);
            pool.setStorageDevice(device.getId());
            pool.setPoolServiceType(PoolServiceType.block.toString());
            String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(dbClient, pool); 
            pool.setNativeGuid(poolNativeGuid);
            pool.setLabel(poolNativeGuid);
            //setting default values on Pool Creation
            pool.setMaximumThickVolumeSize(0L);
            pool.setMinimumThickVolumeSize(0L);
            pool.setMaximumThinVolumeSize(0L);
            pool.setMinimumThinVolumeSize(0L);
            _logger.info(String.format("Maximum default limits for volume capacity in storage pool: %s  \n   max thin volume capacity: %s, max thick volume capacity: %s ",
                    pool.getId(), pool.getMaximumThinVolumeSize(), pool.getMaximumThickVolumeSize()));

            pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            Set<String> diskDrives = new HashSet<String>();
            if (device.getModel().equalsIgnoreCase("A14")) {
                // Gen2 2810/2812 use SATA
                diskDrives.add(SupportedDriveTypeValues.SATA.name());
            }
            else {
                // Gen3 2810/2812 114 or 214
                diskDrives.add(SupportedDriveTypeValues.SAS.name());
            }

            pool.addDriveTypes(diskDrives);
        }
        
        String subscribedCapacity = getCIMPropertyValue(poolInstance, IBM_SUBSCRIBEDCAPACITY);
        if (null != subscribedCapacity) {
            pool.setSubscribedCapacity(ControllerUtils.convertBytesToKBytes(subscribedCapacity));
        }
        pool.setFreeCapacity(SmisUtils.getFreeCapacity(poolInstance));
        pool.setTotalCapacity(SmisUtils.getTotalCapacity(poolInstance));
        pool.setPoolName(poolName);
        String operationalStatus = determineOperationalStatus(poolInstance);
        if (!newPool && (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getOperationalStatus(), operationalStatus) ||
                ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getProtocols(), protocols) ||
                ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getSupportedResourceTypes(), supportedVolumeTypes)) ||
                ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(), DiscoveredDataObject.DiscoveryStatus.VISIBLE.name())) {
            modifiedPool = true;
        }
        pool.addProtocols(protocols);
        pool.setOperationalStatus(operationalStatus);

        pool.setSupportedResourceTypes(supportedVolumeTypes);
        pool.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());
        
        if (newPool) {
            newPoolList.add(pool);
            // add new pools to modified pools list to consider them for implicit pool matching.
            poolsToMatchWithVpool.put(pool.getId(), pool);
        }
        else {
            updatePoolList.add(pool);
            // add to modified pool list if pool's property which is required for vPool matcher, has changed.
            // No need to check whether the pool is already there in the list here
            // because this processor is the first to discover pools.
            if (modifiedPool) {
                poolsToMatchWithVpool.put(pool.getId(), pool);
            }
        }
    }
}
