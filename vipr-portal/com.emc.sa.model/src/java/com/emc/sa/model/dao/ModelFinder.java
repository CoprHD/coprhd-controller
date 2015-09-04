/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.List;

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
}
