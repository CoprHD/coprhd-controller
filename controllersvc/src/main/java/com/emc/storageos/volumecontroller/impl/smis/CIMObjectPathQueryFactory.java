/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.google.common.collect.Iterators.toArray;
import static com.google.common.collect.Iterators.transform;
import static java.lang.String.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.sun.xml.internal.xsom.impl.scd.Iterators.Map;

/**
 * This class uses WBEM queries to acquire the CIMObjectPath instances directly from the Provider.
 */
public class CIMObjectPathQueryFactory extends AbstractCIMObjectPathFactory {

    private static final Logger log = LoggerFactory.getLogger(CIMObjectPathQueryFactory.class);
    private static final String QUERY_LANG = "wql";

    @Override
    public CIMObjectPath getElementCompositionSvcPath(StorageSystem storageDevice) {
        String wql = format("SELECT * FROM %s WHERE SystemName like'%s'",
                prefixWithParamName(REPLICATION_SERVICE), storageDevice.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(REPLICATION_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getConfigSvcPath(StorageSystem storageDevice) {
        String wql = format("SELECT Name FROM %s WHERE SystemName = '%s'",
                prefixWithParamName(STORAGE_CONFIGURATION_SERVICE), getSystemName(storageDevice));
        CIMObjectPath queryClass = getQueryClass(STORAGE_CONFIGURATION_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getStorageSynchronized(StorageSystem sourceSystem, BlockObject source, StorageSystem targetSystem,
            BlockObject target) {
        return null;
    }

    @Override
    public CIMObjectPath getGroupSynchronized(CIMObjectPath sourceGroup, CIMObjectPath targetGroup) {
        return null;
    }

    @Override
    public CIMObjectPath getControllerConfigSvcPath(StorageSystem storageDevice) {
        String wql = format("SELECT * FROM %s WHERE SystemName like'%s'",
                prefixWithParamName(CONTROLLER_CONFIGURATION_SERVICE), storageDevice.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(CONTROLLER_CONFIGURATION_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getTierPolicySvcPath(StorageSystem storageDevice) {
        String wql = format("SELECT * FROM %s WHERE SystemName like'%s'",
                prefixWithParamName(TIER_POLICY_SERVICE), storageDevice.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(TIER_POLICY_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    /**
     * TODO StoragePoolSetting or Tier_policyRule
     */
    @Override
    public CIMObjectPath getTierPolicyRulePath(StorageSystem storageDevice, String policyName) {
        String tierPolicyRuleClazz = storageDevice.checkIfVmax3() ? STORAGE_POOL_SETTING : TIER_POLICY_RULE;
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s' AND PolicyRuleName like '%s'",
                prefixWithParamName(tierPolicyRuleClazz), storageDevice.getSerialNumber(),
                policyName);
        CIMObjectPath queryClass = getQueryClass(TIER_POLICY_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];

    }

    @Override
    public CIMObjectPath getControllerReplicationSvcPath(StorageSystem storageDevice) {
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s'",
                prefixWithParamName(REPLICATION_SERVICE), storageDevice.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(REPLICATION_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getStorageProtectionSvcPath(StorageSystem storageDevice) {
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s'",
                prefixWithParamName(PROTECTION_SERVICE), storageDevice.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(PROTECTION_SERVICE);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getReplicationServiceCapabilitiesPath(StorageSystem storageDevice) {
        String wql = format("SELECT * FROM %s WHERE InstanceID  = '%s'",
                prefixWithParamName(REPLICATION_SERVICE_CAPABILTIES), getSystemName(storageDevice));
        CIMObjectPath queryClass = getQueryClass(REPLICATION_SERVICE_CAPABILTIES);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getSeSystemRegistrationService(StorageSystem storage) {
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s'",
                SE_SYSTEM_REGISTRATION_SERVICE, storage.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(SE_SYSTEM_REGISTRATION_SERVICE);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath[] getClarProtocolControllers(StorageSystem storageDevice, String protocolControllerName) throws Exception {
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s' AND DeviceID like '%s'",
                CLAR_LUN_MASKING_SCSI_PROTOCOL_CONTROLLER, storageDevice.getSerialNumber(),
                protocolControllerName);
        CIMObjectPath queryClass = getQueryClass(CLAR_LUN_MASKING_SCSI_PROTOCOL_CONTROLLER);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths;
    }

    @Override
    public CIMObjectPath[] getVolumePaths(StorageSystem storageDevice, String[] volumeNames) throws Exception {
        ArrayList<CIMObjectPath> theElementsList = new ArrayList<CIMObjectPath>();

        for (String volumeName : volumeNames) {
            String wql = format("SELECT Name FROM %s WHERE SystemName = '%s' AND DeviceID = '%s'",
                    prefixWithParamName(STORAGE_VOLUME), getSystemName(storageDevice), volumeName);
            CIMObjectPath queryClass = getQueryClass(STORAGE_VOLUME);

            CIMObjectPath[] volumePath = execQuery(storageDevice, queryClass, wql);

            if (volumePath.length != 1) {
                return null;
            }
            theElementsList.add(volumePath[0]);
        }

        CIMObjectPath[] volArray = {};
        volArray = theElementsList.toArray(volArray);
        return volArray;
    }

    @Override
    public CIMObjectPath[] getTargetPortPaths(StorageSystem storageDevice, List<URI> targetURIList) throws Exception {
        // Query factory already gets the values from provider, no need to implement.
        return null;
    }

    @Override
    public CIMObjectPath[] getInitiatorPaths(StorageSystem storageDevice, String[] initiatorNames) throws Exception {
        List<CIMObjectPath> cimPathList = new ArrayList<CIMObjectPath>();
        for (String iniName : initiatorNames) {
            // TODO
            String wql = format("SELECT * FROM %s WHERE InstanceID like '%s'",
                    CP_SE_STORAGE_HARDWARE_ID, iniName);
            CIMObjectPath queryClass = getQueryClass(CP_SE_STORAGE_HARDWARE_ID);

            CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

            if (paths.length == 0) {
                continue;
            }
            cimPathList.add(paths[0]);
        }
        CIMObjectPath[] paths = {};
        return cimPathList.toArray(paths);

    }

    @Override
    public HashMap<String, CIMObjectPath> getInitiatorToInitiatorPath(StorageSystem storageDevice, List<String> initiatorNames)
            throws Exception {
        HashMap<String, CIMObjectPath> initiatorToInitiatorPathMap = new HashMap<String, CIMObjectPath>();
        for (String iniName : initiatorNames) {
            String wql = format("SELECT * FROM %s WHERE InstanceID like '%s'",
                    CP_SE_STORAGE_HARDWARE_ID, iniName);
            CIMObjectPath queryClass = getQueryClass(CP_SE_STORAGE_HARDWARE_ID);

            CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

            if (paths.length == 0) {
                continue;
            }
            initiatorToInitiatorPathMap.put(iniName, paths[0]);
        }
        return initiatorToInitiatorPathMap;
    }

    @Override
    public CIMObjectPath getMaskingViewPath(StorageSystem storageDevice, String groupName) {
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s' AND DeviceID like '%s'",
                prefixWithParamName(LUN_MASKING_VIEW), storageDevice.getSerialNumber(), groupName);
        CIMObjectPath queryClass = getQueryClass(LUN_MASKING_VIEW);

        CIMObjectPath[] paths = execQuery(storageDevice, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getLunMaskingProtocolControllerPath(StorageSystem storage, ExportMask exportMask) {
        String wql = format("SELECT * FROM %s WHERE SystemName like '%s' AND DeviceID like '%s'",
                prefixWithParamName(LUN_MASKING_SCSI_PROTOCOL_CONTROLLER), storage.getSerialNumber(), exportMask.getNativeId());
        CIMObjectPath queryClass = getQueryClass(LUN_MASKING_SCSI_PROTOCOL_CONTROLLER);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getBlockObjectPath(StorageSystem storage, StorageSystem source, BlockObject blockObject) {
        return null;
    }

    @Override
    public CIMObjectPath getMaskingGroupPath(StorageSystem storage, String storageGroupName, MASKING_GROUP_TYPE se_deviceMaskingGroup)
            throws Exception {
        String wql = format("SELECT * FROM %s WHERE ElementName LIKE '%s'",
                prefixWithParamName(se_deviceMaskingGroup.toString()), getSystemName(storage));
        CIMObjectPath queryClass = getQueryClass(se_deviceMaskingGroup.toString());

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getBlockObjectPath(StorageSystem storage, BlockObject blockObject) {
        String[] properties = new String[] {
                CP_CREATION_CLASS_NAME, CP_DEVICE_ID, CP_SYSTEM_CREATION_CLASS_NAME, CP_SYSTEM_NAME
        };
        String wql = format("SELECT %s FROM %s WHERE %s = '%s' AND %s = '%s'",
                Joiner.on(',').join(properties),
                prefixWithParamName(STORAGE_VOLUME),
                CP_SYSTEM_NAME,
                getSystemName(storage),
                CP_DEVICE_ID,
                blockObject.getNativeId());
        CIMObjectPath queryClass = getQueryClass(STORAGE_VOLUME);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getVolumePath(StorageSystem storage, String nativeId) {
        String wql = format("SELECT Name FROM %s WHERE SystemName = '%s' AND DeviceID = '%s'",
                prefixWithParamName(STORAGE_VOLUME), getSystemName(storage), nativeId);
        CIMObjectPath queryClass = getQueryClass(STORAGE_VOLUME);

        CIMObjectPath[] volumePath = execQuery(storage, queryClass, wql);

        if (volumePath.length == 0) {
            return null;
        }
        return volumePath[0];
    }

    @Override
    public CIMObjectPath getReplicationGroupPath(StorageSystem storage, String groupName) {
        return getReplicationGroupPath(storage, storage.getSerialNumber(), groupName);
    }

    @Override
    public CIMObjectPath getReplicationGroupPath(StorageSystem activeProviderStorageProxy, String serialNumber, String groupName) {
        String wql = format("SELECT * FROM %s WHERE InstanceID like '%s' AND ElementName = '%s'",
                SE_REPLICATION_GROUP, serialNumber, groupName);
        CIMObjectPath queryClass = getQueryClass(SE_REPLICATION_GROUP);

        CIMObjectPath[] paths = execQuery(activeProviderStorageProxy, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getReplicationGroupObjectPath(StorageSystem storage, String instanceId) {
        // -+1 format is not available for rep groups in 8.0.3
        return cimAdapter.getReplicationGroupPath(storage, instanceId);
    }

    @Override
    public CIMObjectPath getSyncAspectPath(StorageSystem storage, String aspectInstanceId) {
        String syncAspectPathStr = storage.checkIfVmax3() ? SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE : CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE;
        String wql1 = format("SELECT * FROM %s WHERE InstanceID like '%s' AND InstanceID like '%s'",
                syncAspectPathStr, aspectInstanceId, storage.getSerialNumber());
        // TODO instanceID is stored in DB, so ideally this should work for V3
        // REVIST : This class is not found for V2.
        CIMObjectPath queryClass = getQueryClass(syncAspectPathStr);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql1);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getStoragePoolPath(StorageSystem storage, StoragePool storagePool) {
        String wql = format("SELECT * FROM %s WHERE InstanceID like '%s' AND InstanceID like '%s'",
                storagePool.getPoolClassName(), storagePool.getPoolName(), storage.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(storagePool.getPoolClassName());

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getPoolSettingPath(StorageSystem storage, String poolSettingID) {
        String wql = format("SELECT * FROM %s WHERE InstanceID like '%s' AND InstanceID like '%s'",
                CLAR_STORAGE_POOL_SETTING, poolSettingID, storage.getSerialNumber());

        CIMObjectPath queryClass = getQueryClass(CLAR_STORAGE_POOL_SETTING);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);
        if (paths.length == 0) {
            return null;
        }
        return paths[0];

    }

    @Override
    public CIMObjectPath getSyncSettingsPath(StorageSystem storage, CIMObjectPath volumePath, String aspectInstanceId) {
        return null;
    }

    @Override
    public CIMObjectPath getGroupSynchronizedPath(StorageSystem storage, String consistencyGroupName, String snapGroupName) {
        return null;
    }

    @Override
    public CIMObjectPath getSyncAspectForSourceGroupPath(StorageSystem storage, String aspectInstanceId) {
        String wql = format("SELECT * FROM %s WHERE InstanceID like '%s' AND InstanceID like '%s'",
                CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP, aspectInstanceId, storage.getSerialNumber());

        CIMObjectPath queryClass = getQueryClass(CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);
        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getGroupSynchronizedSettingsPath(StorageSystem storage, String groupName, String settingsInstance) {
        return null;
    }

    @Override
    public CIMObjectPath getStorageSystem(StorageSystem storage) {
        String wql = format("SELECT * FROM %s WHERE SystemName like'%s'",
                prefixWithParamName(STORAGE_SYSTEM), storage.getSerialNumber());
        CIMObjectPath queryClass = getQueryClass(STORAGE_VOLUME);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public String getProcessorName(StorageSystem system, String processorName) {
        return null;
    }

    @Override
    public CIMObjectPath objectPath(String namespace, String name, CIMProperty[] keys) {
        return null;
    }

    @Override
    public CIMObjectPath objectPath(String instanceId) {
        return null;
    }

    @Override
    public CIMObjectPath getStorageHardwareIDManagementService(StorageSystem storage) {
        String wql = format("SELECT * FROM %s WHERE SystemName like'%s' AND Name ='%s'",
                prefixWithParamName(STORAGE_HARDWARE_ID_MGMT_SVC), storage.getSerialNumber(),
                EMC_STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE);
        CIMObjectPath queryClass = getQueryClass(STORAGE_HARDWARE_ID_MGMT_SVC);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMObjectPath getPrivilegeManagementService(StorageSystem storage) {
        String wql = format("SELECT * FROM %s WHERE SystemName like'%s' AND Name ='%s'",
                prefixWithParamName(CP_CLAR_PRIVILEGE_MGMT_SVC), storage.getSerialNumber(),
                EMC_PRIVILEGE_MANAGEMENT_SERVICE);
        CIMObjectPath queryClass = getQueryClass(CP_CLAR_PRIVILEGE_MGMT_SVC);

        CIMObjectPath[] paths = execQuery(storage, queryClass, wql);

        if (paths.length == 0) {
            return null;
        }
        return paths[0];
    }

    @Override
    public CIMInstance getStoragePoolVdevSettings(CIMObjectPath setting) {
        return null;
    }

    @Override
    public CIMObjectPath getRemoteReplicationCollection(StorageSystem system, RemoteDirectorGroup group) {
        // TODO SRDF chek with Selva, whether he has done any changes.
        return null;
    }

    @Override
    public CIMObjectPath getReplicationSettingObjectPathFromDefault(CIMInstance settingInstance) {
        // NO change needed only for V3.
        return null;
    }

    private WBEMClient getClient(StorageSystem storageSystem) {
        CimConnection connection = cimConnectionFactory.getConnection(storageSystem);
        return connection.getCimClient();
    }

    private CIMObjectPath getQueryClass(String className) {
        return new CIMObjectPath(null, null, null, ROOT_EMC_NAMESPACE, prefixWithParamName(className), null);
    }

    private CIMObjectPath[] execQuery(StorageSystem storageDevice, CIMObjectPath queryClass, String wql) {
        log.info("ExecQuery: {}", wql);

        CloseableIterator<CIMInstance> instances = null;
        try {
            long start = System.currentTimeMillis();
            instances = getClient(storageDevice).execQuery(queryClass, wql, QUERY_LANG);
            log.info("ExecQuery took {}ms", System.currentTimeMillis() - start);

            Iterator<CIMObjectPath> transform = transform(instances, getInstanceToObjectPathFn());
            return toArray(transform, CIMObjectPath.class);
        } catch (WBEMException e) {
            log.warn("Failed to perform SMI-S query: {}", wql, e);
        } finally {
            if (instances != null) {
                instances.close();
            }
        }
        return new CIMObjectPath[0];
    }

    private Function<CIMInstance, CIMObjectPath> getInstanceToObjectPathFn() {
        return new Function<CIMInstance, CIMObjectPath>() {
            @Override
            public CIMObjectPath apply(CIMInstance instance) {
                return instance.getObjectPath();
            }
        };
    }

}
