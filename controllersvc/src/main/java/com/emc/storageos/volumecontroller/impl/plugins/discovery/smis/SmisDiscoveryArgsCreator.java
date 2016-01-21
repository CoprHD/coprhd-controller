/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.util.List;
import java.util.Map;
import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.plugins.common.ArgsCreator;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Util;
import com.emc.storageos.plugins.common.domainmodel.Argument;

/**
 * Methods used in Fast Discovery
 * 
 */
public class SmisDiscoveryArgsCreator extends ArgsCreator {
    public SmisDiscoveryArgsCreator(Util util) {
        super(util);
    }

    /**
     * Create SE_DeviceMaskingGroup Object Path for Bourne Generated Device Group Name
     * used in checkDeviceGroupExists Already in Provider.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return CIMObjectPath
     */
    public final Object getStorageDeviceGroupObjectPath(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        // group Name generate and create CIMObjectPath
        String serialID = (String) keyMap.get(Constants._serialID);
        String bourneCreatedDeviceGroup = getBourneCreatedDeviceGroupName(argument,
                keyMap, index).toString();
        return generateDeviceMaskingGroupObjectPath(serialID, bourneCreatedDeviceGroup,
                keyMap);
    }

    /**
     * Create SE_DeviceMaskingGroup Object Path for Bourne Generated Device Group Name, wrapped with
     * CIMArgument, used in DeleteDeviceGroup.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    public final Object getStorageDeviceGroupObjectPathCIMWrapper(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        Object value = getStorageDeviceGroupObjectPath(argument, keyMap, index);
        if (null != value) {
            CIMObjectPath path = (CIMObjectPath) value;
            return new CIMArgument<Object>(argument.getName(),
                    CIMDataType.getDataType(path), value);
        }
        return value;
    }

    /**
     * Get Bourne Created device Group Name from DeviceGroupNames list.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    private final Object getBourneCreatedDeviceGroupName(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        @SuppressWarnings("unchecked")
        List<String> deviceGroupNames = (List<String>) keyMap.get(argument.getValue());
        return deviceGroupNames.get(index);
    }

    /**
     * Generate DeviceMasking Group CIMObject Path
     * 
     * @param serialID
     * @param bourneCreatedDeviceGroup
     * @param keyMap
     * @return
     */
    private CIMObjectPath generateDeviceMaskingGroupObjectPath(
            String serialID, String bourneCreatedDeviceGroup,
            final Map<String, Object> keyMap) {
        @SuppressWarnings("unchecked")
        CIMProperty<?> instanceID = new CIMProperty(Constants.INSTANCEID,
                CIMDataType.STRING_T, Constants.SYMMETRIX_U + Constants.PLUS + serialID
                        + Constants.PLUS + bourneCreatedDeviceGroup, true, false, null);
        CIMProperty<?>[] keys = new CIMProperty<?>[1];
        keys[0] = instanceID;
        CIMObjectPath deviceGroupPath = CimObjectPathCreator.createInstance(
                Constants.SE_DEVICEMASKINGGROUP, keyMap.get(Constants._InteropNamespace)
                        .toString(), keys);
        return deviceGroupPath;
    }

