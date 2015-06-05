/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.joiner;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.DataObject;

/**
 * This class an implementation of the QueryEngine interface which represents
 * the low level DB interfaces (here DbClient).
 * Joiner will invoke the database through this class.
 * @author watson
 *
 */
class DbClientQueryEngine implements QueryEngine {
    private DbClient _dbClient;
    
    DbClientQueryEngine(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    @Override
    public <T extends DataObject> Set<URI> queryByType(Class<T> clazz) {
       HashSet<URI> returnSet = new HashSet<URI>();
       List<URI> uris = _dbClient.queryByType(clazz, true);
       returnSet.addAll(uris);
       return returnSet;
    }
    
    @Override
    public <T extends DataObject> Iterator<T> queryIterObject(Class<T> clazz, Collection<URI> uris) {
        Iterator<T> iter = _dbClient.queryIterativeObjects(clazz, new ArrayList(uris));
        return iter;
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI uri) {
        return _dbClient.queryObject(clazz, uri);
    }
    
    /**
     * Return the URIs matching a Constraint.
     * @param constraint
     * @return
     */
    @Override
    public <T extends DataObject> Set<URI> queryByConstraint(Constraint constraint) {
        List<URI> uris = _dbClient.queryByConstraint(constraint);
        Set<URI> uriSet = new HashSet<URI>();
        uriSet.addAll(uris);
        return uriSet;
    }
}
