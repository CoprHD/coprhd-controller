package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.FILE_POLICY;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("AssociateFilePolicyToFileSystem")
public class AssociateFilePolicyToFileSystemService extends ViPRService {
    
    @Param(PROJECT)
    protected URI project;
    
    @Param(FILESYSTEMS)
    protected URI fileSystem;

    @Param(FILE_POLICY)
    protected URI filePolicy;

    @Override
    public void execute() throws Exception {
        // TODO Auto-generated method stub
        FileStorageUtils.associateFilePolicy(fileSystem, filePolicy);
    }

}
