/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.RelatedResourceRep;

public class GetUnmanagedCGsForProtectionSystem extends ViPRExecutionTask<List<RelatedResourceRep>> {
    private URI protectionSystem;

    public GetUnmanagedCGsForProtectionSystem(String protectionSystem) {
        this(uri(protectionSystem));
    }

    public GetUnmanagedCGsForProtectionSystem(URI protectionSystem) {
        this.protectionSystem = protectionSystem;
        provideDetailArgs(protectionSystem);
    }

    @Override
    public List<RelatedResourceRep> executeTask() throws Exception {
        return getClient().unmanagedCGs().listByProtectionSystem(protectionSystem);
    }
}
