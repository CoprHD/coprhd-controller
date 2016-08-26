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

    public ExportMaskValidationContext() {
    }

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

    public StorageSystem getStorage() {
        return storage;
    }

    public void setStorage(StorageSystem storage) {
        this.storage = storage;
    }

    public ExportMask getExportMask() {
        return exportMask;
    }

    public void setExportMask(ExportMask exportMask) {
        this.exportMask = exportMask;
    }

    public Collection<? extends BlockObject> getBlockObjects() {
        return blockObjects;
    }

    public void setBlockObjects(Collection<? extends BlockObject> blockObjects) {
        this.blockObjects = blockObjects;
    }

    public void setBlockObjects(Collection<URI> blockObjects, DbClient dbClient) {
        this.blockObjects = BlockObject.fetchAll(dbClient, blockObjects);
    }

    public Collection<Initiator> getInitiators() {
        return initiators;
    }

    public void setInitiators(Collection<Initiator> initiators) {
        this.initiators = initiators;
    }
}
