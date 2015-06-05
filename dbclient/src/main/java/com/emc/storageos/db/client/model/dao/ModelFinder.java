/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.dao.DataAccessException;

import java.net.URI;
import java.util.List;

public class ModelFinder<T extends DataObject> extends BaseModelFinder<T> {

    public ModelFinder(Class<T> clazz, ModelClient client) {
        super(clazz, client);
    }

    public Iterable<T> findAll(boolean activeOnly) throws DataAccessException {
        List<URI> ids = findAllIds(activeOnly);
        return findByIds(ids, activeOnly);
    }

    public List<URI> findByLabel(String prefix) {
        return toURIs(client.findByPrefix(clazz, "label", prefix));
    }
    
    public List<URI> findAllIds(boolean activeOnly) throws DataAccessException {
        return client.findAllIds(clazz, activeOnly);
    }
}
