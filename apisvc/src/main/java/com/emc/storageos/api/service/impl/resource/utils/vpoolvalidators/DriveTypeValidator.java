/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SupportedDriveTypes;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class DriveTypeValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;

    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool vPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        if (VirtualPoolUtil.validateNullDriveTypeForHDSSystems(vPool.getAutoTierPolicyName(),
                vPool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE), vPool.getDriveType())) {
            throw APIException.badRequests.invalidDriveType(vPool.getArrayInfo().get(
                    VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE).toString());
        }
        if (null != updateParam.getDriveType() && !updateParam.getDriveType().equalsIgnoreCase(NONE)
                && null == SupportedDriveTypes.lookup(updateParam.getDriveType())) {
            throw APIException.badRequests.invalidParameter("driveType", updateParam.getDriveType());
        }
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        // validate DriveType always.
        return true;

    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(
            BlockVirtualPoolParam createParam, DbClient dbClient) {
        if (null == SupportedDriveTypes.lookup(createParam.getDriveType())) {
            throw APIException.badRequests.invalidParameter("driveType", createParam.getDriveType());
        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        if (null != createParam.getDriveType() && !createParam.getDriveType().equalsIgnoreCase(NONE)) {
            return true;
        }
        return false;
    }
}
