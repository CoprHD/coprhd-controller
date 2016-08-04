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

    public enum ReplicationStrategy {
        LOCAL, REMOTE
    }

    public enum FileSystemType {
        FILESYSTEM, SNAPSHOT, CLONE, MIRROR
    }


    public enum IngestFileStrategyEnum {
        LOCAL_FILESHARE,
        LOCAL_SNAPSHOT,
        LOCAL_MIRROR,
        LOCAL_CLONE,
        REMOTE_FILESHARE,

        NONE;

        public static IngestFileStrategyEnum getIngestStrategy(String strategyName) {
            _logger.debug("looking for a strategy for strategy name: " + strategyName);
            for (IngestFileStrategyEnum fileStrategyEnum : copyOfValues) {
                if (fileStrategyEnum.name().equals(strategyName)) {
                    return fileStrategyEnum;
                }
            }
            return NONE;
        }

        private static final IngestFileStrategyEnum[] copyOfValues = values();

    }

    /**
     * Based on the strategy key, ingest strategy object will be associated
     * with corresponding ingestResource
     *
     * @param strategyEnum
     * @return
     */
    public IngestFileStrategy getIngestStrategy(IngestFileStrategyEnum strategyEnum) {

        IngestFileStrategy ingestStrategy = new IngestFileStrategy();
        ingestStrategy.setDbClient(_dbClient);
        switch (strategyEnum) {

            case REMOTE_FILESHARE:
                ingestStrategy.setIngestResourceOrchestrator(fileMirrorIngestOrchestrator);
                break;

            case LOCAL_CLONE:
            case LOCAL_FILESHARE:
                ingestStrategy.setIngestResourceOrchestrator(fileSystemIngestOrchestrator);
                break;

            case LOCAL_SNAPSHOT:
                ingestStrategy.setIngestResourceOrchestrator(fileSnapIngestOrchestrator);
                break;

            case LOCAL_MIRROR:
                ingestStrategy.setIngestResourceOrchestrator(fileMirrorIngestOrchestrator);
                break;
            default:
                break;

        }
        return ingestStrategy;
    }
}
