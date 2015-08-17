/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.net.URI;
import java.util.List;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

public class BaseModelFinder<T extends DataObject> {

    protected ModelClient client;
    protected Class<T> clazz;

    public BaseModelFinder(Class<T> clazz, ModelClient client) {
        this.clazz = clazz;
        this.client = client;
    }

    protected List<URI> toURIs(StringSet in) {
        List<URI> out = Lists.newArrayList();
        if (in != null) {
            for (String s : in) {
                out.add(URI.create(s));
            }
        }
        return out;
    }

    protected List<URI> toURIs(List<NamedElement> namedElements) {
        List<URI> out = Lists.newArrayList();
        if (namedElements != null) {
            for (NamedElement namedElement : namedElements) {
                out.add(namedElement.getId());
            }
        }
        return out;
    }

    public T findById(URI id) throws DataAccessException {
        if (id != null) {
            return client.findById(clazz, id);
        }
        return null;
    }

    public T findById(String id) throws DataAccessException {
        if (StringUtils.isNotBlank(id)) {
            return findById(URI.create(id));
        }
        return null;
    }

    /**
     * Finds by IDs and filters out inactive. Note that the number of results could be less than IDs requested.
     * 
     * @param ids IDs of records to query
     * @return
     * @throws DataAccessException
     */
    public Iterable<T> findByIds(List<URI> ids, boolean activeOnly) throws DataAccessException {
        return client.findByIds(clazz, ids, activeOnly);
    }

    public Iterable<T> findByIds(StringSet ids, boolean activeOnly) throws DataAccessException {
        return findByIds(toURIs(ids), activeOnly);
    }
}
