/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class SystemTypeValidator extends VirtualPoolValidator<VirtualPoolCommonParam, VirtualPoolUpdateParam> {

    private boolean compareSystemTypes(
            StringSet systemTypes, StringSet availableSystemTypes) {
        for (String systemType : systemTypes) {
            if (!availableSystemTypes.contains(systemType)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool vPool, VirtualPoolUpdateParam updateParam, DbClient dbClient) {
        if (!storageDriverManager.isDriverManaged(updateParam.getSystemType()) &&
                null == SystemType.lookup(updateParam.getSystemType())) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("System Type");
        }

        if (null != vPool.getAutoTierPolicyName()
                && !NONE.equalsIgnoreCase(vPool.getAutoTierPolicyName())) {
            if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(
                            updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxe.toString().equalsIgnoreCase(updateParam.getSystemType())) {
                throw APIException.badRequests.invalidParameterSystemTypeforAutoTiering();
            }
        }

        if (isRaidLevelAvailable(vPool)) {
            if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(
                            updateParam.getSystemType())
                    && !VirtualPool.SystemType.vnxe.toString().equalsIgnoreCase(
                            updateParam.getSystemType())) {
                throw APIException.badRequests.virtualPoolSupportsVmaxVnxblockWithRaid();
            }
        }
    }

    private boolean isRaidLevelAvailable(VirtualPool virtualPool) {
        boolean status = false;
        if (virtualPool != null && virtualPool.getArrayInfo() != null) {
            StringSet raidLevelSet = virtualPool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL);
            if (raidLevelSet != null && !raidLevelSet.isEmpty()) {
                status = true;
            }
        }
        return status;
    }

    @Override
    protected boolean isUpdateAttributeOn(VirtualPoolUpdateParam updateParam) {
        return (null != updateParam.getSystemType());
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(VirtualPoolCommonParam createParam, DbClient dbClient) {
        if (!storageDriverManager.isDriverManaged(createParam.getSystemType()) &&
        null == SystemType.lookup(createParam.getSystemType())) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("System Type");
        }
    }

    @Override
    protected boolean isCreateAttributeOn(VirtualPoolCommonParam createParam) {
        if (null != createParam.getSystemType()
                && !createParam.getSystemType().equalsIgnoreCase(NONE)) {
            return true;
        }
        return false;
    }
}