    /**
     * get FAST policy Object path associated with DeviceGroup
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    private final CIMObjectPath getFASTPolicyAssociatedWithDeviceGroup(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        @SuppressWarnings("unchecked")
        List<CIMObjectPath> deviceGroups = (List<CIMObjectPath>) keyMap.get(argument
                .getValue());
        CIMObjectPath deviceGroupPath = deviceGroups.get(index);
        String deviceGroupName = deviceGroupPath.getKey(Constants.INSTANCEID).getValue().toString();
        _logger.debug("Device Group Name associated policy :" + deviceGroupName);
        CIMObjectPath fastPolicyPath = (CIMObjectPath) keyMap.get(deviceGroupName);
        return fastPolicyPath;
    }

    /**
     * get FAST policy Object path associated with DeviceGroup, wrapped as CIMArgument
     * used while adding DeviceGroup to fast Policy
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    public Object getFASTPolicyAssociatedWithDeviceGroupCIMWrapper(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        CIMObjectPath fastPolicyPath = getFASTPolicyAssociatedWithDeviceGroup(argument,
                keyMap, index);
        if (null != fastPolicyPath) {
            return new CIMArgument<Object>(argument.getName(),
                    CIMDataType.getDataType(fastPolicyPath), fastPolicyPath);
        }
        return null;
    }

    /**
     * get vnxPoolCapabilities CIMObject Path from PoolCapabilities-->InitailTierMethodology mapping
     * used in creating a Storage Pool Setting
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    public Object getVnxPoolCapabilitiesToTierMethodInfo(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        @SuppressWarnings("unchecked")
        List<String> vnxPoolCapabilities_tierMethods = (List<String>) keyMap.get(argument
                .getValue());
        String vnxPoolCapabilities_tierMethod = vnxPoolCapabilities_tierMethods
                .get(index);
        String vnxPoolCapabilities = vnxPoolCapabilities_tierMethod.substring(0, vnxPoolCapabilities_tierMethod.lastIndexOf("-"));
        CIMObjectPath vnxPoolCapabilitiesPath = CimObjectPathCreator.createInstance(vnxPoolCapabilities);
        _logger.debug("VNX Pool capabilities found from Capabilities_Tier Mapping :"
                + vnxPoolCapabilitiesPath);
        return vnxPoolCapabilitiesPath;
    }

    /**
     * get Initial Storage Tiering methodology value for given Storage Pool Setting.
     * used while modifying Storage Pool Setting Instance
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    public Object getInitialStorageTierMethodologyValue(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        @SuppressWarnings("unchecked")
        List<CIMInstance> vnxPoolSettingInstances = (List<CIMInstance>) keyMap
                .get(argument.getValue());
        CIMInstance vnxPoolSettingInstance = vnxPoolSettingInstances.get(index);
        String tierMethod = (String) keyMap.get(vnxPoolSettingInstance.getObjectPath()
                .toString() + Constants.HYPHEN + Constants.TIERMETHODOLOGY);
        _logger.debug("Tier Method got from Mapping :" + tierMethod);
        CIMProperty<?> prop = new CIMProperty<Object>(Constants.INITIAL_STORAGE_TIER_METHODOLOGY, CIMDataType.UINT16_T,
                new UnsignedInteger16(tierMethod));
        CIMProperty<?> initialStorageTierSelectionProp = new CIMProperty<Object>(
                Constants.INITIAL_STORAGE_TIERING_SELECTION, CIMDataType.UINT16_T,
                new UnsignedInteger16(Constants.RELATIVE_PERFORMANCE_ORDER));
        CIMProperty<?>[] propArray = new CIMProperty<?>[] { prop, initialStorageTierSelectionProp };
        return propArray;
    }

    public Object getPoolSetting(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        return CimObjectPathCreator.createInstance("");
    }

    /**
     * Add DeviceGroup Paths into CIMObjectPath[], which his being used as value for InElements
     * while invoking ModifyStorageTierPolicyRule.
     * 
     * @param arg
     * @param keyMap
     * @param index
     * @return
     */
    public final Object generateDeviceGroupInElementsCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        @SuppressWarnings("unchecked")
        List<CIMObjectPath> deviceGroupPaths = (List<CIMObjectPath>) keyMap.get(arg
                .getValue());
        CIMObjectPath deviceGroupPath = deviceGroupPaths.get(index);
        CIMObjectPath[] pathArray = new CIMObjectPath[] { deviceGroupPath };
        return new CIMArgument<Object>(arg.getName(),
                CIMDataType.getDataType(pathArray), pathArray);
    }

    /**
     * get Bourne Created Device Group Name , wrapped as CIMArgument
     * used in "CreateGroup" SMI-S call, i.e. creating a new Device Group
     * 
     * @param arg
     * @param keyMap
     * @param index
     * @return
     */
    public final Object getBourneCreatedDeviceGroupNameCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        String value = (String) getBourneCreatedDeviceGroupName(arg, keyMap, index);
        if (null != value) {
            return new CIMArgument<Object>(arg.getName(),
                    CIMDataType.getDataType(value), value);
        }
        return value;
    }

}
