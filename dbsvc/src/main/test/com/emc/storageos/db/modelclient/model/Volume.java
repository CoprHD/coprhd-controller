/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import java.net.URI;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;

/**
 * @author cgarber
 * 
 */
@Cf("Volume")
public class Volume extends BlockObject {

    private URI _pool;
    private StoragePool _storagePool;

    @RelationIndex(cf = "RelationIndex", type = StoragePool.class)
    @Name("pool")
    public URI getPool() {
        return _pool;
    }

    public void setPool(URI pool) {
        _pool = pool;
        setChanged("pool");
    }

    /**
     * @return the _storagePool
     */
    @Relation(mappedBy = "pool")
    @Name("storagePool")
    public StoragePool getStoragePool() {
        return _storagePool;
    }

    /**
     * @param _storagePool the _storagePool to set
     */
    public void setStoragePool(StoragePool _storagePool) {
        this._storagePool = _storagePool;
    }

}
