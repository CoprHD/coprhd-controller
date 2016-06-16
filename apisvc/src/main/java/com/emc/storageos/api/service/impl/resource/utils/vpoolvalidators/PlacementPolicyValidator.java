/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.ResourcePlacementPolicyType;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class PlacementPolicyValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool vPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        StringSet systemTypes = null;
        StringSetMap arrayInfo = vPool.getArrayInfo();
        if (arrayInfo != null && !arrayInfo.isEmpty()) {
            systemTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
        }
        validatePlacementPolicy(updateParam.getPlacementPolicy(), updateParam.getSystemType(), systemTypes);
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        return updateParam.getPlacementPolicy() != null;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        validatePlacementPolicy(createParam.getPlacementPolicy(), createParam.getSystemType(), null);
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        return createParam.getPlacementPolicy() != null;
    }

    private void validatePlacementPolicy(String policyName, String system, StringSet systems) {
        ResourcePlacementPolicyType policyType = ResourcePlacementPolicyType.lookup(policyName);
        if (policyType == null) {
            throw APIException.badRequests.unsupportedPlacementPolicy(policyName);
        }

        // validate system type is vmax if aray_affinity is used. Array affinity is only supported for VMAX arrays
        if (policyType.equals(ResourcePlacementPolicyType.array_affinity)) {
            if (system != null && !VirtualPool.SystemType.NONE.name().equals(system)) {
                SystemType systemType = SystemType.lookup(system);
                if (systemType != null && !systemType.equals(SystemType.vmax)) {
                    throw APIException.badRequests.unsupportedPlacementPolicy(policyName);
                }
            }

            if (systems != null && !systems.isEmpty() && !systems.contains(VirtualPool.SystemType.NONE.name())) {
                if (!systems.contains(SystemType.vmax.name())) {
                    throw APIException.badRequests.unsupportedPlacementPolicy(policyName);
                }
            }
        }
    }
}
