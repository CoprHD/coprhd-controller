/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import org.apache.commons.collections.CollectionUtils;

public class HostIOLimitValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {
    private Integer MAX_HOST_IO_LIMIT_BANDWIDTH = 100000;
    private Integer MAX_HOST_IO_LIMIT_IOPS = 999900;

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        return updateParam.isHostIOLimitBandwidthSet() || updateParam.isHostIOLimitIOPsSet();
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
                throw APIException.badRequests.missingParameterSystemTypeforHostIOLimits();
            }
        }

        // Any driver managed type can support host io limits: return if driver type is in update param,
        // or driver type is already in vpool system type collection.
        if (null != updateParam.getSystemType() &&
                getStorageDriverManager().isDriverManaged(updateParam.getSystemType())) {
            return;
        } else if (null != arrayInfo
                && null != arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            StringSet deviceTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (CollectionUtils.containsAny(deviceTypes, getStorageDriverManager().getBlockSystems())) {
                return;
            }
        }

        if (null != updateParam.getSystemType()) {
            if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(updateParam.getSystemType())) {
                throw APIException.badRequests.invalidParameterSystemTypeforHostIOLimits();
            }
        } else if (null != arrayInfo
                && null != arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            StringSet deviceTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (!deviceTypes.contains(VirtualPool.SystemType.vmax.toString())) {
                throw APIException.badRequests.invalidParameterSystemTypeforHostIOLimits();
            }
        }

        validHostIOLimits(updateParam.getHostIOLimitBandwidth(), updateParam.getHostIOLimitIOPs());
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        if (null == createParam.getSystemType()
                || createParam.getSystemType().equalsIgnoreCase(NONE)) {
            throw APIException.badRequests.missingParameterSystemTypeforHostIOLimits();
        }

        // Any driver managed type can support host io limits
        if (getStorageDriverManager().isDriverManaged(createParam.getSystemType())) {
            return;
        }

        if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(createParam.getSystemType())) {
            throw APIException.badRequests.invalidParameterSystemTypeforHostIOLimits();
        }

        validHostIOLimits(createParam.getHostIOLimitBandwidth(), createParam.getHostIOLimitIOPs());

    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        return createParam.isHostIOLimitBandwidthSet() || createParam.isHostIOLimitIOPsSet();
    }

    private void validHostIOLimits(Integer limitBandwidth, Integer limitIops) {
        // if specified, iops value must a positive number and it should be multiple of 100
        if (limitIops != null && (limitIops < 0 || limitIops % 100 != 0 || limitIops > MAX_HOST_IO_LIMIT_IOPS)) {
            throw APIException.badRequests.invalidParameterValueforHostIOLimitIOPs();
        }

        // if specified, bandwidth value must a positive number and it must <= MAX_HOST_IO_LIMIT_BANDWIDTH
        if (limitBandwidth != null && (limitBandwidth < 0 || limitBandwidth > MAX_HOST_IO_LIMIT_BANDWIDTH)) {
            throw APIException.badRequests.invalidParameterValueforHostIOLimitBandwidth();
        }
    }
}
