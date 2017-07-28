/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class AddExistingStorageGroupParamType extends ParamType {

    // min/max occurs: 0/unbounded
    private String[] storageGroupId;

    // min/max occurs: 0/1
    private Boolean enableComplianceAlerts;

    public String[] getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String[] storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public void setEnableComplianceAlerts(Boolean enableComplianceAlerts) {
        this.enableComplianceAlerts = enableComplianceAlerts;
    }

}
