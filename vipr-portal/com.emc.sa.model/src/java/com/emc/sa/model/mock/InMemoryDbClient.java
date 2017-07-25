/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.mock;

import java.net.URI;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.uimodels.Order;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.emc.sa.model.dao.DBClientWrapper;
import com.emc.sa.model.dao.DataAccessException;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class InMemoryDbClient implements DBClientWrapper {

    private static final Logger LOG = Logger.getLogger(InMemoryDbClient.class);
    private Map<URI, DataObject> data = Maps.newLinkedHashMap();

    private Object getColumnField(DataObject o, String columnField) {
        try {
            return PropertyUtils.getProperty(o, columnField);
        } catch (Exception e) {
            return null;
        }
    }

    private NamedElement createNamedElement(DataObject value) {
        NamedElement elem = new NamedElement();
        elem.setId(value.getId());
        elem.setName(value.getLabel());
        return elem;
    }

    @Override
    public <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value)
            throws DataAccessException {
        List<NamedElement> results = Lists.newArrayList();
        for (URI modelId : findAllIds(clazz)) {
            T model = findById(clazz, modelId);
            Object o = getColumnField(model, columnField);
            if (ObjectUtils.equals(o, value)) {
                results.add(createNamedElement(model));
            }
        }
        return results;
    }

    @Override
    public List<NamedElement> findOrdersByAlternateId(String columnField, String value, long startTime, long endTime,
                                                      int maxCount)
            throws DataAccessException {
        List<NamedElement> results = Lists.newArrayList();
        for (URI modelId : findAllIds(Order.class)) {
            Order order = findById(Order.class, modelId);
            Object o = getColumnField(order, columnField);
            if (ObjectUtils.equals(o, value)) {
                results.add(createNamedElement(order));
            }
        }
        return results;
    }

    @Override
    public long getOrderCount(String userId, String fieldName, long startTime, long endTime) {
        //TODO:
        return 0;
    }

    @Override
    public Map<String, Long> getOrderCount(List<URI> tids, String fieldName, long startTime, long endTime) {
        //TODO:
        return null;
    }

    @Override
    public <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(Class<T> clazz, String columnField, URI id,
            String labelPrefix) throws DataAccessException {
        List<NamedElement> results = Lists.newArrayList();
        for (URI modelId : findAllIds(clazz)) {
            T model = findById(clazz, modelId);
            Object o = getColumnField(model, columnField);
            if (ObjectUtils.equals(o, id) && StringUtils.startsWith(model.getLabel(), labelPrefix)) {
                results.add(createNamedElement(model));
            }
        }
        return results;
    }

    @Override
    public List<NamedElement> findAllOrdersByTimeRange(URI tid, String columnField, Date startTime, Date endTime, int maxCount)
            throws DataAccessException {
        List<NamedElement> results = Lists.newArrayList();
        for (URI modelId : findAllIds(Order.class)) {
            Order model = findById(Order.class, modelId);
            Object o = getColumnField(model, columnField);
            results.add(createNamedElement(model));
        }
        return results;
    }

    @Override
    public <T extends DataObject> List<NamedElement> findBy(Class<T> clazz, String columnField, URI id) throws DataAccessException {
        List<NamedElement> results = Lists.newArrayList();
        for (URI modelId : findAllIds(clazz)) {
            T model = findById(clazz, modelId);
            Object o = getColumnField(model, columnField);
            if (ObjectUtils.equals(o, id)) {
                results.add(createNamedElement(model));
            }
        }
        return results;
    }

    @Override
    public <T extends DataObject> List<NamedElement> findByPrefix(Class<T> clazz, String columnField, String prefix)
            throws DataAccessException {
        List<NamedElement> results = Lists.newArrayList();
        for (URI modelId : findAllIds(clazz)) {
            T model = findById(clazz, modelId);
            Object o = getColumnField(model, columnField);
            if (o != null && o.toString().startsWith(prefix)) {
                results.add(createNamedElement(model));
            }
        }
        return results;
    }

    @Override
    public <T extends DataObject> List<URI> findAllIds(Class<T> clazz) throws DataAccessException {
        LOG.debug("findAllIds(" + clazz.getSimpleName() + ")");
        List<URI> results = Lists.newArrayList();
        for (Map.Entry<URI, DataObject> entry : data.entrySet()) {
            if (entry.getValue().getClass() == clazz) {
                LOG.debug(" - " + entry.getKey());
                results.add(entry.getKey());
            }
        }
        return results;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T extends DataObject> Iterator<T> findAllFields(Class<T> clazz, final List<URI> ids, final List<String> columnFields) throws DataAccessException {
        LOG.debug("findAllIds(" + clazz.getSimpleName() + ")");
        List<T> results = Lists.newArrayList();
        for (Map.Entry<URI, DataObject> entry : data.entrySet()) {
            if (entry.getValue().getClass() == clazz) {
                LOG.debug(" - " + entry.getKey());
                results.add((T) entry);
            }
        }
        return results.iterator();
    }

    @Override
    public <T extends DataObject> T findById(Class<T> clazz, URI id) throws DataAccessException {
        @SuppressWarnings("unchecked")
        T result = (T) data.get(id);
        LOG.debug("findById(" + clazz.getSimpleName() + ", " + id + ") = " + result);
        return result;
    }

    @Override
    public <T extends DataObject> List<T> findByIds(Class<T> clazz, List<URI> ids) throws DataAccessException {
        List<T> results = Lists.newArrayList();
        for (URI id : ids) {
            results.add(findById(clazz, id));
        }
        return results;
    }

    @Override
    public <T extends DataObject> void update(T model) throws DataAccessException {
        if (model != null) {
            if (model.getId() == null) {
                model.setId(URIUtil.createId(model.getClass()));
                LOG.debug("save(" + model.getId() + ") (new)");
            }
            else {
                LOG.debug("save(" + model.getId() + ")");
            }
            URI id = model.getId();
            data.put(id, model);
        }
    }

    @Override
    public <T extends DataObject> void create(T model) throws DataAccessException {
        update(model);
    }

    @Override
    public <T extends DataObject> void delete(T model) throws DataAccessException {
        if (model != null) {
            URI id = model.getId();
            if (data.remove(id) != null) {
                LOG.debug("Deleted " + model.getClass().getSimpleName() + " with ID: " + id);
            }
            else {
                LOG.debug("No such object: " + id);
            }
        }
    }

    @Override
    public <T extends DataObject> void delete(List<T> models) throws DataAccessException {
        for (T model : models) {
            delete(model);
        }
    }

    @Override
    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key)
            throws DataAccessException {
        return Maps.newHashMap();
    }

    @Override
    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key,
            Set<String> filterBy) throws DataAccessException {
        return Maps.newHashMap();
    }

}
