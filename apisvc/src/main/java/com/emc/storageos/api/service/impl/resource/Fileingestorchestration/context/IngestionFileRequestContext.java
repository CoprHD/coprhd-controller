/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.IngestFileStrategyFactory;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Created by bonduj on 8/1/2016.
 */
public interface IngestionFileRequestContext extends Iterator<UnManagedFileSystem> {
    /**
     * Returns the StorageSystem for the UnManagedFileSystem currently being processed.
     *
     * @return the StorageSystem for the UnManagedFileSystem currently being processed
     */
    public StorageSystem getStorageSystem();

    /**
     * Returns the VirtualPool for the UnManagedFileSystem currently being processed.
     *
     * @param unmanagedFileSystem the UnManagedFileSystem to find the vpool for
     * @return the VirtualPool for the UnManagedFileSystem currently being processed
     */
    public VirtualPool getVpool(UnManagedFileSystem unmanagedFileSystem);

    /**
     * Returns the VirtualArray for the UnManagedFileSystem currently being processed.
     *
     * @param unmanagedFileSystem the UnManagedFileSystem to find the varray for
     * @return the VirtualArray for the UnManagedFileSystem currently being processed
     */
    public VirtualArray getVarray(UnManagedFileSystem unmanagedFileSystem);

    /**
     * Returns the Project for the UnManagedFileSystem currently being processed.
     *
     * @return the Project for the UnManagedFileSystem currently being processed
     */
    public Project getProject();

    /**
     * Returns the TenantOrg for the UnManagedFileSystem currently being processed.
     *
     * @return the TenantOrg for the UnManagedFileSystem currently being processed
     */
    public TenantOrg getTenant();


    /**
     * Returns the UnManagedFileSystem currently being processed by ingestion.
     *
     * @return the UnManagedFileSystem currently being processed
     */
    public UnManagedFileSystem getCurrentUnmanagedFileSystem();

    /**
     * Returns the UnManagedFileSystem URI currently being processed by ingestion.
     *
     * @return the UnManagedFileSystem URI currently being processed
     */
    public URI getCurrentUnManagedFileSystemUri();

    /**
     * Returns the FileIngestionContext currently being processed by ingestion.
     *
     * @return the FileIngestionContext currently being processed
     */
    public FileIngestionContext getFileContext();

}
