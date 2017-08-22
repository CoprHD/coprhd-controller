/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.CreateStorageEmulationType;
import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class AddNewStorageGroupParamType extends ParamType {

    // min/max occurs: 0/1
    private String srpId;
    // min/max occurs: 0/unbounded
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;
    // min/max occurs: 0/1
    private CreateStorageEmulationType emulation;
    // min/max occurs: 0/1
    private Boolean enableComplianceAlerts;

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }

    public void setEmulation(CreateStorageEmulationType emulation) {
        this.emulation = emulation;
    }

    public void setEnableComplianceAlerts(Boolean enableComplianceAlerts) {
        this.enableComplianceAlerts = enableComplianceAlerts;
    }
}
