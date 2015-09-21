/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;

/**
 * Created by bibbyi1 on 3/3/2015.
 */
public interface CIMObjectPathFactory extends SmisConstants {
    String prefixWithSystemName(String str);

    String prefixWithParamName(String str);

    CIMObjectPath getCimObjectPathFromOutputArgs(CIMArgument[] outputArguments, String key);

    Object getFromOutputArgs(CIMArgument[] outputArguments, String key);

    CIMObjectPath[] getProtocolControllersFromOutputArgs(CIMArgument[] outputArguments);

    CIMObjectPath getElementCompositionSvcPath(StorageSystem storageDevice);

    CIMObjectPath getConfigSvcPath(StorageSystem storageDevice);

    CIMObjectPath getStorageSynchronized(StorageSystem sourceSystem, BlockObject source, StorageSystem targetSystem, BlockObject target);

    CIMObjectPath getGroupSynchronized(CIMObjectPath sourceGroup, CIMObjectPath targetGroup);

    CIMObjectPath getStorageGroupObjectPath(String storageGroupName, StorageSystem storage) throws Exception;

    CIMObjectPath getControllerConfigSvcPath(StorageSystem storageDevice);

    CIMObjectPath getTierPolicySvcPath(StorageSystem storageDevice);

    CIMObjectPath getTierPolicyRulePath(StorageSystem storageDevice, String policyName);

    CIMObjectPath getControllerReplicationSvcPath(StorageSystem storageDevice);

    CIMObjectPath getStorageProtectionSvcPath(StorageSystem storageDevice);

    CIMObjectPath getReplicationServiceCapabilitiesPath(StorageSystem storageDevice);

    CIMObjectPath getSeSystemRegistrationService(StorageSystem storage);

    CIMObjectPath[] getClarProtocolControllers(StorageSystem storageDevice, String protocolControllerName) throws Exception;

    CIMObjectPath[] getVolumePaths(StorageSystem storageDevice, String[] volumeNames) throws Exception;

    CIMObjectPath[] getTargetPortPaths(StorageSystem storageDevice, List<URI> targetURIList) throws Exception;

    CIMObjectPath[] getInitiatorPaths(StorageSystem storageDevice, String[] initiatorNames) throws Exception;

    /**
     * Gets the map of initiator to initiator path.
     * 
     * @param storageDevice reference to storage system
     * @param initiatorNames list of initiator names
     * @return map of initiator to initiatorPath
     * @throws Exception
     */
    HashMap<String, CIMObjectPath> getInitiatorToInitiatorPath(StorageSystem storageDevice, List<String> initiatorNames)
            throws Exception;

    String getMaskingGroupName(StorageSystem storageDevice, CIMObjectPath groupPath);

    CIMObjectPath getMaskingGroupPath(StorageSystem storageDevice, String groupName, MASKING_GROUP_TYPE groupType) throws Exception;

    CIMObjectPath getMaskingViewPath(StorageSystem storageDevice, String groupName);

    CIMObjectPath getLunMaskingProtocolControllerPath(StorageSystem storage,
            ExportMask exportMask);

    CIMObjectPath getBlockObjectPath(StorageSystem storage, StorageSystem source, BlockObject blockObject);

    CIMObjectPath getBlockObjectPath(StorageSystem storage, BlockObject blockObject);

    CIMObjectPath getVolumePath(StorageSystem storage, String nativeId);

    CIMObjectPath getReplicationGroupPath(StorageSystem storage, String groupName);

    /**
     * Gets the replication group path.
     * 
     * @param activeProviderStorageProxy can be a proxy StorageSystem that is used only to reference an active StorageProvider
     *            to lookup a replication group for the array referenced by serialNumber.
     * @param serialNumber the serial number of the storage system which has this group
     * @param groupName the Replication group name
     * @return the replication group path
     */
    CIMObjectPath getReplicationGroupPath(StorageSystem activeProviderStorageProxy, String serialNumber, String groupName);

    CIMObjectPath getReplicationGroupObjectPath(StorageSystem storage, String instanceId);

    CIMObjectPath getSyncAspectPath(StorageSystem storage, String aspectInstanceId);

    CIMObjectPath getStoragePoolPath(StorageSystem storage, StoragePool storagePool);

    CIMObjectPath getPoolSettingPath(StorageSystem storage, String poolSettingID);

    CIMObjectPath getSyncSettingsPath(StorageSystem storage, CIMObjectPath volumePath, String aspectInstanceId);

    CIMObjectPath getGroupSynchronizedPath(StorageSystem storage, String consistencyGroupName, String snapGroupName);

    CIMObjectPath getSyncAspectForSourceGroupPath(StorageSystem storage, String aspectInstanceId);

    CIMObjectPath getGroupSynchronizedSettingsPath(StorageSystem storage, String groupName, String settingsInstance);

    CloseableIterator<CIMObjectPath> getSyncObjects(StorageSystem storage, BlockObject subject);

    CIMObjectPath getSyncObject(StorageSystem storage, BlockObject subject);

    CIMObjectPath getStorageSystem(StorageSystem storage);

    String getSystemName(StorageSystem system);

    String getPoolName(StorageSystem system, String poolID);

    // TODO: check if this method is used at all if not delete it
    String getProcessorName(StorageSystem system, String processorName);

    CIMObjectPath objectPath(String namespace, String name, CIMProperty[] keys);

    CIMObjectPath objectPath(String instanceId);

    CIMObjectPath getStorageHardwareIDManagementService(StorageSystem storage);

    CIMObjectPath getPrivilegeManagementService(StorageSystem storage);

    CIMInstance getStoragePoolVdevSettings(CIMObjectPath setting);

    CIMObjectPath getRemoteReplicationCollection(StorageSystem system,
            RemoteDirectorGroup group);

    CIMObjectPath getReplicationSettingObjectPathFromDefault(CIMInstance settingInstance);
}
