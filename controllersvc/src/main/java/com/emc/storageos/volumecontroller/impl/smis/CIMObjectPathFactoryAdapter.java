/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;

/**
 * This class implements the CIMObjectPathFactory interface and delegates all calls to
 * a dynamic proxy.
 * 
 */
public class CIMObjectPathFactoryAdapter implements CIMObjectPathFactory {
    private final static Logger log = LoggerFactory.getLogger(CIMObjectPathFactoryAdapter.class);

    private CIMObjectPathFactory proxy;
    private InvocationHandler handler;

    public void setHandler(InvocationHandler handler) {
        this.handler = handler;
    }

    /**
     * Initialize
     */
    public void init() {
        log.info("Initializing CIMObjectPathFactory proxy..");
        proxy = (CIMObjectPathFactory) Proxy.newProxyInstance(CIMObjectPathFactory.class.getClassLoader(),
                new Class<?>[] { CIMObjectPathFactory.class }, handler);
    }

    @Override
    public String prefixWithSystemName(String str) {
        return proxy.prefixWithSystemName(str);
    }

    @Override
    public String prefixWithParamName(String str) {
        return proxy.prefixWithParamName(str);
    }

    @Override
    public CIMObjectPath getCimObjectPathFromOutputArgs(CIMArgument[] outputArguments, String key) {
        return proxy.getCimObjectPathFromOutputArgs(outputArguments, key);
    }

    @Override
    public Object getFromOutputArgs(CIMArgument[] outputArguments, String key) {
        return proxy.getFromOutputArgs(outputArguments, key);
    }

    @Override
    public CIMObjectPath[] getProtocolControllersFromOutputArgs(CIMArgument[] outputArguments) {

        return proxy.getProtocolControllersFromOutputArgs(outputArguments);
    }

    @Override
    public CIMObjectPath getElementCompositionSvcPath(StorageSystem storageDevice) {
        return proxy.getElementCompositionSvcPath(storageDevice);
    }

    @Override
    public CIMObjectPath getConfigSvcPath(StorageSystem storageDevice) {
        return proxy.getConfigSvcPath(storageDevice);
    }

    @Override
    public CIMObjectPath getStorageSynchronized(StorageSystem sourceSystem, BlockObject source, StorageSystem targetSystem,
            BlockObject target) {
        return proxy.getStorageSynchronized(sourceSystem, source, targetSystem, target);
    }

    @Override
    public CIMObjectPath getGroupSynchronized(CIMObjectPath sourceGroup, CIMObjectPath targetGroup) {
        return proxy.getGroupSynchronized(sourceGroup, targetGroup);
    }

    @Override
    public CIMObjectPath getStorageGroupObjectPath(String storageGroupName, StorageSystem storage) throws Exception {
        return proxy.getStorageGroupObjectPath(storageGroupName, storage);
    }

    @Override
    public CIMObjectPath getControllerConfigSvcPath(StorageSystem storageDevice) {
        return proxy.getControllerConfigSvcPath(storageDevice);
    }

    @Override
    public CIMObjectPath getTierPolicySvcPath(StorageSystem storageDevice) {
        return proxy.getTierPolicySvcPath(storageDevice);
    }

    @Override
    public CIMObjectPath getTierPolicyRulePath(StorageSystem storageDevice, String policyName) {
        return proxy.getTierPolicyRulePath(storageDevice, policyName);
    }

    @Override
    public CIMObjectPath getControllerReplicationSvcPath(StorageSystem storageDevice) {
        return proxy.getControllerReplicationSvcPath(storageDevice);
    }

    @Override
    public CIMObjectPath getStorageProtectionSvcPath(StorageSystem storageDevice) {
        return proxy.getStorageProtectionSvcPath(storageDevice);
    }

    @Override
    public CIMObjectPath getReplicationServiceCapabilitiesPath(StorageSystem storageDevice) {
        return proxy.getReplicationServiceCapabilitiesPath(storageDevice);
    }

    @Override
    public CIMObjectPath getSeSystemRegistrationService(StorageSystem storage) {
        return proxy.getSeSystemRegistrationService(storage);
    }

    @Override
    public CIMObjectPath[] getClarProtocolControllers(StorageSystem storageDevice, String protocolControllerName) throws Exception {
        return proxy.getClarProtocolControllers(storageDevice, protocolControllerName);
    }

    @Override
    public CIMObjectPath[] getVolumePaths(StorageSystem storageDevice, String[] volumeNames) throws Exception {
        return proxy.getVolumePaths(storageDevice, volumeNames);
    }

    @Override
    public CIMObjectPath[] getTargetPortPaths(StorageSystem storageDevice, List<URI> targetURIList) throws Exception {
        return proxy.getTargetPortPaths(storageDevice, targetURIList);
    }

    @Override
    public CIMObjectPath[] getInitiatorPaths(StorageSystem storageDevice, String[] initiatorNames) throws Exception {
        return proxy.getInitiatorPaths(storageDevice, initiatorNames);
    }

    @Override
    public HashMap<String, CIMObjectPath> getInitiatorToInitiatorPath(StorageSystem storageDevice, List<String> initiatorNames)
            throws Exception {
        return proxy.getInitiatorToInitiatorPath(storageDevice, initiatorNames);
    }

    @Override
    public String getMaskingGroupName(StorageSystem storageDevice, CIMObjectPath groupPath) {
        return proxy.getMaskingGroupName(storageDevice, groupPath);
    }

