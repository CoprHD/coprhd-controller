package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.impl;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.FileIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BlockVolumeIngestionContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by bonduj on 8/2/2016.
 */
public class FileSystemIngestionContext implements FileIngestionContext {
    private static final Logger _logger = LoggerFactory.getLogger(BlockVolumeIngestionContext.class);
    protected DbClient _dbClient;

    private UnManagedFileSystem _unManagedFileSystem;
    private List<String> _errorMessages;

    public FileSystemIngestionContext(DbClient _dbClient, UnManagedFileSystem _unManagedFileSystem) {
        this._dbClient = _dbClient;
        this._unManagedFileSystem = _unManagedFileSystem;
    }

    @Override
    public UnManagedFileSystem getUnmanagedFileSystem() {
        return _unManagedFileSystem;
    }

    @Override
    public UnManagedFileSystem getCurrentUnmanagedFileSystem() {
        return null;
    }

    @Override
    public boolean isFileExported() {
        return false;
    }

    @Override
    public List<String> getErrorMessages() {
        return null;
    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }
}
