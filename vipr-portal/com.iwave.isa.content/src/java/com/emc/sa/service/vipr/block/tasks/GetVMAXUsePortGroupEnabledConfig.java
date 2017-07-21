/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;


import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customconfig.SimpleValueRep;

public class GetVMAXUsePortGroupEnabledConfig extends ViPRExecutionTask<SimpleValueRep> {

    public GetVMAXUsePortGroupEnabledConfig() {
    }

    @Override
    public SimpleValueRep executeTask() throws Exception {
        return getClient().customConfigs().getCustomConfigTypeValue("VMAXUsePortGroupEnabled/value", "vmax");
    }
}