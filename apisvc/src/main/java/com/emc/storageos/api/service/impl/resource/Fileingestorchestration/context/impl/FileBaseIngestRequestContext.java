/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.impl;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.FileIngestionContext;
import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.IngestionFileRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.FileSystemIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

/**
 * Created by bonduj on 8/1/2016.
 */
public class FileBaseIngestRequestContext implements IngestionFileRequestContext {

    private final VirtualPool _vpool;
    private final VirtualArray _virtualArray;
    private final Project _project;
    private final TenantOrg _tenant;

    private final DbClient _dbClient;


    private FileIngestionContext _currentFileIngestionContext;



    private URI _currentUnManagedFileSystemUri;

    private final Iterator<URI> _unManagedFileSystemUrisToProcessIterator;

    public FileBaseIngestRequestContext(VirtualPool _vpool, VirtualArray _virtualArray, Project _project, TenantOrg _tenant, DbClient _dbClient, Iterator<URI> _unManagedFileSystemUrisToProcessIterator) {
        this._vpool = _vpool;
        this._virtualArray = _virtualArray;
        this._project = _project;
        this._tenant = _tenant;
        this._dbClient = _dbClient;
        this._unManagedFileSystemUrisToProcessIterator = _unManagedFileSystemUrisToProcessIterator;
    }

    @Override
    public StorageSystem getStorageSystem() {
        return null;
    }

    @Override
    public VirtualPool getVpool(UnManagedFileSystem unmanagedFileSystem) {
        return null;
    }

    @Override
    public VirtualArray getVarray(UnManagedFileSystem unmanagedFileSystem) {
        return null;
    }

    @Override
    public Project getProject() {
        return null;
    }

    @Override
    public TenantOrg getTenant() {
        return null;
    }


    @Override
    public URI getCurrentUnManagedFileSystemUri() {
        return null;
    }

    @Override
    public FileIngestionContext getFileContext() {
        return null;
    }

    /*
    * (non-Javadoc)
    *
    * @see java.util.Iterator#hasNext()
    */
    @Override
    public boolean hasNext() {
        return _unManagedFileSystemUrisToProcessIterator.hasNext();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#next()
     */
    @Override
    public UnManagedFileSystem next() {
        _currentUnManagedFileSystemUri = _unManagedFileSystemUrisToProcessIterator.next();
        UnManagedFileSystem currentFileSystem = _dbClient.queryObject(UnManagedFileSystem.class, _currentUnManagedFileSystemUri);
        if (null != currentFileSystem) {
            this.setCurrentUnmanagedFileSystemUri(currentFileSystem);
        }
        return currentFileSystem;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        _unManagedFileSystemUrisToProcessIterator.remove();
    }

    /**
     * Instantiates the correct FileIngestionContext type for the
     * current UnManagedFileSystem being processed, based on the UnManagedFile type.
     */
    protected static class FileIngestionContextFactory {

        public static FileIngestionContext getVolumeIngestionContext(UnManagedFileSystem unManagedFileSystem,
                                                                       DbClient dbClient, IngestionFileRequestContext parentRequestContext) {
            if (null == unManagedFileSystem) {
                return null;
            } else {
                return new FileSystemIngestionContext(dbClient, unManagedFileSystem);
            }
        }

    }


    /**
     * Private setter for the current UnManagedFileSystem, used by this class' implementation
     * of Iterator<UnManagedFileSystem>. This method will set the current FileIngestionContext.
     *
     * @param unManagedFileSystem the UnManagedFileSystem to set
     */
    private void setCurrentUnmanagedFileSystemUri(UnManagedFileSystem unManagedFileSystem) {
       this._currentFileIngestionContext = FileIngestionContextFactory.getVolumeIngestionContext(unManagedFileSystem, _dbClient, this);
    }


    @Override
    public UnManagedFileSystem getCurrentUnmanagedFileSystem() {
        if (_currentFileIngestionContext == null) {
            return null;
        }

        return _currentFileIngestionContext.getUnmanagedFileSystem();
    }

}
