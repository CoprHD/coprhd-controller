/*
 * Copyright (c) 2105 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.PROTECTION_SYSTEMS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.GetUnmanagedCGsForProtectionSystem;
import com.emc.sa.service.vipr.tasks.DiscoverUnmanagedCGs;
import com.emc.sa.service.vipr.tasks.GetProtectionSystems;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;

@Service("DiscoverUnmanagedCGs")
public class DiscoverUnmanagedCGsService extends ViPRService {
    @Param(PROTECTION_SYSTEMS)
    protected List<String> protectionSystems;

    @Override
    public void execute() throws Exception {

        List<URI> uris = uris(protectionSystems);

        List<ProtectionSystemRestRep> systemRestReps =
                execute(new GetProtectionSystems(uris));

        for (ProtectionSystemRestRep protectionSystem : systemRestReps) {

            logInfo("discover.unmanaged.cg.service.discovering", protectionSystem.getName());

            execute(new DiscoverUnmanagedCGs(protectionSystem.getId().toString(), DiscoverUnmanagedCGs.UnmanagedNamespace.UNMANAGED_CGS));

            int postCount = countUnmanagedCGs(protectionSystem.getId().toString());
            logInfo("discover.unmanaged.cg.service.discovered", postCount, protectionSystem.getName());

        }
    }

    private int countUnmanagedCGs(String protectionSystem) {
        int total = 0;

        List<RelatedResourceRep> unmanaged =
                execute(new GetUnmanagedCGsForProtectionSystem(protectionSystem));
        if (unmanaged != null) {
            total = unmanaged.size();
        }
        return total;
    }
}
