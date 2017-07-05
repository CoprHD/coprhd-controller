/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.DataObject;

public class ModelFinder<T extends DataObject> extends BaseModelFinder<T> {

    public ModelFinder(Class<T> clazz, DBClientWrapper client) {
        super(clazz, client);
    }

    public List<T> findAll() throws DataAccessException {
        List<URI> ids = findAllIds();
        return findByIds(ids);
    }

    public List<URI> findByLabel(String prefix) {
        return toURIs(client.findByPrefix(clazz, "label", prefix));
    }

    public List<URI> findAllIds() throws DataAccessException {
        return client.findAllIds(clazz);
    }
    
    public Iterator<T> findAllFields(final List<URI> ids, final List<String> columnField) {
        return client.findAllFields(clazz, ids, columnField);
    }
}
