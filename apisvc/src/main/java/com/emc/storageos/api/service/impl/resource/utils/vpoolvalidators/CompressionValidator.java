/*
 * Copyright (c) 2017 Dell EMC
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

/**
 * Compression is supported only for VMAX3 All Flash Arrays.
 * Compression can be enabled only on FAST managed Storage groups.
 */
public class CompressionValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        return true; // return always true
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        if (cos.getCompressionEnabled() || Boolean.TRUE.equals(updateParam.getCompressionEnabled())) {
            StringSetMap arrayInfo = cos.getArrayInfo();
            if (null == arrayInfo
                    || null == arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                if (null == updateParam.getSystemType()
                        || VirtualPool.SystemType.NONE.name().equalsIgnoreCase(updateParam.getSystemType())) {
                    throw APIException.badRequests.missingParameterSystemTypeforCompression();
                }
            }

            if (null != updateParam.getSystemType()) {
                if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(updateParam.getSystemType())) {
                    throw APIException.badRequests.invalidParameterSystemTypeforCompression();
                }
            } else if (null != arrayInfo
                    && null != arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                StringSet deviceTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
                if (!deviceTypes.contains(VirtualPool.SystemType.vmax.toString())) {
                    throw APIException.badRequests.invalidParameterSystemTypeforCompression();
                }
            }

            if (null == cos.getAutoTierPolicyName() || cos.getAutoTierPolicyName().equalsIgnoreCase(NONE)) {
                if (null == updateParam.getAutoTieringPolicyName() || updateParam.getAutoTieringPolicyName().isEmpty()
                        || updateParam.getAutoTieringPolicyName().equalsIgnoreCase(NONE)) {
                    throw APIException.badRequests.invalidParameterAutoTieringPolicyforCompression();
                }
            }
        }
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        if (null == createParam.getSystemType()
                || createParam.getSystemType().equalsIgnoreCase(NONE)) {
            throw APIException.badRequests.missingParameterSystemTypeforCompression();
        }

        if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(createParam.getSystemType())) {
            throw APIException.badRequests.invalidParameterSystemTypeforCompression();
        }

        if (null == createParam.getAutoTieringPolicyName()
                || createParam.getAutoTieringPolicyName().equalsIgnoreCase(NONE)) {
            throw APIException.badRequests.invalidParameterAutoTieringPolicyforCompression();
        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        return Boolean.TRUE.equals(createParam.getCompressionEnabled());
    }

}
