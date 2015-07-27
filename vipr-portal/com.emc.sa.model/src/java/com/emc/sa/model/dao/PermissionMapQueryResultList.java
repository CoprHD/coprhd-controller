/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.emc.storageos.db.client.constraint.QueryResultList;
import com.google.common.collect.Maps;

public class PermissionMapQueryResultList extends QueryResultList<URI> {
    
    private Set<String> filterBy;
    private Map<URI, Set<String>> permissionsMap = Maps.newHashMap();
    
    public PermissionMapQueryResultList(Set<String> filterBy) {
        this.filterBy = filterBy;
    }
    
    
    @Override
    public URI createQueryHit(URI uri) {
        // none
        return uri;
    }

    @Override
    public URI createQueryHit(URI uri, String permission, UUID timestamp) {
        if (filterBy == null || filterBy.contains(permission)) {
            if (!permissionsMap.containsKey(uri)) {
                permissionsMap.put(uri, new HashSet<String>());
            }
            permissionsMap.get(uri).add(permission);
        }
        return uri;
    }
    
    public Map<URI, Set<String>> getPermissionMap() {
        
        return permissionsMap;
    }
}
