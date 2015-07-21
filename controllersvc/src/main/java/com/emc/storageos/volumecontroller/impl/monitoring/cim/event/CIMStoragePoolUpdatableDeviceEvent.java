/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.event;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

@Component("CIMStoragePoolUpdatableDeviceEvent")
@Scope("prototype")
public class CIMStoragePoolUpdatableDeviceEvent extends
        CIMInstanceRecordableDeviceEvent implements ApplicationContextAware {
    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(CIMStoragePoolUpdatableDeviceEvent.class);

    /**
     * Overloaded constructor
     * 
     * @param dbClient
     */
    @Autowired
    public CIMStoragePoolUpdatableDeviceEvent(DbClient dbClient) {
        super(dbClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<? extends DataObject> getResourceClass() {
        return StoragePool.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNativeGuid() {

        _logger.debug("Computing NativeGuid for VolumeView Event assoicaued with StoragePool");

        if (_nativeGuid != null) {
            _logger.debug("Using already computed NativeGuid : {}", _nativeGuid);
            return _nativeGuid;
        }

        try {
            _nativeGuid = NativeGUIDGenerator
                    .generateSPNativeGuidFromSPIndication(_indication);
            logMessage("NativeGuid for StoragePool Computed as  : [{}]",
                    new Object[] { _nativeGuid });
        } catch (Exception e) {
            _logger.error("Unable to compute NativeGuid :", e);
        }

        return _nativeGuid;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        _applicationContext = applicationContext;
    }

    /**
     * Log the messages. This method eliminates the logging condition check
     * every time when we need to log a message.
     * 
     * @param msg
     * @param obj
     */
    private void logMessage(String msg, Object[] obj) {
        if (_monitoringPropertiesLoader.isToLogIndications()) {
            _logger.debug("-> " + msg, obj);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRecordType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensions() {
        return null;
    }

    /**
     * A Utility method to get the value out of a map.
     * 
     * @param key
     * @return
     */
    private String getValueFromIndication(String key) {
        return _indication.get(key);
    }

    /**
     * As part of indication we receive the capacity size in bytes and since
     * Bourne platform maintains volume size in KB, capacity will be calculated
     * by diving with 1024.
     * 
     * @param freeCapacityAttributeName
     * @param poolName
     * @param totalCapacityAttributeName
     * @param subscribedCapacityAttributeName
     * @return
     */
    protected boolean retriveAndProcessIndicationAttributeValues(
            String freeCapacityAttribute, String poolNameAttribute,
            String totalCapacityAttribute, String subscribedCapacityAttribute) {

        UpdtableStoragePoolModel spModel = new UpdtableStoragePoolModel();

        if (freeCapacityAttribute != null) {
            spModel.setFreeCapacity(getValueFromIndication(freeCapacityAttribute));
        }
        if (poolNameAttribute != null) {
            spModel.setPoolName(getValueFromIndication(poolNameAttribute));
        }
        if (subscribedCapacityAttribute != null) {
            spModel.setSubscribedCapacity(getValueFromIndication(subscribedCapacityAttribute));
        }
        if (totalCapacityAttribute != null) {
            spModel.setTotalCapacity(getValueFromIndication(totalCapacityAttribute));
        }

        return updateStoragePoolObject(spModel);
    }

    /**
     * update the Storage Pool object with the available data came as part of
     * indication.
     * 
     * @return
     */
    private boolean updateStoragePoolObject(UpdtableStoragePoolModel spModel) {

        Long freeCapacity = spModel.getFreeCapacityInKB();
        String poolName = spModel.getPoolName();
        Long totalCapacity = spModel.getTotalCapacityInKB();
        Long subscribedCapacity = spModel.getSubscribedCapacityInKB();

        try {
            StoragePool pool = retriveStoragePoolFromDatabase();
            if (pool != null) {

                boolean isUpdateRequired = Boolean.FALSE;
                logMessage(
                        "==> Comparing Existing StoregPool Object with Recieved Information :\n freeCapacity: Existing Value [{}] - Recieved Value [{}], \n PoolName: Existing Value [{}] - Recieved Value [{}], \n TotalCapacity: Existing Value [{}] - Recieved Value [{}], \n SubscribedCapacity: Existing Value [{}] - Recieved Value [{}] \n",
                        new Object[] { pool.calculateFreeCapacityWithoutReservations(), freeCapacity,
                                pool.getPoolName(), poolName,
                                pool.getTotalCapacity(), totalCapacity,
                                pool.getSubscribedCapacity(),
                                subscribedCapacity });

                if (freeCapacity != null
                        && freeCapacity.longValue() != pool.calculateFreeCapacityWithoutReservations().longValue()) {
                    logMessage(
                            "Updating Free Capacity : from {} to {}",
                            new Object[] { pool.calculateFreeCapacityWithoutReservations(), freeCapacity });
                    pool.setFreeCapacity(freeCapacity);
                    isUpdateRequired = true;
                }
                if (poolName != null && !poolName.equals(pool.getPoolName())) {
                    logMessage("Updating Pool Name : from {} to {}",
                            new Object[] { pool.getPoolName(), poolName });
                    pool.setPoolName(poolName);
                    isUpdateRequired = true;
                }
                if (totalCapacity != null
                        && totalCapacity.longValue() != pool.getTotalCapacity().longValue()) {
                    logMessage("Updating Total Capacity : from {} to {}",
                            new Object[] { pool.getTotalCapacity(),
                                    totalCapacity });
                    pool.setTotalCapacity(totalCapacity);
                    isUpdateRequired = true;
                }
                if (subscribedCapacity != null
                        && subscribedCapacity.longValue() != pool.getSubscribedCapacity().longValue()) {
                    logMessage("Updating Subscribed Capacity : from {} to {}",
                            new Object[] { pool.getSubscribedCapacity(),
                                    subscribedCapacity });
                    pool.setSubscribedCapacity(subscribedCapacity);
                    isUpdateRequired = true;
                }
                if (isUpdateRequired) {
                    _dbClient.persistObject(pool);
                    logMessage("Storage Pool Object Updated", new Object[] {});
                }

                return isUpdateRequired;
            } else {
                _logger.debug("Indication not processed as no assosiated Storage Pool Object found");
            }
        } catch (IOException e) {
            _logger.error(
                    "Error occured while retriving StoragePool Object for Corresponding Indication {}",
                    e.getMessage());
        } catch (NumberFormatException e) {
            _logger.error(
                    "Error occured while reading capacity data from Corresponding Indication {}",
                    e.getMessage());
        }
        return Boolean.FALSE;

    }

    /**
     * Identifies and use VNX specific attributes to read the corresponding
     * values from VNX Storage Pool indication
     * 
     * @return
     */
    public boolean updateStoragePoolObjectFromVNXStoragePoolIndication() {

        return retriveAndProcessIndicationAttributeValues(
                CIMConstants.STORAGE_POOL_INDICATION_FREE_CAPACITY,
                CIMConstants.STORAGE_POOL_INDICATION_POOL_NAME,
                CIMConstants.STORAGE_POOL_INDICATION_TOTAL_CAPACITY,
                CIMConstants.STORAGE_POOL_INDICATION_SUBSCRIBED_CAPACITY);
    }

    /**
     * Identifies and use VMAX specific attributes to read the corresponding
     * values from VMAX Storage Pool indication
     * 
     * @return
     */
    public boolean updateStoragePoolObjectFromVMAXStoragePoolIndication() {

        return retriveAndProcessIndicationAttributeValues(
                CIMConstants.STORAGE_POOL_INDICATION_FREE_CAPACITY,
                CIMConstants.STORAGE_POOL_INDICATION_POOL_NAME,
                CIMConstants.STORAGE_POOL_INDICATION_TOTAL_CAPACITY,
                CIMConstants.STORAGE_POOL_INDICATION_SUBSCRIBED_CAPACITY);

    }

    /**
     * Queries the database for the existing Storage Pool Object
     * 
     * @return an instance of StoragePool
     * @throws IOException
     */
    private StoragePool retriveStoragePoolFromDatabase() throws IOException {
        _logger.debug("looking for Storage pool Object");
        List<URI> resourceURIs = new ArrayList<URI>();
        String nativeGuid = getNativeGuid();

        if (nativeGuid == null) {
            return null;
        }

        resourceURIs = _dbClient
                .queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePoolByNativeGuidConstraint(nativeGuid));
        if (resourceURIs.size() > 1) {
            _logger.error(
                    "Multiple StoragePools found with same native guid {}",
                    nativeGuid);
        } else if (resourceURIs.isEmpty()) {
            _logger.debug("No StoragePools found with native guid {}",
                    nativeGuid);
        } else if (resourceURIs.size() == 1) {
            StoragePool pool = (StoragePool) _dbClient.queryObject(
                    getResourceClass(), resourceURIs.get(0));
            return pool;
        }

        return null;
    }

}

class UpdtableStoragePoolModel {

    private static final Logger _logger = LoggerFactory
            .getLogger(UpdtableStoragePoolModel.class);

    String _freeCapacity = null;
    String _totalCapacity = null;
    String _poolName = null;
    String _subscribedCapacity = null;

    public Long getFreeCapacityInKB() {
        if (_freeCapacity != null) {
            try {
                return ControllerUtils.convertBytesToKBytes(_freeCapacity);
            } catch (NumberFormatException e) {
                _logger.debug("Invalid Free Capacity value", e.getMessage());
            } catch (Exception e) {
                _logger.debug("Error evaluating Free Capacity", e.getMessage());
            }
        }
        return null;
    }

    public Long getSubscribedCapacityInKB() {

        if (_subscribedCapacity != null) {
            try {
                return ControllerUtils
                        .convertBytesToKBytes(_subscribedCapacity);
            } catch (NumberFormatException e) {
                _logger.debug("Invalid Subscribed Capacity value",
                        e.getMessage());
            } catch (Exception e) {
                _logger.debug("Error evaluating Subscribed Capacity",
                        e.getMessage());
            }
        }
        return null;
    }

    public Long getTotalCapacityInKB() {
        if (_totalCapacity != null) {
            try {
                return ControllerUtils.convertBytesToKBytes(_totalCapacity);
            } catch (NumberFormatException e) {
                _logger.debug("Invalid Total Capacity value", e.getMessage());
            } catch (Exception e) {
                _logger.debug("Error evaluating Total Capacity", e.getMessage());
            }
        }
        return null;
    }

    public String getFreeCapacity() {
        return _freeCapacity;
    }

    public void setFreeCapacity(String freeCapacity) {
        _freeCapacity = freeCapacity;
    }

    public String getTotalCapacity() {
        return _totalCapacity;
    }

    public void setTotalCapacity(String totalCapacity) {
        _totalCapacity = totalCapacity;
    }

    public String getPoolName() {
        return _poolName;
    }

    public void setPoolName(String poolName) {
        _poolName = poolName;
    }

    public String _subscribedCapacity() {
        return _subscribedCapacity;
    }

    public void setSubscribedCapacity(String subscribedCapacity) {
        _subscribedCapacity = subscribedCapacity;
    }
}