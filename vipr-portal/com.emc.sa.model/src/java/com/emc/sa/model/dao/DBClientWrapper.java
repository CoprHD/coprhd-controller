/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.security.authorization.PermissionsKey;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Chris Dail
 */
public interface DBClientWrapper {
    public <T extends DataObject> List<URI> findAllIds(Class<T> clazz) throws DataAccessException;

    public <T extends DataObject> T findById(Class<T> clazz, URI id) throws DataAccessException;

    public <T extends DataObject> List<T> findByIds(Class<T> clazz, List<URI> ids) throws DataAccessException;

    // public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName, List<URI> ids) throws IOException;
    // public <T> void queryByConstraint(Constraint constraint, Constraint.QueryResult<T> result) throws IOException;

    public <T extends DataObject> List<NamedElement> findBy(Class<T> clazz, String columnField, URI id) throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findByPrefix(Class<T> clazz, String columnField, String prefix)
            throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(Class<T> clazz, String columnField, URI id,
            String labelPrefix) throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value)
            throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findByTimeRange(Class<T> clazz, String columnField, Date startTime, Date endTime)
            throws DataAccessException;

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key)
            throws DataAccessException;

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key,
            final Set<String> filterBy) throws DataAccessException;

    public <T extends DataObject> void create(T model) throws DataAccessException;

    public <T extends DataObject> void update(T model) throws DataAccessException;

    public <T extends DataObject> void delete(T model) throws DataAccessException;

    public <T extends DataObject> void delete(List<T> models) throws DataAccessException;
}
