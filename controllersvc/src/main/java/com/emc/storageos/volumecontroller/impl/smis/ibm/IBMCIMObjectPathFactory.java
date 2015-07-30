/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm;

import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.smis.CIMArgumentFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;

/**
 * This class will contain functions and properties related to creating
 * CIMObjectPath objects.
 */
public class IBMCIMObjectPathFactory implements IBMSmisConstants {
    private final static Logger _log = LoggerFactory
            .getLogger(IBMCIMObjectPathFactory.class);
    private final static String REPLICATION_GROUP_NAME_FORMAT = "%s:%s-%s";

    CIMArgumentFactory _cimArgument = null;
    CIMPropertyFactory _cimProperty = null;
    CIMConnectionFactory _cimConnection = null;
    DbClient _dbClient;
    private String _systemNamePrefix;
    private String _paramNamePrefix;

    public void setCimArgumentFactory(CIMArgumentFactory cimArgumentFactory) {
        _cimArgument = cimArgumentFactory;
    }

    public void setCimPropertyFactory(CIMPropertyFactory cimPropertyFactory) {
        _cimProperty = cimPropertyFactory;
    }

    public void setCimConnectionFactory(CIMConnectionFactory connectionFactory) {
        _cimConnection = connectionFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSystemNamePrefix(String systemNamePrefix) {
        _systemNamePrefix = systemNamePrefix;
    }

    public String prefixWithSystemName(String str) {
        return _systemNamePrefix.concat(str);
    }

    public void setParamNamePrefix(String paramNamePrefix) {
        _paramNamePrefix = paramNamePrefix;
    }

    public String prefixWithParamName(String str) {
        return _paramNamePrefix.concat(str);
    }

    /*
     * (non-Javadoc) Construct object path of
     * IBMTSDS_StorageConfigurationService.
     * 
     * Example - Namespace: "/root/ibm"
     * CreationClassName="IBMTSDS_StorageConfigurationService"
     * Name="StorageConfigurationService for IBM.2810-7825363"
     * SystemCreationClassName="IBMTSDS_StorageSystem"
     * SystemName="IBM.2810-7825363"
     */
    public CIMObjectPath getConfigSvcPath(StorageSystem storageDevice) {
        CIMObjectPath configSvcPath;
        try {
            String systemName = storageDevice.getSerialNumber();
            CIMProperty[] configSvcPropKeys = {
                    _cimProperty.string(CP_CREATION_CLASS_NAME,
                            prefixWithParamName(STORAGE_CONFIGURATION_SERVICE)),
                    _cimProperty.string(CP_NAME,
                            STORAGE_CONFIGURATION_SERVICE_NAME + systemName),
                    _cimProperty.string(CP_SYSTEM_CREATION_CLASS_NAME,
                            prefixWithParamName(STORAGE_SYSTEM)),
                    _cimProperty.string(CP_SYSTEM_NAME, systemName) };

            configSvcPath = CimObjectPathCreator.createInstance(
                    prefixWithParamName(STORAGE_CONFIGURATION_SERVICE),
                    Constants.IBM_NAMESPACE, configSvcPropKeys);
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            throw new IllegalStateException("Problem getting config service: "
                    + storageDevice.getLabel());
        }

        return configSvcPath;
    }

    /**
     * Create object path for storage pool.
     */
    public CIMObjectPath getStoragePoolPath(StoragePool storagePool) {
        CIMProperty[] poolKeys = { _cimProperty.string(CP_INSTANCE_ID,
                storagePool.getNativeId()) };

        return CimObjectPathCreator.createInstance(
                storagePool.getPoolClassName(), Constants.IBM_NAMESPACE,
                poolKeys);
    }

    /**
     * Construct object path of block object.
     */
    public CIMObjectPath getBlockObjectPath(StorageSystem storage,
            BlockObject blockObject) {
        return getObjectPath(storage, blockObject.getNativeId());
    }

    /**
     * Construct object paths of volumes.
     */
    public CIMObjectPath[] getVolumePaths(StorageSystem storageDevice,
            String[] volumeNames) throws Exception {
        ArrayList<CIMObjectPath> theElementsList = new ArrayList<CIMObjectPath>();
        for (String volumeName : volumeNames) {
            theElementsList.add(getObjectPath(storageDevice, volumeName));
        }

        return theElementsList
                .toArray(new CIMObjectPath[theElementsList.size()]);
    }

    /**
     * Construct object path with given volume's native ID
     */
    private CIMObjectPath getObjectPath(StorageSystem storage, String nativeId) {
        @SuppressWarnings("rawtypes")
        CIMProperty[] volumeKeys = {
                _cimProperty.string(CP_CREATION_CLASS_NAME, CP_STORAGE_VOLUME),
                _cimProperty.string(CP_DEVICE_ID, nativeId),
                _cimProperty.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        _paramNamePrefix.concat(STORAGE_SYSTEM)),
                _cimProperty.string(CP_SYSTEM_NAME, storage.getSerialNumber()) };

        return CimObjectPathCreator.createInstance(
                CP_STORAGE_VOLUME,
                Constants.IBM_NAMESPACE, volumeKeys);
    }

