package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FAILOVER_TARGET_FILE;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("FailoverFileSystem")
public class FailoverFileSystemService extends ViPRService {

    @Param(FAILOVER_TARGET_FILE)
    protected URI failoverTargetFile;
    
    @Override
    public void execute() throws Exception {
        // TODO Auto-generated method stub

    }

}
