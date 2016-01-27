/*
 * Copyright (c) 2012-2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.FILESYSTEM;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

@Service("CreateFileContinuousCopy")
public class CreateFileContinuousCopyService extends ViPRService {

    @Param(FILESYSTEM)
    protected URI fileSystem;
    
    @Param(NAME)
    protected String name;
    
    @Override
    public void execute() throws Exception {
        //Task<FileShareRestRep> copy = FileStorageUtils.createFileContinuousCopy(fileSystem, name);
        FileStorageUtils.createFileContinuousCopy(fileSystem, name);
    }

}
