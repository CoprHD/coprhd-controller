package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILE_COPIES;
import static com.emc.sa.service.ServiceParams.FILESYSTEM;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RemoveFileContinuousCopy")
public class RemoveFileContinuousCopyService extends ViPRService {
    
    @Param(FILESYSTEM)
    protected String fileId;
    
    @Param(FILE_COPIES)
    protected List<URI> fileCopies;
    
    @Override
    public void execute() throws Exception {
        // TODO Auto-generated method stub
        FileStorageUtils.removeContinuousCopiesForFile(uri(fileId), fileCopies);
    }
}
