/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Constants;
import com.sun.xml.internal.xsom.impl.scd.Iterators.Map;

/**
 * This class will contain functions and properties related to creating CIMObjectPath objects.
 */
public class CIMObjectPathCreatorFactory extends AbstractCIMObjectPathFactory {
    private final static Logger _log = LoggerFactory.getLogger(CIMObjectPathCreatorFactory.class);

    @Override
    public CIMObjectPath getElementCompositionSvcPath(StorageSystem storageDevice) {
        CIMObjectPath elementCompositionSvcPath;
        try {
            CIMProperty[] elementCompositionSvcPropKeys = {
                    cimPropertyFactory.string(CP_CREATION_CLASS_NAME,
                            prefixWithParamName(ELEMENT_COMPOSITION_SERVICE)),
                    cimPropertyFactory.string(CP_NAME, EMC_ELEMENT_COMPOSITION_SERVICE),
                    cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME,
                            prefixWithParamName(STORAGE_SYSTEM)),
                    cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
            };
            elementCompositionSvcPath = CimObjectPathCreator.createInstance(prefixWithParamName(ELEMENT_COMPOSITION_SERVICE),
                    getCimConnectionFactory().getNamespace(storageDevice), elementCompositionSvcPropKeys);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting element composition service: " + storageDevice.getSerialNumber());
        }
        return elementCompositionSvcPath;
    }

    @Override
    public CIMObjectPath getConfigSvcPath(StorageSystem storageDevice) {
        CIMObjectPath configSvcPath;
        try {
            CIMProperty[] configSvcPropKeys = {
                    cimPropertyFactory.string(CP_CREATION_CLASS_NAME,
                            prefixWithParamName(STORAGE_CONFIGURATION_SERVICE)),
                    cimPropertyFactory.string(CP_NAME, EMC_STORAGE_CONFIGURATION_SERVICE),
                    cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME,
                            prefixWithParamName(STORAGE_SYSTEM)),
                    cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
            };
            configSvcPath = CimObjectPathCreator.createInstance(prefixWithParamName(STORAGE_CONFIGURATION_SERVICE),
                    getCimConnectionFactory().getNamespace(storageDevice), configSvcPropKeys);
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            throw new IllegalStateException("Problem getting config service: " + storageDevice.getSerialNumber());
        }
        return configSvcPath;
    }

    @Override
    public CIMObjectPath getStorageSynchronized(StorageSystem sourceSystem, BlockObject source, StorageSystem targetSystem,
            BlockObject target) {
        CIMObjectPath sourcePath = cimAdapter.getBlockObjectPath(sourceSystem, sourceSystem, source);
        CIMObjectPath targetPath = cimAdapter.getBlockObjectPath(targetSystem, sourceSystem, target);
        CIMProperty[] propKeys = { cimPropertyFactory.string(CP_SYNCED_ELEMENT, targetPath.toString()),
                cimPropertyFactory.string(CP_SYSTEM_ELEMENT, sourcePath.toString()) };
        String protocol = sourceSystem.getSmisUseSSL() ? CimConstants.SECURE_PROTOCOL : CimConstants.DEFAULT_PROTOCOL;
        return CimObjectPathCreator.createInstance(
                SE_STORAGE_SYNCHRONIZED_SV_SV, getCimConnectionFactory().getNamespace(sourceSystem), propKeys);
    }

    @Override
    public CIMObjectPath getGroupSynchronized(CIMObjectPath sourceGroup, CIMObjectPath targetGroup) {
        // TODO Intention is to eventually rely on known group names...
        // getReplicationGroupPath(targetSystem, "target group name");
        // getReplicationGroupPath(sourceSystem, "source group name");
        CIMProperty[] propKeys = { cimPropertyFactory.string(CP_SYNCED_ELEMENT, targetGroup.toString()),
                cimPropertyFactory.string(CP_SYSTEM_ELEMENT, sourceGroup.toString()) };
        return CimObjectPathCreator.createInstance(SE_GROUP_SYNCHRONIZED_RG_RG, ROOT_EMC_NAMESPACE, propKeys);
    }

    @Override
    public CIMObjectPath getControllerConfigSvcPath(StorageSystem storageDevice) {
        CIMProperty[] configSvcPropKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME,
                        prefixWithParamName(CONTROLLER_CONFIGURATION_SERVICE)),
                cimPropertyFactory.string(CP_NAME, EMC_CONTROLLER_CONFIGURATION_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(CONTROLLER_CONFIGURATION_SERVICE),
                getCimConnectionFactory().getNamespace(storageDevice), configSvcPropKeys);
    }

    @Override
    public CIMObjectPath getTierPolicySvcPath(StorageSystem storageDevice) {
        CIMProperty[] configSvcPropKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME,
                        prefixWithParamName(TIER_POLICY_SERVICE)),
                cimPropertyFactory.string(CP_NAME, EMC_TIER_POLICY_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(TIER_POLICY_SERVICE),
                cimConnectionFactory.getNamespace(storageDevice), configSvcPropKeys);
    }

    @Override
    public CIMObjectPath getTierPolicyRulePath(StorageSystem storageDevice, String policyName) {
        CIMProperty[] configSvcPropKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME,
                        prefixWithParamName(storageDevice.checkIfVmax3() ? STORAGE_POOL_SETTING : TIER_POLICY_RULE)),
                cimPropertyFactory.string(CP_POLICY_NAME, SmisUtils.translate(storageDevice, policyName)),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME,
                        prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(storageDevice.checkIfVmax3() ? STORAGE_POOL_SETTING
                : TIER_POLICY_RULE),
                cimConnectionFactory.getNamespace(storageDevice), configSvcPropKeys);
    }

    @Override
    public CIMObjectPath getControllerReplicationSvcPath(StorageSystem storageDevice) {
        CIMProperty[] replicationSvcKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(REPLICATION_SERVICE)),
                cimPropertyFactory.string(CP_NAME, EMC_REPLICATION_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(REPLICATION_SERVICE),
                cimConnectionFactory.getNamespace(storageDevice), replicationSvcKeys);
    }

    @Override
    public CIMObjectPath getStorageProtectionSvcPath(StorageSystem storageDevice) {
        CIMProperty[] replicationSvcKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(PROTECTION_SERVICE)),
                cimPropertyFactory.string(CP_NAME, EMC_PROTECTION_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(PROTECTION_SERVICE),
                cimConnectionFactory.getNamespace(storageDevice), replicationSvcKeys);
    }

    @Override
    public CIMObjectPath getReplicationServiceCapabilitiesPath(StorageSystem storageDevice) {
        CIMProperty[] propKeys = {
                cimPropertyFactory.string(CP_INSTANCE_ID, getSystemName(storageDevice))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(REPLICATION_SERVICE_CAPABILTIES),
                cimConnectionFactory.getNamespace(storageDevice), propKeys);
    }

    @Override
    public CIMObjectPath getSeSystemRegistrationService(StorageSystem storage) {
        CIMProperty[] properties = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, SE_SYSTEM_REGISTRATION_SERVICE),
                cimPropertyFactory.string(CP_NAME, SYSTEM_REGISTRATION_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, EMC_SYSTEM_REGISTRATION_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_NAME, EMC_SMI_S_PROVIDER)
        };
        return CimObjectPathCreator.createInstance(SE_SYSTEM_REGISTRATION_SERVICE, cimConnectionFactory.getNamespace(storage), properties);
    }

    @Override
    public CIMObjectPath[] getClarProtocolControllers(StorageSystem storageDevice, String protocolControllerName) throws Exception {
        List<CIMObjectPath> list = new ArrayList<CIMObjectPath>();
        if (protocolControllerName == null || protocolControllerName.isEmpty()) {
            return null;
        }
        CIMProperty[] keys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, CLAR_LUN_MASKING_SCSI_PROTOCOL_CONTROLLER),
                cimPropertyFactory.string(CP_DEVICE_ID, protocolControllerName),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
        };
        list.add(CimObjectPathCreator.createInstance("Clar_LunMaskingSCSIProtocolController",
                cimConnectionFactory.getNamespace(storageDevice), keys));
        CIMObjectPath[] array = {};
        return list.toArray(array);
    }

    @Override
    public CIMObjectPath[] getVolumePaths(StorageSystem storageDevice, String[] volumeNames) throws Exception {
        ArrayList<CIMObjectPath> theElementsList = new ArrayList<CIMObjectPath>();
        for (String volumeName : volumeNames) {
            CIMProperty[] volumeKeys = {
                    cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_VOLUME)),
                    cimPropertyFactory.string(CP_DEVICE_ID, volumeName),
                    cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                    cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storageDevice))
            };
            CIMObjectPath volumePath = CimObjectPathCreator.createInstance(prefixWithParamName(STORAGE_VOLUME),
                    cimConnectionFactory.getNamespace(storageDevice), volumeKeys);
            theElementsList.add(volumePath);
        }
        CIMObjectPath[] volArray = {};
        volArray = theElementsList.toArray(volArray);
        return volArray;
    }

    @Override
    public CIMObjectPath[] getTargetPortPaths(StorageSystem storageDevice, List<URI> targetURIList) throws Exception {
        CIMObjectPath protocolEndpointPath = CimObjectPathCreator.createInstance(CIM_PROTOCOL_ENDPOINT,
                ROOT_EMC_NAMESPACE);
        List<CIMObjectPath> objectPaths = new ArrayList<CIMObjectPath>();
        Set<String> portSet = new HashSet<String>();
        for (URI target : targetURIList) {
            StoragePort storagePort = dbClient.queryObject(StoragePort.class, target);
            String portName;
            if (storagePort.getTransportType().equals("FC")) {
                portName = storagePort.getPortNetworkId().replaceAll(":", "");
            } else {
                portName = storagePort.getPortEndPointID();
            }
            portSet.add(portName);
        }
        CloseableIterator<CIMInstance> iterator =
                cimConnectionFactory.getConnection(storageDevice).getCimClient().enumerateInstances(
                        protocolEndpointPath, false, false, false, null);
        while (iterator.hasNext()) {
            CIMInstance instance = iterator.next();
            String protocolEndpointName = CIMPropertyFactory.getPropertyValue(instance, SmisConstants.CP_NAME);
            if (portSet.contains(protocolEndpointName)) {
                objectPaths.add(instance.getObjectPath());
            }
        }
        CIMObjectPath[] objectPathArray = {};
        objectPathArray = objectPaths.toArray(objectPathArray);
        return objectPathArray;
    }

    @Override
    public CIMObjectPath[] getInitiatorPaths(StorageSystem storageDevice, String[] initiatorNames) throws Exception {
        CIMObjectPath[] initiatorPortPaths = {};
        ArrayList<CIMObjectPath> list = new ArrayList<CIMObjectPath>();
        for (String initiatorName : initiatorNames) {
            CIMProperty[] initiatorKeys = {
                    cimPropertyFactory.string(CP_INSTANCE_ID, initiatorName)
            };
            CIMObjectPath initiatorPortPath = CimObjectPathCreator.createInstance(CP_SE_STORAGE_HARDWARE_ID,
                    cimConnectionFactory.getNamespace(storageDevice),
                    initiatorKeys);
            list.add(initiatorPortPath);
        }
        initiatorPortPaths = list.toArray(initiatorPortPaths);
        return initiatorPortPaths;
    }

    @Override
    public HashMap<String, CIMObjectPath> getInitiatorToInitiatorPath(StorageSystem storageDevice, List<String> initiatorNames)
            throws Exception {
        // No need to implement here
        return null;
    }

    @Override
    public CIMObjectPath getMaskingViewPath(StorageSystem storageDevice, String groupName) {
        CIMProperty[] mvKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(LUN_MASKING_VIEW)),
                cimPropertyFactory.string(CP_DEVICE_ID,
                        storageDevice.getUsingSmis80() ? groupName : prefixWithSystemName(storageDevice.getSerialNumber()).concat("+")
                                .concat(groupName)),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME,
                        SmisUtils.translate(storageDevice, prefixWithSystemName(storageDevice.getSerialNumber())))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(LUN_MASKING_VIEW),
                cimConnectionFactory.getNamespace(storageDevice), mvKeys);
    }

    @Override
    public CIMObjectPath getLunMaskingProtocolControllerPath(StorageSystem storage,
            ExportMask exportMask) {
        String creationClass = prefixWithParamName(LUN_MASKING_SCSI_PROTOCOL_CONTROLLER);
        String storageSystem = prefixWithParamName(STORAGE_SYSTEM);
        String systemName = SmisUtils.translate(storage, prefixWithSystemName(storage.getSerialNumber()));
        CIMProperty[] keys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, creationClass),
                cimPropertyFactory.string(CP_DEVICE_ID, exportMask.getNativeId()),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, storageSystem),
                cimPropertyFactory.string(CP_SYSTEM_NAME, systemName)
        };
        return CimObjectPathCreator.createInstance(creationClass, cimConnectionFactory.getNamespace(storage),
                keys);
    }

    @Override
    public CIMObjectPath getBlockObjectPath(StorageSystem storage, StorageSystem source, BlockObject blockObject) {

        @SuppressWarnings("rawtypes")
        CIMProperty[] volumeKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_VOLUME)),
                cimPropertyFactory.string(CP_DEVICE_ID, blockObject.getNativeId()),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storage))
        };
        return CimObjectPathCreator.createInstance(null, null, null,
                cimConnectionFactory.getNamespace(storage), prefixWithParamName(STORAGE_VOLUME), volumeKeys);
    }

    @Override
    public CIMObjectPath getBlockObjectPath(StorageSystem storage, BlockObject blockObject) {
        @SuppressWarnings("rawtypes")
        CIMProperty[] volumeKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_VOLUME)),
                cimPropertyFactory.string(CP_DEVICE_ID, blockObject.getNativeId()),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storage))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(STORAGE_VOLUME),
                cimConnectionFactory.getNamespace(storage), volumeKeys);
    }

    @Override
    public CIMObjectPath getVolumePath(StorageSystem storage, String nativeId) {
        CIMProperty[] volumeKeys = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_VOLUME)),
                cimPropertyFactory.string(CP_DEVICE_ID, nativeId),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storage))
        };
        return CimObjectPathCreator.createInstance(prefixWithParamName(STORAGE_VOLUME), cimConnectionFactory.getNamespace(storage),
                volumeKeys);
    }

    @Override
    public CIMObjectPath getReplicationGroupPath(StorageSystem storage, String groupName) {
        return getReplicationGroupPath(storage, storage.getSerialNumber(), groupName);
    }

    @Override
    public CIMObjectPath getReplicationGroupPath(StorageSystem activeProviderStorageProxy, String serialNumber, String groupName) {
        // VMAX V3, e.g., 000196700567+EMC_SMI_RG1414546375042
        String groupInstanceName = activeProviderStorageProxy.getUsingSmis80() ? String.format("%s+%s", serialNumber, groupName)
                : String.format("%s+1+%s%s", groupName, getSystemNamePrefix(), serialNumber);

        CIMProperty[] replicationGroupKeys = {
                cimPropertyFactory.string(CP_INSTANCE_ID, groupInstanceName)
        };
        return CimObjectPathCreator.createInstance(SE_REPLICATION_GROUP, cimConnectionFactory.getNamespace(activeProviderStorageProxy),
                replicationGroupKeys);
    }

    @Override
    public CIMObjectPath getReplicationGroupObjectPath(StorageSystem storage, String instanceId) {
        CIMProperty[] replicationGroupKeys = {
                cimPropertyFactory.string(CP_INSTANCE_ID, instanceId)
        };
        return CimObjectPathCreator.createInstance(SE_REPLICATION_GROUP, cimConnectionFactory.getNamespace(storage), replicationGroupKeys);
    }

    @Override
    public CIMObjectPath getSyncAspectPath(StorageSystem storage, String aspectInstanceId) {
        CIMProperty[] syncAspectKeys = {
                cimPropertyFactory.string(CP_INSTANCE_ID, aspectInstanceId)
        };
        return CimObjectPathCreator.createInstance(storage.checkIfVmax3() ? SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE
                : CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE,
                cimConnectionFactory.getNamespace(storage), syncAspectKeys);
    }

    @Override
    public CIMObjectPath getStoragePoolPath(StorageSystem storage, StoragePool storagePool) {
        StringBuffer poolInstanceId = new StringBuffer(storage.getNativeGuid());
        poolInstanceId.append(Constants.PLUS).append(storagePool.getNativeId());
        CIMProperty[] poolKeys = { cimPropertyFactory.string(CP_INSTANCE_ID, SmisUtils.translate(storage, poolInstanceId.toString())) };
        return CimObjectPathCreator.createInstance(storagePool.getPoolClassName(), cimConnectionFactory.getNamespace(storage), poolKeys);
    }

    @Override
    public CIMObjectPath getPoolSettingPath(StorageSystem storage, String poolSettingID) {
        CIMProperty[] poolsettingkeys = {
                cimPropertyFactory.string(CP_INSTANCE_ID, poolSettingID)
        };
        return CimObjectPathCreator.createInstance(CLAR_STORAGE_POOL_SETTING, cimConnectionFactory.getNamespace(storage), poolsettingkeys);
    }

    @Override
    public CIMObjectPath getSyncSettingsPath(StorageSystem storage, CIMObjectPath volumePath, String aspectInstanceId) {
        CIMObjectPath syncAspectPath = cimAdapter.getSyncAspectPath(storage, aspectInstanceId);
        CIMProperty[] settingsKeys = {
                cimPropertyFactory.reference(CP_MANAGED_ELEMENT, volumePath),
                cimPropertyFactory.reference(CP_SETTING_DATA, syncAspectPath)
        };
        return CimObjectPathCreator.createInstance(storage.checkIfVmax3() ? SYMM_SETTINGS_DEFINE_STATE_SV_SAFS
                : CLAR_SETTINGS_DEFINE_STATE_SV_SAFS,
                cimConnectionFactory.getNamespace(storage), settingsKeys);
    }

    @Override
    public CIMObjectPath getGroupSynchronizedPath(StorageSystem storage, String consistencyGroupName, String snapGroupName) {
        CIMObjectPath snapGroup = cimAdapter.getReplicationGroupPath(storage, snapGroupName);
        CIMObjectPath consistencyGroup = cimAdapter.getReplicationGroupPath(storage, consistencyGroupName);
        CIMProperty[] groupSynchronizedKeys = {
                cimPropertyFactory.reference(CP_SYNCED_ELEMENT, snapGroup),
                cimPropertyFactory.reference(CP_SYSTEM_ELEMENT, consistencyGroup)
        };
        return CimObjectPathCreator.createInstance(SE_GROUP_SYNCHRONIZED_RG_RG, cimConnectionFactory.getNamespace(storage),
                groupSynchronizedKeys);
    }

    @Override
    public CIMObjectPath getSyncAspectForSourceGroupPath(StorageSystem storage, String aspectInstanceId) {
        CIMProperty[] syncAspectKeys = {
                cimPropertyFactory.string(CP_INSTANCE_ID, aspectInstanceId)
        };
        return CimObjectPathCreator
                .createInstance(storage.checkIfVmax3() ? SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP
                        : CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP, cimConnectionFactory.getNamespace(storage), syncAspectKeys);
    }

    @Override
    public CIMObjectPath getGroupSynchronizedSettingsPath(StorageSystem storage, String groupName, String settingsInstance) {
        CIMObjectPath group = cimAdapter.getReplicationGroupPath(storage, groupName);
        CIMObjectPath syncAspect = cimAdapter.getSyncAspectForSourceGroupPath(storage, settingsInstance);
        CIMProperty[] settingsKeys = {
                cimPropertyFactory.reference(CP_MANAGED_ELEMENT, group),
                cimPropertyFactory.reference(CP_SETTING_DATA, syncAspect)
        };
        return CimObjectPathCreator.createInstance(storage.checkIfVmax3() ? SYMM_SETTINGS_DEFINE_STATE_RG_SAFS
                : CLAR_SETTINGS_DEFINE_STATE_RG_SAFS, cimConnectionFactory.getNamespace(storage), settingsKeys);
    }

    /**
     * Convenience method to get the CIMObject related to the StorageSystem
     * 
     * @param storage
     *            - StorageSystem object
     * @return CIMObjectPath - path referencing the StorageSystem.
     */
    @Override
    public CIMObjectPath getStorageSystem(StorageSystem storage) {
        String storageSystemClassName = prefixWithParamName(STORAGE_SYSTEM);
        CIMProperty[] properties = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, storageSystemClassName),
                cimPropertyFactory.string(CP_NAME, getSystemName(storage))
        };
        return CimObjectPathCreator.createInstance(storageSystemClassName, cimConnectionFactory.getNamespace(storage), properties);
    }

    @Override
    public CIMObjectPath objectPath(String namespace, String name, CIMProperty[] keys) {
        CIMObjectPath path;
        try {
            path = CimObjectPathCreator.createInstance(name, namespace, keys);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return path;
    }

    @Override
    public CIMObjectPath objectPath(String instanceId) {
        CIMObjectPath path;
        try {
            path = CimObjectPathCreator.createInstance(instanceId);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return path;
    }

    @Override
    public CIMObjectPath getStorageHardwareIDManagementService(StorageSystem storage) {
        String creationClassName = prefixWithParamName(STORAGE_HARDWARE_ID_MGMT_SVC);
        CIMProperty[] properties = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, creationClassName),
                cimPropertyFactory.string(CP_NAME, EMC_STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storage))
        };
        return CimObjectPathCreator.createInstance(creationClassName, cimConnectionFactory.getNamespace(storage), properties);
    }

    @Override
    public CIMObjectPath getPrivilegeManagementService(StorageSystem storage) {
        CIMProperty[] properties = {
                cimPropertyFactory.string(CP_CREATION_CLASS_NAME, CP_CLAR_PRIVILEGE_MGMT_SVC),
                cimPropertyFactory.string(CP_NAME, EMC_PRIVILEGE_MANAGEMENT_SERVICE),
                cimPropertyFactory.string(CP_SYSTEM_CREATION_CLASS_NAME, prefixWithParamName(STORAGE_SYSTEM)),
                cimPropertyFactory.string(CP_SYSTEM_NAME, getSystemName(storage))
        };
        return CimObjectPathCreator.createInstance(CP_CLAR_PRIVILEGE_MGMT_SVC, cimConnectionFactory.getNamespace(storage), properties);
    }

    @Override
    public CIMObjectPath getRemoteReplicationCollection(StorageSystem system,
            RemoteDirectorGroup group) {
        String instanceId = group.getNativeGuid().replace("REMOTEGROUP", "NAME");
        if (system.getUsingSmis80()) {
            instanceId = SmisUtils.translate(system, instanceId.replace(system.getSerialNumber() + "+NAME+", ""));
        }
        CIMProperty[] properties = new CIMProperty[] { cimPropertyFactory.string(CP_INSTANCE_ID, instanceId) };
        return CimObjectPathCreator.createInstance(SE_RemoteReplicationCollection, cimConnectionFactory.getNamespace(system),
                properties);
    }

    @Override
    public CIMObjectPath getReplicationSettingObjectPathFromDefault(CIMInstance settingInstance) {
        CIMProperty<?> instanceIdProp = settingInstance.getProperty(SmisConstants.CP_INSTANCE_ID);
        return CimObjectPathCreator.createInstance(settingInstance
                .getClassName(), Constants.EMC_NAMESPACE,
                new CIMProperty[] { cimPropertyFactory.string(CP_INSTANCE_ID,
                        instanceIdProp.getValue().toString()) });
    }

    @Override
    public CIMObjectPath getMaskingGroupPath(StorageSystem storageDevice, String groupName, MASKING_GROUP_TYPE groupType) throws Exception {
        CIMProperty[] groupKeys = {
                cimPropertyFactory.string(
                        CP_INSTANCE_ID,
                        SmisUtils.translate(storageDevice,
                                prefixWithSystemName(storageDevice.getSerialNumber()).concat("+").concat(groupName))),
        };
        return CimObjectPathCreator.createInstance(groupType.name(),
                cimConnectionFactory.getNamespace(storageDevice), groupKeys);
    }
}
