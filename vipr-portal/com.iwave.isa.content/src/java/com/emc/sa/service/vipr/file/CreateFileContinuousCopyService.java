package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.FILESYSTEM;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateFileContinuousCopy")
public class CreateFileContinuousCopyService extends ViPRService {

    @Param(FILESYSTEM)
    protected URI fileSystem;
    
    @Param(NAME)
    protected URI name;
    
    @Override
    public void execute() throws Exception {
        // TODO Auto-generated method stub

    }

}
