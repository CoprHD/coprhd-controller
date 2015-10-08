/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.util.List;

import com.emc.storageos.model.protection.ProtectionSystemRestRep;

public class GetProtectionSystems extends ViPRExecutionTask<List<ProtectionSystemRestRep>> {
    public GetProtectionSystems() {
    }

    @Override
    public List<ProtectionSystemRestRep> executeTask() throws Exception {
        return getClient().protectionSystems().getAll();
    }

}
