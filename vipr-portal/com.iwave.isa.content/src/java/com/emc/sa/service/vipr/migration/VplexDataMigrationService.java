package com.emc.sa.service.vipr.migration;

import java.net.URI;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ChangeBlockVolumeVirtualPool;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("VplexDataMigration")
public class VplexDataMigrationService extends ViPRService {

    @Param(ServiceParams.PROJECT)
    private URI projectId;

    @Param(ServiceParams.VOLUME)
    private List<String> volumeIds;

    @Param(ServiceParams.TARGET_VIRTUAL_POOL)
    private URI targetVirtualPool;

    @Param(value = ServiceParams.CONSISTENCY_GROUP, required = false)
    private URI consistencyGroup;

    @Param(value = ServiceParams.MIGRATION_SUSPEND, required = false)
    private Boolean migrationSuspend;

    @Param(value = ServiceParams.DISPLAY_JOURNALS, required = false)
    protected String displayJournals;

    @Override
    public void execute() throws Exception {
        boolean forceFlag = BlockProvider.YES_VALUE.equalsIgnoreCase(displayJournals);
        Tasks<VolumeRestRep> tasks = execute(new ChangeBlockVolumeVirtualPool(uris(volumeIds), targetVirtualPool, consistencyGroup, null, migrationSuspend, forceFlag));
        addAffectedResources(tasks);
    }
}
