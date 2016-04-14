/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class AutoTieringPolicyValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        if (null != updateParam.getAutoTieringPolicyName()
                && !updateParam.getAutoTieringPolicyName().equalsIgnoreCase(NONE)
                && !updateParam.getAutoTieringPolicyName().isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        StringSetMap arrayInfo = cos.getArrayInfo();
        if (null == arrayInfo
                || null == arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            if (null == updateParam.getSystemType()
                    || VirtualPool.SystemType.NONE.name()
                            .equalsIgnoreCase(updateParam.getSystemType())) {
                throw APIException.badRequests.missingParameterSystemTypeforAutoTiering();
            }
        }

        if (null != updateParam.getSystemType()) {
            if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(
                            updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxe.toString().equalsIgnoreCase(
                            updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxunity.toString().equalsIgnoreCase(
                            updateParam.getSystemType())
                    && !VirtualPool.SystemType.hds.toString().equalsIgnoreCase(
                            updateParam.getSystemType())) {
                throw APIException.badRequests.invalidParameterSystemTypeforAutoTiering();
            }
            if (!VirtualPoolUtil.isAutoTieringPolicyValidForDeviceType(
                    updateParam.getAutoTieringPolicyName(), updateParam.getSystemType(), dbClient)) {
                throw APIException.badRequests.invalidAutoTieringPolicy();
            }
        }
        else if (null != arrayInfo
                && null != arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            StringSet deviceTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (!deviceTypes.contains(VirtualPool.SystemType.vmax.toString())
                    && !deviceTypes.contains(VirtualPool.SystemType.vnxblock.toString())
                    && !deviceTypes.contains(VirtualPool.SystemType.hds.toString())) {
                throw APIException.badRequests.invalidParameterSystemTypeforAutoTiering();
            }
            for (String deviceType : deviceTypes) {
                if (!VirtualPoolUtil.isAutoTieringPolicyValidForDeviceType(
                        updateParam.getAutoTieringPolicyName(), deviceType, dbClient)) {
                    throw APIException.badRequests.invalidAutoTieringPolicy();
                }
            }
        }
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        if (null == createParam.getSystemType()
                || createParam.getSystemType().equalsIgnoreCase(NONE)) {
            throw APIException.badRequests.missingParameterSystemTypeforAutoTiering();
        }
        if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(createParam.getSystemType())
                && !VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(
                        createParam.getSystemType())
                && !VirtualPool.SystemType.vnxe.toString().equalsIgnoreCase(
                        createParam.getSystemType())
                && !VirtualPool.SystemType.vnxunity.toString().equalsIgnoreCase(
                        createParam.getSystemType())
                && !VirtualPool.SystemType.hds.toString().equalsIgnoreCase(
                        createParam.getSystemType())) {
            throw APIException.badRequests.invalidParameterSystemTypeforAutoTiering();
        }
        if (!VirtualPoolUtil.isAutoTieringPolicyValidForDeviceType(
                createParam.getAutoTieringPolicyName(), createParam.getSystemType(), dbClient)) {
            throw APIException.badRequests.invalidAutoTieringPolicy();
        }
        StringSet systemTypeSet = new StringSet();
        systemTypeSet.add(createParam.getSystemType());
        if (VirtualPoolUtil.validateNullDriveTypeForHDSSystems(createParam.getAutoTieringPolicyName(), systemTypeSet,
                createParam.getDriveType())) {
            throw APIException.badRequests.invalidDriveType(createParam.getSystemType());
        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        if (null != createParam.getAutoTieringPolicyName()
                && !createParam.getAutoTieringPolicyName().equalsIgnoreCase(NONE)) {
            return true;
        }
        return false;
    }
}
