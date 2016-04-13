/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.RaidLevel;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class RaidLevelValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, BlockVirtualPoolUpdateParam param, DbClient dbClient) {
        for (String raidLevel : param.getRaidLevelChanges().getAdd().getRaidLevels()) {
            if (null == RaidLevel.lookup(raidLevel)) {
                throw APIException.badRequests.invalidParameter("raidLevel", raidLevel);
            }
        }
        StringSetMap arrayInfo = cos.getArrayInfo();

        /** if system Type is null , throw exception */
        if (null == arrayInfo
                || null == arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)
                || arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE).contains(
                        VirtualPool.SystemType.NONE.toString())) {
            if (null == param.getSystemType()
                    || VirtualPool.SystemType.NONE.name().equalsIgnoreCase(param.getSystemType())) {
                throw APIException.badRequests.mandatorySystemTypeRaidLevels();
            }
        }
        /**
         * if null != systemType, if deviceType given as part of param, then if other than vmax or vnxblock
         * throw Exception
         * if VirtualPool system Type is null, then if deviceType is not given as part of param, then if already
         * device Type
         * exists in DB, then if its other than vmax or vnxblock throw Exception
         */
        if (null != param.getSystemType()) {
            if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(param.getSystemType())
                    && !VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(
                            param.getSystemType())
                    && !VirtualPool.SystemType.vnxe.toString().equalsIgnoreCase(
                            param.getSystemType())
                    && !VirtualPool.SystemType.vnxunity.toString().equalsIgnoreCase(
                            param.getSystemType())) {
                throw APIException.badRequests.virtualPoolSupportsVmaxVnxblockWithRaid();
            }
        }
        /**
         * if system Type already exists in DB, then check whether device Type is vmax or vnxblock
         */
        else if (null != arrayInfo
                && null != arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            StringSet deviceTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (!deviceTypes.contains(VirtualPool.SystemType.vmax.toString())
                    && !deviceTypes.contains(VirtualPool.SystemType.vnxblock.toString())
                    && !deviceTypes.contains(VirtualPool.SystemType.vnxe.toString())
                    && !deviceTypes.contains(VirtualPool.SystemType.vnxunity.toString())) {
                throw APIException.badRequests.virtualPoolSupportsVmaxVnxblockWithRaid();
            }
        }
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam param) {
        if (null == param.getRaidLevelChanges() || null == param.getRaidLevelChanges().getAdd()
                || null == param.getRaidLevelChanges().getAdd().getRaidLevels()) {
            return false;
        }
        return true;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        for (String raidLevel : createParam.getRaidLevels()) {
            if (null == RaidLevel.lookup(raidLevel)) {
                throw APIException.badRequests.invalidParameter("Raid Level", raidLevel);
            }
        }
        if (null == createParam.getSystemType()) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("System Type");
        }
        if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(createParam.getSystemType())
                && !VirtualPool.SystemType.vnxblock.toString().equalsIgnoreCase(
                        createParam.getSystemType())
                && !VirtualPool.SystemType.vnxe.toString().equalsIgnoreCase(
                        createParam.getSystemType())
                && !VirtualPool.SystemType.vnxunity.toString().equalsIgnoreCase(
                        createParam.getSystemType())) {
            throw APIException.badRequests.parameterOnlySupportedForVmaxAndVnxBlock("Raid Levels");
        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        if (null != createParam.getRaidLevels() && !createParam.getRaidLevels().isEmpty()) {
            return true;
        }
        return false;
    }
}
