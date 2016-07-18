/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.Map;

import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.VirtualMachine;
import com.emc.storageos.model.host.VirtualMachineParam;
import com.google.common.collect.Maps;

public abstract class VirtualMachineConnectionValidator {

    protected static Map<HostType, VirtualMachineConnectionValidator> validators = Maps.newHashMap();

    protected static void addValidator(VirtualMachineConnectionValidator hostConnectionValidator) {
        validators.put(hostConnectionValidator.getType(), hostConnectionValidator);
    }

    public static boolean isVMConnectionValid(VirtualMachineParam vmParam, VirtualMachine existingVM) {
        HostType hostType = HostType.valueOf(vmParam.getType());

        VirtualMachineConnectionValidator vmConnectionValidator = validators.get(hostType);
        if (vmConnectionValidator != null) {
            return vmConnectionValidator.validateConnection(vmParam, existingVM);
        }

        return true;
    }

    public abstract HostType getType();

    public abstract boolean validateConnection(VirtualMachineParam vmParam, VirtualMachine existingVM);

}
