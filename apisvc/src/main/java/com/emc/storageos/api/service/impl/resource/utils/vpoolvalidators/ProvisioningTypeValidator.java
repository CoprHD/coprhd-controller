/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.ProvisioningType;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ProvisioningTypeValidator extends VirtualPoolValidator<VirtualPoolCommonParam, VirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, VirtualPoolUpdateParam updateParam, DbClient dbClient) {
        if (null == ProvisioningType.lookup(updateParam.getProvisionType())) {
            throw APIException.badRequests.invalidParameter("provisionType",
                    updateParam.getProvisionType());
        }
    }

    @Override
    protected boolean isUpdateAttributeOn(VirtualPoolUpdateParam updateParam) {
        if (null != updateParam.getProvisionType()
                && !updateParam.getProvisionType().equalsIgnoreCase(NONE)) {
            return true;
        }
        return false;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(VirtualPoolCommonParam createParam, DbClient dbClient) {
        if (null == ProvisioningType.lookup(createParam.getProvisionType())) {
            throw APIException.badRequests.invalidParameter("provisionType",
                    createParam.getProvisionType());
        }
    }

    @Override
    protected boolean isCreateAttributeOn(VirtualPoolCommonParam createParam) {
        if (null != createParam.getProvisionType()
                && !createParam.getProvisionType().equalsIgnoreCase(NONE)) {
            return true;
        }
        return false;
    }
}
