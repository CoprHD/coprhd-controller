/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.storagedriver.model.Schedule;
import com.emc.storageos.storagedriver.model.ServiceOption;

public class DataProtectionServiceOption extends ServiceOption {

    // The list of capabilities.
    List<CapabilityInstance> capabilities;

    List<Schedule> schedule;



    public DataProtectionServiceOption() {
        this.capabilities = new ArrayList<>();
    }

    public DataProtectionServiceOption(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}