    @Override
    public CIMObjectPath getMaskingGroupPath(StorageSystem storageDevice, String groupName, MASKING_GROUP_TYPE groupType) throws Exception {
        return proxy.getMaskingGroupPath(storageDevice, groupName, groupType);
    }

    @Override
    public CIMObjectPath getMaskingViewPath(StorageSystem storageDevice, String groupName) {
        return proxy.getMaskingViewPath(storageDevice, groupName);
    }

    @Override
    public CIMObjectPath getLunMaskingProtocolControllerPath(StorageSystem storage, ExportMask exportMask) {
        return proxy.getLunMaskingProtocolControllerPath(storage, exportMask);
    }

    @Override
    public CIMObjectPath getBlockObjectPath(StorageSystem storage, StorageSystem source, BlockObject blockObject) {
        return proxy.getBlockObjectPath(storage, source, blockObject);
    }

    @Override
    public CIMObjectPath getBlockObjectPath(StorageSystem storage, BlockObject blockObject) {
        return proxy.getBlockObjectPath(storage, blockObject);
    }

    @Override
    public CIMObjectPath getVolumePath(StorageSystem storage, String nativeId) {
        return proxy.getVolumePath(storage, nativeId);
    }

    @Override
    public CIMObjectPath getReplicationGroupPath(StorageSystem storage, String groupName) {
        return proxy.getReplicationGroupPath(storage, groupName);
    }

    @Override
    public CIMObjectPath getReplicationGroupPath(StorageSystem activeProviderStorageProxy, String serialNumber, String groupName) {
        return proxy.getReplicationGroupPath(activeProviderStorageProxy, serialNumber, groupName);
    }

    @Override
    public CIMObjectPath getReplicationGroupObjectPath(StorageSystem storage, String instanceId) {
        return proxy.getReplicationGroupObjectPath(storage, instanceId);
    }

    @Override
    public CIMObjectPath getSyncAspectPath(StorageSystem storage, String aspectInstanceId) {
        return proxy.getSyncAspectPath(storage, aspectInstanceId);
    }

    @Override
    public CIMObjectPath getStoragePoolPath(StorageSystem storage, StoragePool storagePool) {
        return proxy.getStoragePoolPath(storage, storagePool);
    }

    @Override
    public CIMObjectPath getPoolSettingPath(StorageSystem storage, String poolSettingID) {
        return proxy.getPoolSettingPath(storage, poolSettingID);
    }

    @Override
    public CIMObjectPath getSyncSettingsPath(StorageSystem storage, CIMObjectPath volumePath, String aspectInstanceId) {
        return proxy.getSyncSettingsPath(storage, volumePath, aspectInstanceId);
    }

    @Override
    public CIMObjectPath getGroupSynchronizedPath(StorageSystem storage, String consistencyGroupName, String snapGroupName) {
        return proxy.getGroupSynchronizedPath(storage, consistencyGroupName, snapGroupName);
    }

    @Override
    public CIMObjectPath getSyncAspectForSourceGroupPath(StorageSystem storage, String aspectInstanceId) {
        return proxy.getSyncAspectForSourceGroupPath(storage, aspectInstanceId);
    }

    @Override
    public CIMObjectPath getGroupSynchronizedSettingsPath(StorageSystem storage, String groupName, String settingsInstance) {
        return proxy.getGroupSynchronizedSettingsPath(storage, groupName, settingsInstance);
    }

    @Override
    public CloseableIterator<CIMObjectPath> getSyncObjects(StorageSystem storage, BlockObject subject) {
        return proxy.getSyncObjects(storage, subject);
    }

    @Override
    public CIMObjectPath getSyncObject(StorageSystem storage, BlockObject subject) {
        return proxy.getSyncObject(storage, subject);
    }

    @Override
    public CIMObjectPath getStorageSystem(StorageSystem storage) {
        return proxy.getStorageSystem(storage);
    }

    @Override
    public String getSystemName(StorageSystem system) {
        return proxy.getSystemName(system);
    }

    @Override
    public String getPoolName(StorageSystem system, String poolID) {
        return proxy.getPoolName(system, poolID);
    }

    @Override
    public String getProcessorName(StorageSystem system, String processorName) {
        return proxy.getProcessorName(system, processorName);
    }

    @Override
    public CIMObjectPath objectPath(String namespace, String name, CIMProperty[] keys) {
        return proxy.objectPath(namespace, name, keys);
    }

    @Override
    public CIMObjectPath objectPath(String instanceId) {
        return proxy.objectPath(instanceId);
    }

    @Override
    public CIMObjectPath getStorageHardwareIDManagementService(StorageSystem storage) {
        return proxy.getStorageHardwareIDManagementService(storage);
    }

    @Override
    public CIMObjectPath getPrivilegeManagementService(StorageSystem storage) {
        return proxy.getPrivilegeManagementService(storage);
    }

    @Override
    public CIMInstance getStoragePoolVdevSettings(CIMObjectPath setting) {
        return proxy.getStoragePoolVdevSettings(setting);
    }

    @Override
    public CIMObjectPath getRemoteReplicationCollection(StorageSystem system, RemoteDirectorGroup group) {
        return proxy.getRemoteReplicationCollection(system, group);
    }

    @Override
    public CIMObjectPath getReplicationSettingObjectPathFromDefault(CIMInstance settingInstance) {
        return proxy.getReplicationSettingObjectPathFromDefault(settingInstance);
    }
}
