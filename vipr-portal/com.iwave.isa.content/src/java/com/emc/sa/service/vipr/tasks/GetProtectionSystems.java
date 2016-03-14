/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.protection.ProtectionSystemRestRep;

public class GetProtectionSystems extends ViPRExecutionTask<List<ProtectionSystemRestRep>> {
    private List<URI> protectionSystems;

    public GetProtectionSystems() {
        protectionSystems = null;
    }

    public GetProtectionSystems(List<URI> protectionSystems) {
        this.protectionSystems = protectionSystems;
        provideDetailArgs(protectionSystems);
    }

    @Override
    public List<ProtectionSystemRestRep> executeTask() throws Exception {
        if (protectionSystems == null) {
            return getClient().protectionSystems().getAll();
        }
        return getClient().protectionSystems().getByIds(this.protectionSystems);        
    }

}
