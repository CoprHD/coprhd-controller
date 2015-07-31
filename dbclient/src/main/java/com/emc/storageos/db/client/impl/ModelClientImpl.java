/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentPrefixConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.PrefixConstraintImpl;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.common.IterativeList;
import com.emc.storageos.db.exceptions.DatabaseException;
import java.net.URI;
import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Dail
 */
public class ModelClientImpl extends ModelClient {

    private static final Logger LOG = LoggerFactory.getLogger(ModelClientImpl.class);

    private DbClient dbClient;

    public ModelClientImpl() {
    }

    public ModelClientImpl(DbClient client) {
        this.dbClient = client;
    }

    @PostConstruct
    public void init() {
        if (dbClient == null) {
            throw new IllegalStateException(getClass().getName() + " does not have a DB Client set");
        }
        dbClient.start();
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    /**
     * Finds an object by ID.
     * 
     * @param id
     *            the ID of the object.
     * @return the object.
     */
    public <T extends DataObject> T findById(URI id) {
        if (id == null) {
            throw DatabaseException.fatals.nullIdProvided();
        }
        Class<T> modelClass = getModelClass(id);
        if (modelClass != null) {
            return of(modelClass).findById(id);
        }
        else {
            return null;
        }
    }

    /**
     * Finds an object by ID.
     * 
     * @param id
     *            the ID of the object.
     * @return the object.
     */
    public <T extends DataObject> T findById(String id) {
        if (id == null) {
            throw DatabaseException.fatals.nullIdProvided();
        }
        return findById(URI.create(id));
    }

    public <T extends DataObject> List<NamedElement> findByLabel(Class<T> clazz, String label) {
        return findByPrefix(clazz, "label", label);
    }

    public <T extends DataObject> List<URI> findByType(Class<T> clazz, boolean activeOnly) {
        return findAllIds(clazz, activeOnly);
    }

    @Override
    public <T extends DataObject> Iterable<T> findByIds(Class<T> clazz, List<URI> ids, boolean activeOnly) throws DatabaseException {
        LOG.debug("findByIds({}, {})", new Object[] { clazz, ids });

        Iterable<T> it = new IterativeList<T>(dbClient.queryIterativeObjects(clazz, ids, activeOnly));
        return it;
    }

    @Override
    public <T extends DataObject> List<URI> findAllIds(Class<T> clazz, boolean activeOnly) throws DatabaseException {
        LOG.debug("findAllIds({})", clazz);

        List<URI> results = dbClient.queryByType(clazz, activeOnly);
        return results;
    }

    @Override
    public <T extends DataObject> T findById(Class<T> clazz, URI id) throws DatabaseException {
        LOG.debug("findById({}, {})", new Object[] { clazz, id });

        T result = dbClient.queryObject(clazz, id);
        return result;
    }

    @Override
    public <T extends DataObject> List<NamedElement> findBy(Class<T> clazz,
            String columnField, URI id) throws DatabaseException {
        LOG.debug("findBy({}, {}, {})", new Object[] { clazz, columnField, id });

        DataObjectType doType = TypeMap.getDoType(clazz);

        ColumnField field = doType.getColumnField(columnField);

        ContainmentConstraint constraint = new ContainmentConstraintImpl(id, clazz, field);

        return queryNamedElementsByConstraint(constraint);
    }

    @Override
    public <T extends DataObject> List<NamedElement> findByPrefix(
            Class<T> clazz, String columnField, String prefix)
            throws DatabaseException {
        LOG.debug("findByPrefix({}, {}, {})", new Object[] { clazz, columnField, prefix });

        DataObjectType doType = TypeMap.getDoType(clazz);

        PrefixConstraint constraint = new PrefixConstraintImpl(prefix, doType.getColumnField(columnField));

        return queryNamedElementsByConstraint(constraint);
    }

    @Override
    public <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(
            Class<T> clazz, String columnField, URI id, String labelPrefix)
            throws DatabaseException {
        LOG.debug("findByContainmentAndPrefix({}, {}, {}, {})", new Object[] { clazz, columnField, id, labelPrefix });

        DataObjectType doType = TypeMap.getDoType(clazz);

        ContainmentPrefixConstraint constraint = new ContainmentPrefixConstraintImpl(id, labelPrefix, doType.getColumnField(columnField));

        return queryNamedElementsByConstraint(constraint);
    }

    @Override
    public <T extends DataObject> void create(T model) throws DatabaseException {
        LOG.debug("create({}:{})", model.getClass(), model);

        dbClient.createObject(model);
    }

    @Override
    public <T extends DataObject> void update(T model) throws DatabaseException {
        LOG.debug("save({}:{})", model.getClass(), model);

        dbClient.updateAndReindexObject(model);
    }

    @Override
    public <T extends DataObject> void delete(T model) throws DatabaseException {
        LOG.debug("delete({}:{})", model.getClass(), model);

        dbClient.markForDeletion(model);
    }

    protected List<NamedElement> queryNamedElementsByConstraint(Constraint constraint) {
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();

        dbClient.queryByConstraint(constraint, queryResults);
        return queryResults;
    }

    @Override
    public <T extends DataObject> void delete(List<T> models) throws DatabaseException {
        for (T model : models) {
            delete(model);
        }
    }

    @Override
    public <T extends DataObject> List<NamedElement> findByAlternateId(
            Class<T> clazz, String columnField, String value)
            throws DatabaseException {
        LOG.debug("findByAlternateId({}, {}, {})", new Object[] { clazz, columnField, value });

        DataObjectType doType = TypeMap.getDoType(clazz);

        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField(columnField), value);

        return queryNamedElementsByConstraint(constraint);
    }
}
