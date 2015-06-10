/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
                out.add(namedElement.id);
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
