/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.*;

import javax.annotation.PostConstruct;

import com.emc.storageos.db.client.constraint.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentPrefixConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.PrefixConstraintImpl;
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
            throw new IllegalStateException(getClass().getName()+" does not have a DB Client set");
        }
        dbClient.start();
    }
    
    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key) throws DataAccessException {
        return findByPermission(type, key, null);
    }
    
    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key, final Set<String> filterBy) throws DataAccessException {

        if (key == null || type == null) {
            return Maps.newHashMap();
        }        
        
        PermissionMapQueryResultList queryResults = new PermissionMapQueryResultList(filterBy);        
        
        ContainmentPermissionsConstraint constraint = 
                ContainmentPermissionsConstraint.Factory.getObjsWithPermissionsConstraint(key.toString(), type);
        
        try {
            getDbClient().queryByConstraint(constraint, queryResults);
        }
        catch (DatabaseException e) {
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
    
    public <T extends DataObject> List<NamedElement> findByPrefix(Class<T> clazz, String prefixColumnField, String prefix) throws DataAccessException {
        
        LOG.debug("findByPrefix({}, {}, {})", new Object[] { clazz, prefixColumnField, prefix });
        
        DataObjectType doType = TypeMap.getDoType(clazz);
        
        PrefixConstraint constraint = new PrefixConstraintImpl(prefix, doType.getColumnField(prefixColumnField));
        
        return queryNamedElementsByConstraint(constraint);
    }
    
    public <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(Class<T> clazz, String columnField, URI id, String labelPrefix) throws DataAccessException {
     
        LOG.debug("findByContainmentAndPrefix({}, {}, {}, {})", new Object[] { clazz, columnField, id, labelPrefix });
        
        DataObjectType doType = TypeMap.getDoType(clazz);
        
        ContainmentPrefixConstraint constraint = new ContainmentPrefixConstraintImpl(id, labelPrefix, doType.getColumnField(columnField));
        
        return queryNamedElementsByConstraint(constraint);
    }
    
    public <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value) throws DataAccessException {
        
        LOG.debug("findByAlternateId({}, {}, {})", new Object[] { clazz, columnField, value });
        
        DataObjectType doType = TypeMap.getDoType(clazz);
        
        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField(columnField), value);
        
        return queryNamedElementsByConstraint(constraint);
    }

    public <T extends DataObject> List<NamedElement> findByTimeRange(Class<T> clazz, String columnField, Date startTime, Date endTime) throws DataAccessException {
        LOG.debug("findByTimeRange({}, {})", new Object[] { clazz, columnField });
        DecommissionedConstraint constraint = DecommissionedConstraint.Factory.getTimeConstraint(clazz, columnField, startTime, endTime);
        return queryNamedElementsByConstraint(constraint);
    }

    protected List<NamedElement> queryNamedElementsByConstraint(Constraint constraint) {
        
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();
        
        try {
            getDbClient().queryByConstraint(constraint, queryResults);
        }
        catch (DatabaseException e) {
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
        
        LOG.debug("findAllIds({})", clazz );
        
        try {
            boolean activeOnly = true;
            List<URI> results = getDbClient().queryByType(clazz, activeOnly);
            return results;
        }
        catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> T findById(Class<T> clazz, URI id) throws DataAccessException {
        
        LOG.debug("findById({}, {})", new Object[] { clazz, id });
        
        try {
            T result = getDbClient().queryObject(clazz, id);
            return result;
        }
        catch (DatabaseException e) {
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
        }
        catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void update(T model) throws DataAccessException {
        
        LOG.debug("save({}:{})", model.getClass(), model);

        try {
            getDbClient().updateAndReindexObject(model);
        }
        catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void create(T model) throws DataAccessException {
        LOG.debug("create({}:{})", model.getClass(), model);

        try {
            getDbClient().createObject(model);
        }
        catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void delete(T model) throws DataAccessException {
        
        LOG.debug("delete({}:{})", model.getClass(), model);
        
        try {
            getDbClient().markForDeletion(model);
        }
        catch (DatabaseException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public <T extends DataObject> void delete(List<T> models) throws DataAccessException {
        for (T model: models) {
            delete(model);
        }
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }
}
