/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.security.authorization.PermissionsKey;

/**
 * @author Chris Dail
 */
public interface DBClientWrapper {
    public <T extends DataObject> List<URI> findAllIds(Class<T> clazz) throws DataAccessException;

    public <T extends DataObject> T findById(Class<T> clazz, URI id) throws DataAccessException;

    public <T extends DataObject> List<T> findByIds(Class<T> clazz, List<URI> ids) throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findBy(Class<T> clazz, String columnField, URI id) throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findByPrefix(Class<T> clazz, String columnField, String prefix)
            throws DataAccessException;

   public <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(Class<T> clazz, String columnField, URI id,
            String labelPrefix) throws DataAccessException;

    public <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value)
            throws DataAccessException;

    public List<NamedElement> findOrdersByAlternateId(String columnField, String value, long startTime, long endTime,
                                                      int maxCount) throws DataAccessException;

    public long getOrderCount(String userId, String value, long startTime, long endTime) throws DataAccessException;

    public Map<String, Long> getOrderCount(List<URI> tids, String value, long startTime, long endTime) throws DataAccessException;

    public List<NamedElement> findAllOrdersByTimeRange(URI tid, String columnField, Date startTime, Date endTime, int maxCount)
            throws DataAccessException;

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key)
            throws DataAccessException;

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findByPermission(Class<T> type, PermissionsKey key,
            final Set<String> filterBy) throws DataAccessException;

    public <T extends DataObject> void create(T model) throws DataAccessException;

    public <T extends DataObject> void update(T model) throws DataAccessException;

    public <T extends DataObject> void delete(T model) throws DataAccessException;

    public <T extends DataObject> void delete(List<T> models) throws DataAccessException;

    public <T extends DataObject> Iterator<T> findAllFields(final Class<T> clazz, final List<URI> ids, final List<String> columnFields);
}