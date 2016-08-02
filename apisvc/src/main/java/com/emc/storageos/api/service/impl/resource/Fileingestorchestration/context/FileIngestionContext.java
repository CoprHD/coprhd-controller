/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;

import java.util.List;
import java.net.URI;

/**
 * Created by bonduj on 8/1/2016.
 */
public interface FileIngestionContext {
    public UnManagedFileSystem getUnmanagedFileSystem();

    public UnManagedFileSystem getCurrentUnmanagedFileSystem();
    /**
     * Returns true if this FileIngestionContext's UnManagedFileSystem is exported
     *
     *
     * @return true if the UnManagedFileSystem is exported
     * @return true if the UnManagedFileSystem is exported
     */
    public boolean isFileExported();

    /**
     * A list of Strings for any error messages related to
     * the processing of this UnManagedFileSystem, used to assemble
     * an error message returned in the task status for this UnManagedFileSystem.
     *
     * @return a List of error message Strings
     */
    public List<String> getErrorMessages();

    /**
     * Commits any changes to the database related to the
     * processing of this FileIngestionContext's UnManagedFileSystem.
     */
    public void commit();

    /**
     * Rolls back any changes to the database related to the
     * processing of this FileIngestionContext's UnManagedFileSystem.
     */
    public void rollback();
}
