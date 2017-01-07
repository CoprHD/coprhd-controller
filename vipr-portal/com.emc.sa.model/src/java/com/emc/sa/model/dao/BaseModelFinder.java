/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;

public class BaseModelFinder<T extends DataObject> {
    protected DBClientWrapper client;
    protected Class<T> clazz;

    public BaseModelFinder(Class<T> clazz, DBClientWrapper client) {
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

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findPermissions(Class<T> type, StorageOSUser user, URI tenantId) {
        return findPermissions(type, user, tenantId, null);
    }

    public <T extends DataObjectWithACLs> Map<URI, Set<String>> findPermissions(Class<T> type, StorageOSUser user, URI tenantId,
            final Set<String> filterBy) {
        final Map<URI, Set<String>> permissionsMap = Maps.newHashMap();

        if (user == null) {
            throw new IllegalArgumentException("StorageOSUser can not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant URI can not be null");
        }

        try {
            PermissionsKey userKey = new PermissionsKey(PermissionsKey.Type.SID, user.getName(), tenantId);
            Map<URI, Set<String>> userPermissions = client.findByPermission(type, userKey, filterBy);
            if (userPermissions != null && userPermissions.isEmpty() == false) {
                permissionsMap.putAll(userPermissions);
            }

            if (user.getGroups() != null) {
                for (String group : user.getGroups()) {

                    PermissionsKey groupKey = new PermissionsKey(PermissionsKey.Type.GROUP, group, tenantId);
                    Map<URI, Set<String>> groupPermissions = client.findByPermission(type, groupKey, filterBy);

                    if (groupPermissions != null && groupPermissions.isEmpty() == false) {
                        permissionsMap.putAll(groupPermissions);
                    }
                }
            }

        } catch (DatabaseException ex) {
            throw new DataAccessException(ex);
        }

        return permissionsMap;
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
    public List<T> findByIds(List<URI> ids) throws DataAccessException {
        if (ids != null) {
            return active(client.findByIds(clazz, ids));
        }
        return Lists.newArrayList();
    }

    public List<T> findByIds(StringSet ids) throws DataAccessException {
        return findByIds(toURIs(ids));
    }

    public static <T extends DataObject> List<T> active(List<T> unfiltered) {
        List<T> filtered = Lists.newArrayList();
        for (T item : unfiltered) {
            if (item.getInactive() == null || !item.getInactive()) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
