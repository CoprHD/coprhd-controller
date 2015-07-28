/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.VirtualPoolService;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ThinVolumePreAllocationValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        String provisionType = updateParam.getProvisionType() != null ? updateParam.getProvisionType() : cos.getSupportedProvisioningType();
        String systemType = updateParam.getSystemType() != null ? updateParam.getSystemType() : VirtualPoolService.getSystemType(cos);

        validateVMaxThinVolumePreAllocateParam(provisionType, systemType,
                updateParam.getThinVolumePreAllocationPercentage());
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        return (null != updateParam.getThinVolumePreAllocationPercentage());
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        validateVMaxThinVolumePreAllocateParam(createParam.getProvisionType(), createParam.getSystemType(),
                createParam.getThinVolumePreAllocationPercentage());
    }

    /**
     * Validates VMAX Thin volume preallocate param.
     */
    private void validateVMaxThinVolumePreAllocateParam(String provisionType, String systemType, Integer thinVolumePreAllocationPercentage) {
        if (!VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(provisionType) &&
                thinVolumePreAllocationPercentage > 0) {
            throw APIException.badRequests.thinVolumePreallocationPercentageOnlyApplicableToThin();
        }
        if (VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(systemType) &&
                thinVolumePreAllocationPercentage > 0) {
            throw APIException.badRequests.thinVolumePreallocationPercentageOnlyApplicableToVMAX();
        }
        if (null != thinVolumePreAllocationPercentage && thinVolumePreAllocationPercentage < 0 || thinVolumePreAllocationPercentage > 100) {
            throw APIException.badRequests.invalidParameterPercentageExpected(
                    "thin_volume_preallocation_percentage", thinVolumePreAllocationPercentage);
        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        if (null != createParam.getThinVolumePreAllocationPercentage()) {
            return true;
        }
        return false;
    }
}