    /**
     * Return requested output argument.
     */
    public Object getFromOutputArgs(CIMArgument[] outputArguments, String key) {
        Object element = null;
        for (CIMArgument outArg : outputArguments) {
            if (outArg != null) {
                if (outArg.getName().equals(key)) {
                    element = outArg.getValue();
                    break;
                }
            }
        }

        return element;
    }

    /**
     * Construct object path of Storage HardwareID management service.
     */
    public CIMObjectPath getStorageHardwareIDManagementService(
            StorageSystem storage) {
        String systemName = storage.getSerialNumber();
        CIMProperty[] properties = {
                _cimProperty
                        .string(CP_CREATION_CLASS_NAME,
                                prefixWithParamName(STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE)),
                _cimProperty
                        .string(CP_NAME,
                                STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE_NAME
                                        + systemName),
                _cimProperty.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        prefixWithParamName(STORAGE_SYSTEM)),
                _cimProperty.string(CP_SYSTEM_NAME, systemName) };

        return CimObjectPathCreator.createInstance(
                prefixWithParamName(STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE),
                Constants.IBM_NAMESPACE, properties);
    }

    /**
     * Construct object path of controller configuration service.
     */
    public CIMObjectPath getControllerConfigSvcPath(StorageSystem storageDevice) {
        String systemName = storageDevice.getSerialNumber();
        CIMProperty[] configSvcPropKeys = {
                _cimProperty.string(CP_CREATION_CLASS_NAME,
                        prefixWithParamName(CONTROLLER_CONFIGURATION_SERVICE)),
                _cimProperty.string(CP_NAME, CONTROLLER_CONFIGURATION_SERVICE_NAME + systemName),
                _cimProperty.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        prefixWithParamName(STORAGE_SYSTEM)),
                _cimProperty.string(CP_SYSTEM_NAME, systemName)
        };

        return CimObjectPathCreator.createInstance(prefixWithParamName(CONTROLLER_CONFIGURATION_SERVICE),
                Constants.IBM_NAMESPACE, configSvcPropKeys);
    }

    /**
     * Get protocol controller object path.
     * 
     * @param storage StorageSystem
     * @param protocolControllerId String of protocol controller ID
     * @return CIMObjectPath of protocol controller
     */
    public CIMObjectPath getSCSIProtocolControllerPath(StorageSystem storage,
            String protocolControllerId) {
        if (protocolControllerId == null || protocolControllerId.isEmpty()) {
            return null;
        }

        String creationClass = CP_SCSI_PROTOCOL_CONTROLLER;
        CIMProperty[] keys = {
                _cimProperty.string(CP_CREATION_CLASS_NAME, creationClass),
                _cimProperty.string(CP_DEVICE_ID, protocolControllerId),
                _cimProperty.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                _cimProperty.string(CP_SYSTEM_NAME, storage.getSerialNumber())
        };
        return CimObjectPathCreator.createInstance(creationClass, Constants.IBM_NAMESPACE, keys);
    }

    /**
     * Get protocol controllers in output argument.
     */
    public CIMObjectPath[] getProtocolControllersFromOutputArgs(CIMArgument[] outputArguments) {
        CIMObjectPath[] protocolControllers = null;
        for (CIMArgument outputArgument : outputArguments) {
            if ((outputArgument != null) && outputArgument.getName().equals(CP_PROTOCOL_CONTROLLERS)) {
                protocolControllers = (CIMObjectPath[]) outputArgument.getValue();
                break;
            }
        }
        return protocolControllers;
    }

    public CIMObjectPath getReplicationSvcPath(StorageSystem storageDevice) {
        String replicationSvcClassName = prefixWithParamName(REPLICATION_SERVICE);
        @SuppressWarnings("rawtypes")
        CIMProperty[] replicationSvcKeys = {
                _cimProperty.string(CP_CREATION_CLASS_NAME,
                        replicationSvcClassName),
                _cimProperty.string(CP_NAME, REPLICATION_SERVICE_NAME
                        + storageDevice.getSerialNumber()),
                _cimProperty.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        prefixWithParamName(STORAGE_SYSTEM)),
                _cimProperty.string(CP_SYSTEM_NAME, storageDevice.getSerialNumber()) };
        return CimObjectPathCreator.createInstance(replicationSvcClassName,
                Constants.IBM_NAMESPACE, replicationSvcKeys);
    }

    public CIMObjectPath getConsistencyGroupPath(StorageSystem storage,
            String groupName) {
        String groupInstanceName = String.format(REPLICATION_GROUP_NAME_FORMAT,
                _paramNamePrefix, storage.getSerialNumber(), groupName);
        @SuppressWarnings("rawtypes")
        CIMProperty[] replicationGroupKeys = { _cimProperty.string(
                CP_INSTANCE_ID, groupInstanceName) };
        return CimObjectPathCreator.createInstance(CP_CONSISTENCY_GROUP,
                Constants.IBM_NAMESPACE, replicationGroupKeys);
    }

    public CIMObjectPath getSnapshotGroupPath(StorageSystem storage,
            String groupName) {
        CIMObjectPath queryPath = CimObjectPathCreator.createInstance(CP_SNAPSHOT_GROUP,
                Constants.IBM_NAMESPACE, null);
        return getObjectPathByName(storage, queryPath, "ElementName", groupName);
    }

    /**
     * Return IBMTSDS_StorageSynchronized instance referenced by the given
     * BlockObject.
     * 
     * @param storage
     * @param subject
     * @return CIMObjectPath
     */
    public CIMObjectPath getSyncObject(StorageSystem storage,
            BlockObject subject) {
        CloseableIterator<CIMObjectPath> syncReference = null;
        CIMObjectPath syncObjectPath = NULL_IBM_CIM_OBJECT_PATH;
        try {
            CIMObjectPath subjectPath = getBlockObjectPath(storage, subject);
            syncReference = _cimConnection.getConnection(storage)
                    .getCimClient()
                    .referenceNames(subjectPath, CP_STORAGE_SYNCHRONIZED, null);
            if (syncReference != null) {
                while (syncReference.hasNext()) {
                    syncObjectPath = syncReference.next();
                    if (syncObjectPath != null) {
                        break;
                    }
                }
            }
        } catch (WBEMException e) {
            _log.warn(String.format("Trying to find syncObject for %s failed",
                    subject.getId()));
        } finally {
            if (syncReference != null) {
                syncReference.close();
            }
        }
        return syncObjectPath;
    }

    public CIMObjectPath getGroupSynchronizedPath(StorageSystem storage, String snapGroupName) {
        CIMObjectPath queryPath = CimObjectPathCreator.createInstance(CP_GROUP_SYNCHRONIZED,
                Constants.IBM_NAMESPACE, null);
        return getObjectPathByName(storage, queryPath, "RelationshipName", snapGroupName);
    }

    @SuppressWarnings("rawtypes")
    public CIMObjectPath getCimObjectPathFromOutputArgs(
            CIMArgument[] outputArguments, String key) {
        CIMObjectPath cimObjectPath = null;
        for (CIMArgument outArg : outputArguments) {
            if (outArg != null) {
                if (outArg.getName().equals(key)) {
                    cimObjectPath = (CIMObjectPath) outArg.getValue();
                    break;
                }
            }
        }
        return cimObjectPath;
    }

    /**
     * Executes query
     * 
     * @param storageSystem
     * @param query
     * @param queryLanguage
     * @return list of matched instances
     */
    public List<CIMInstance> executeQuery(StorageSystem storageSystem,
            CIMObjectPath objectPath, String query, String queryLanguage) {
        CloseableIterator<CIMInstance> iterator = null;
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        _log.info(String.format(
                "Executing query: %s, objectPath: %s, query language: %s",
                query, objectPath, queryLanguage));
        List<CIMInstance> instanceList = new ArrayList<CIMInstance>();
        try {
            iterator = client.execQuery(objectPath, query, queryLanguage);
            while (iterator.hasNext()) {
                CIMInstance instance = iterator.next();
                instanceList.add(instance);
            }

        } catch (WBEMException we) {
            _log.error(
                    "Caught an error while attempting to execute query and process query result. Query: "
                            + query, we);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }

        return instanceList;
    }

    /**
     * Look up CIMObjectPath by property value. The value has to be unique on array.
     * 
     * @param storage
     * @param path CIMObjectPath for the query (only object name, no key)
     * @param prop property (string type) that used in lookup
     * @param value string value
     * @return CIMObjectPath of returned instance, or null
     */
    private CIMObjectPath getObjectPathByName(StorageSystem storage, CIMObjectPath path, String prop, String value) {
        String query = String.format(
                "Select * From %s Where %s=\"%s\"",
                path.getObjectName(), prop, value);
        List<CIMInstance> instances = executeQuery(storage, path, query, "WQL");
        if (instances.isEmpty()) {
            return null;
        }
        else {
            return instances.get(0).getObjectPath();
        }
    }
}
