/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.constraint.TimeSeriesConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

import com.emc.storageos.db.client.constraint.impl.*;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BourneDbClient implements DBClientWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(BourneDbClient.class);

    private DbClient dbClient;

    public BourneDbClient() {
    }

    @PostConstruct
    public void init() {
        if (dbClient == null) {
            throw new IllegalStateException(getClass().getName() + " does not have a DB Client set");
        }
        dbClient.start();
    }

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key)
            throws DataAccessException {
        return findByPermission(type, key, null);
    }

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key,
            final Set<String> filterBy) throws DataAccessException {

        if (key == null || type == null) {
            return Maps.newHashMap();
        }

        PermissionMapQueryResultList queryResults = new PermissionMapQueryResultList(filterBy);

        ContainmentPermissionsConstraint constraint =
                ContainmentPermissionsConstraint.Factory.getObjsWithPermissionsConstraint(key.toString(), type);

        try {
            getDbClient().queryByConstraint(constraint, queryResults);
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }

        // loops over results and loads into permissionMap
        for (Iterator<URI> iterator = queryResults.iterator(); iterator.hasNext(); iterator.next()) {
            // Do nothing
        }

        return queryResults.getPermissionMap();
    }

    public <T extends DataObject> List<NamedElement> findBy(Class<T> clazz, String columnField, URI id) throws DataAccessException {

        LOG.debug("findBy({}, {}, {})", new Object[] { clazz, columnField, id });

        DataObjectType doType = TypeMap.getDoType(clazz);

        ColumnField field = doType.getColumnField(columnField);

        ContainmentConstraint constraint = new ContainmentConstraintImpl(id, clazz, field);

        return queryNamedElementsByConstraint(constraint);
    }

    public <T extends DataObject> List<NamedElement> findByPrefix(Class<T> clazz, String prefixColumnField, String prefix)
            throws DataAccessException {

        LOG.debug("findByPrefix({}, {}, {})", new Object[] { clazz, prefixColumnField, prefix });

        DataObjectType doType = TypeMap.getDoType(clazz);

        PrefixConstraint constraint = new PrefixConstraintImpl(prefix, doType.getColumnField(prefixColumnField));

        return queryNamedElementsByConstraint(constraint);
    }

    public <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(Class<T> clazz, String columnField, URI id,
            String labelPrefix) throws DataAccessException {

        LOG.debug("findByContainmentAndPrefix({}, {}, {}, {})", new Object[] { clazz, columnField, id, labelPrefix });

        DataObjectType doType = TypeMap.getDoType(clazz);

        ContainmentPrefixConstraint constraint = new ContainmentPrefixConstraintImpl(id, labelPrefix, doType.getColumnField(columnField));

        return queryNamedElementsByConstraint(constraint);
    }

    public <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value)
            throws DataAccessException {
          LOG.debug("findByAlternateId(class={}, columnField={}, value={})", new Object[] { clazz, columnField, value});

        DataObjectType doType = TypeMap.getDoType(clazz);

        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField(columnField), value);

        return queryNamedElementsByConstraint(constraint);
    }

    @Override
    public List<NamedElement> findOrdersByAlternateId(String columnField, String userId, long startTime, long endTime, int maxCount)
            throws DataAccessException {

        LOG.debug("findOrdersByAlternateId(columnField={}, userId={}, maxCount={})", new Object[] { columnField, userId, maxCount});

        TimeSeriesConstraint constraint = TimeSeriesConstraint.Factory.getOrdersByUser(userId, startTime,endTime);

        return queryNamedElementsByConstraint(constraint, maxCount);
    }

    @Override
    public long getOrderCount(String userId, String fieldName, long startTime, long endTime) {

        LOG.debug("getOrderCount(userId={} cf={}, startTime={}, endTime={})", new Object[] {userId, fieldName, startTime, endTime});

        TimeSeriesConstraint constraint = TimeSeriesConstraint.Factory.getOrdersByUser(userId, startTime, endTime);
        DbClientImpl dbclient = (DbClientImpl)getDbClient();
        constraint.setKeyspace(dbclient.getKeyspace(Order.class));

        try {
            return constraint.count();
        }catch (ConnectionException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public Map<String, Long> getOrderCount(List<URI> tids, String fieldName, long startTime, long endTime) {

        LOG.debug("getOrderCount(tids={} cf={}, startTime={}, endTime={})", new Object[] {tids, fieldName, startTime, endTime});

        Map<String, Long> counts = new HashMap();

        for (URI tid : tids) {
            TimeSeriesConstraint constraint = TimeSeriesConstraint.Factory.getOrders(tid, startTime, endTime);
            DbClientImpl dbclient = (DbClientImpl) getDbClient();
            constraint.setKeyspace(dbclient.getKeyspace(Order.class));

            try {
                counts.put(tid.toString(), constraint.count());
            } catch (ConnectionException e) {
                throw new DataAccessException(e);
            }
        }

        return counts;
    }

    @Override
    public List<NamedElement> findAllOrdersByTimeRange(URI tid, String columnField, Date startTime, Date endTime,
                                                       int maxCount)
            throws DataAccessException {
        LOG.debug("findAllOrdersByTimeRange(tid={} columnField={}, startTime={} endTime={} maxCount={})",
                new Object[]{tid, columnField, startTime, endTime, maxCount});

        long startTimeInMS = startTime.getTime();
        long endTimeInMS = endTime.getTime();

        TimeSeriesConstraint constraint = TimeSeriesConstraint.Factory.getOrders(tid, startTimeInMS, endTimeInMS);
        List<NamedElement> allOrderIds = queryNamedElementsByConstraint(constraint, maxCount);

        return allOrderIds;
    }

    protected List<NamedElement> queryNamedElementsByConstraint(Constraint constraint) {
        return queryNamedElementsByConstraint(constraint, -1);
    }

    protected List<NamedElement> queryNamedElementsByConstraint(Constraint constraint, int maxCount) {
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();

        try {
            if (maxCount >0) {
                getDbClient().queryByConstraint(constraint, queryResults, null, maxCount);
            }else {
                getDbClient().queryByConstraint(constraint, queryResults);
            }
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }

        List<NamedElement> results = Lists.newArrayList();
        for (NamedElement namedElement : queryResults) {
            results.add(namedElement);
        }

        return results;
    }

    @Override
    public <T extends DataObject> List<URI> findAllIds(Class<T> clazz) throws DataAccessException {

        LOG.debug("findAllIds({})", clazz);

        try {
            boolean activeOnly = true;
            List<URI> results = getDbClient().queryByType(clazz, activeOnly);
            return results;
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }
    
    @Override
    public <T extends DataObject> Iterator<T> findAllFields(final Class<T> clazz, final List<URI> ids, final List<String> columnFields) throws DataAccessException {

        LOG.debug("findAllFields({}, {}, {})", clazz, ids, columnFields);

        try {
            return getDbClient().queryIterativeObjectFields(clazz, columnFields, ids);
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }
    
    @Override
    public <T extends DataObject> T findById(Class<T> clazz, URI id) throws DataAccessException {

        LOG.debug("findById({}, {})", new Object[] { clazz, id });

        try {
            T result = getDbClient().queryObject(clazz, id);
            return result;
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> List<T> findByIds(Class<T> clazz, List<URI> ids) throws DataAccessException {

        LOG.debug("findByIds({}, {})", new Object[] { clazz, ids });

        try {
            List<T> results = Lists.newArrayList();
            Iterator<T> iter = getDbClient().queryIterativeObjects(clazz, ids);
            while (iter.hasNext()) {
                results.add(iter.next());
            }
            return results;
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void update(T model) throws DataAccessException {

        LOG.debug("save({}:{})", model.getClass(), model);

        try {
            getDbClient().updateAndReindexObject(model);
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void create(T model) throws DataAccessException {
        LOG.debug("create({}:{})", model.getClass(), model);

        try {
            getDbClient().createObject(model);
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void delete(T model) throws DataAccessException {

        LOG.debug("delete({}:{})", model.getClass(), model);

        try {
            getDbClient().markForDeletion(model);
        } catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void delete(List<T> models) throws DataAccessException {
        getDbClient().markForDeletion(models);
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }
}
