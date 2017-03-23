/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.DELETION_TYPE;
import static com.emc.sa.service.ServiceParams.FILESYSTEMS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.volumecontroller.FileControllerConstants;

public class DeleteFileSystemHelper {
    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;

    private List<FileShareRestRep> fileSystems;
    
    @Param(value = DELETION_TYPE, required = false)
    protected FileControllerConstants.DeleteTypeEnum fileDeletionType;

    public void precheck() {
        fileSystems = FileStorageUtils.getFileSystems(ViPRExecutionTask.uris(fileSystemIds));
        for(FileShareRestRep fileSystem : fileSystems){
            if( fileSystem.getProtection().getPersonality() != null && fileSystem.getProtection().getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())){
                ExecutionUtils.fail("failTask.DeleteFileSystemService", fileSystem.getName(), fileSystem.getName());
            }
        }
    }

    public void deleteFileSystems() {
        if (fileDeletionType == null) {
            fileDeletionType = FileControllerConstants.DeleteTypeEnum.FULL;
        }
        
        for (FileShareRestRep fs : fileSystems) {
            URI fsId = fs.getId();
            FileStorageUtils.deleteFileSystem(fsId, fileDeletionType);
        }
    }
}
