/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration;


import com.emc.storageos.db.client.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bonduj on 8/1/2016.
 */
public class IngestFileStrategyFactory {
    private static final Logger _logger = LoggerFactory.getLogger(IngestFileStrategyFactory.class);
    private DbClient _dbClient;

    private FileIngestOrchestrator _fileIngestOrchestrator;
    private FileIngestOrchestrator _mirrorFileIngestOrchestrator;
    private FileIngestOrchestrator _snapFileIngestOrchestrator;
    private FileSnapIngestOrchestrator fileSnapIngestOrchestrator;
    private FileMirrorIngestOrchestrator fileMirrorIngestOrchestrator;
    private FileSystemIngestOrchestrator fileSystemIngestOrchestrator;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setFileSnapIngestOrchestrator(FileSnapIngestOrchestrator fileSnapIngestOrchestrator) {
        this.fileSnapIngestOrchestrator = fileSnapIngestOrchestrator;
    }

    public FileSnapIngestOrchestrator getFileSnapIngestOrchestrator() {
        return fileSnapIngestOrchestrator;
    }

    public void setFileMirrorIngestOrchestrator(FileMirrorIngestOrchestrator fileMirrorIngestOrchestrator) {
        this.fileMirrorIngestOrchestrator = fileMirrorIngestOrchestrator;
    }

    public FileMirrorIngestOrchestrator getFileMirrorIngestOrchestrator() {
        return fileMirrorIngestOrchestrator;
    }

    public void setFileSystemIngestOrchestrator(FileSystemIngestOrchestrator fileSystemIngestOrchestrator) {
        this.fileSystemIngestOrchestrator = fileSystemIngestOrchestrator;
    }

    public FileSystemIngestOrchestrator getFileSystemIngestOrchestrator() {
        return fileSystemIngestOrchestrator;
    }
}
