package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.TARGET_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.FILESYSTEMS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.file.tasks.ChangeFileVirtualPool;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;

@Service("ChangeFileVirtualPool")
public class ChangeFileVirtualPoolService  extends ViPRService {

    @Param(TARGET_VIRTUAL_POOL)
    protected URI targetVirtualPool;
    
    @Param(FILESYSTEMS)
    protected String fileId;
    
    @Override
    public void execute() throws Exception {
        FileStorageUtils.changeFileVirtualPool(uri(fileId), targetVirtualPool);
    }
}
