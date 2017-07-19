/*
 * Copyright (c) 2017 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.FILESYSTEM_TRGT_VARRAY;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEMS;
import static com.emc.sa.service.ServiceParams.TYPE;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.file.tasks.GetUnmanagedFilesystems;
import com.emc.sa.service.vipr.file.tasks.IngestUnmanagedFilesystems;
import com.emc.sa.service.vipr.tasks.CheckStorageSystemDiscoveryStatus;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;

@Service("IngestUnmanagedProtectedFilesystems")
public class IngestUnmanagedProtectedFilesystemService extends ViPRService {

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(STORAGE_SYSTEMS)
    protected URI storageSystem;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(PROJECT)
    protected URI project;

    @Param(FILESYSTEM_TRGT_VARRAY)
    protected URI targetVirtualArray;

    @Param(value = TYPE, required = false)
    protected String type;

    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        execute(new CheckStorageSystemDiscoveryStatus(storageSystem));
    }

    @Override
    public void execute() throws Exception {
        List<UnManagedFileSystemRestRep> unmanaged = execute(new GetUnmanagedFilesystems(storageSystem, virtualPool, type));

        execute(new IngestUnmanagedFilesystems(virtualPool, virtualArray, project, uris(fileSystemIds)));

        // Requery and produce a log of what was ingested or not
        int failed = execute(new GetUnmanagedFilesystems(storageSystem, virtualPool, type)).size();
        logInfo("ingest.unmanaged.filesystems.service.ingested", unmanaged.size() - failed);
        logInfo("ingest.unmanaged.filesystems.service.skipped", failed);
    }
}
