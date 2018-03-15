/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.contexts;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;

import java.net.URI;
import java.util.Collection;

/**
 * Defines the context for validating an export mask operation.
 */
public class ExportMaskValidationContext implements ExceptionContext {

    private StorageSystem storage;
    private ExportMask exportMask;
    private Collection<? extends BlockObject> blockObjects;
    private Collection<Initiator> initiators;
    private boolean allowExceptions = true;

    /**
     * Default constructor
     */
    public ExportMaskValidationContext() {
    }

    /**
     * Convenience constructor.
     *
     * @param storage       StorageSystem
     * @param exportMask    ExportMask
     * @param blockObjects  Collection of BlockObject
     * @param initiators    Collection of Initiator
     */
    public ExportMaskValidationContext(StorageSystem storage, ExportMask exportMask,
                                       Collection<BlockObject> blockObjects, Collection<Initiator> initiators) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.blockObjects = blockObjects;
        this.initiators = initiators;
    }

    @Override
    public void setAllowExceptions(boolean allowExceptions) {
        this.allowExceptions = allowExceptions;
    }

    @Override
    public boolean isAllowExceptions() {
        return allowExceptions;
    }

    /**
     * Get the {@link StorageSystem}
     * @return  StorageSystem
     */
    public StorageSystem getStorage() {
        return storage;
    }

    /**
     * Set the {@link StorageSystem}
     * @param storage   StorageSystem
     */
    public void setStorage(StorageSystem storage) {
        this.storage = storage;
    }

    /**
     * Get the {@link ExportMask}
     * @return  ExportMask
     */
    public ExportMask getExportMask() {
        return exportMask;
    }

    /**
     * Set the {@link ExportMask}
     * @param exportMask    ExportMask
     */
    public void setExportMask(ExportMask exportMask) {
        this.exportMask = exportMask;
    }

    /**
     * Get the {@link BlockObject} collection
     * @return  Collection of BlockObject
     */
    public Collection<? extends BlockObject> getBlockObjects() {
        return blockObjects;
    }

    /**
     * Set the {@link BlockObject} collection
     * @param blockObjects  Collection of BlockObject
     */
    public void setBlockObjects(Collection<? extends BlockObject> blockObjects) {
        this.blockObjects = blockObjects;
    }

    /**
     * Convenience setter of passing a collection of {@link BlockObject} URIs to be queried from
     * the database using {@code dbClient}
     * @param blockObjects  Collection of BlockObject
     * @param dbClient      DatabaseClient
     */
    public void setBlockObjects(Collection<URI> blockObjects, DbClient dbClient) {
        this.blockObjects = BlockObject.fetchAll(dbClient, blockObjects);
    }

    /**
     * Get the {@link Initiator} collection.
     * @return  Collection of Initiator
     */
    public Collection<Initiator> getInitiators() {
        return initiators;
    }

    /**
     * Set the {@link Initiator} collection.
     * @param initiators    Collection of Initiator
     */
    public void setInitiators(Collection<Initiator> initiators) {
        this.initiators = initiators;
    }
}
