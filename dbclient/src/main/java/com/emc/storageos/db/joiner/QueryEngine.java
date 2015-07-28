/*
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
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.DataObject;

interface QueryEngine {

    /**
     * Given a DataObject class, return all the URIs of active objects
     * 
     * @param clazz DataObjectClass
     * @return
     */
    <T extends DataObject> Set<URI> queryByType(Class<T> clazz);

    /**
     * Given a DataObject class and URI collection, returns all the matching objects.
     * 
     * @param clazz DataObjectClass
     * @param uris List of URIs of members of class
     * @return Iterator of appropriate type for the clazz
     */
    <T extends DataObject> Iterator<T> queryIterObject(Class<T> clazz, Collection<URI> uris);

    /**
     * Given a URI in a given class, return the object if it exists.
     * 
     * @param clazz -- DataObject class
     * @param uri - URI of instance in clazz
     * @return instantiated data object or null if not found or active
     */
    <T extends DataObject> T queryObject(Class<T> clazz, URI uri);

    /**
     * Return all the URIs matching a given constraint.
     * 
     * @param constraint -- Constraint (from ClassMetaData)
     * @return
     */
    <T extends DataObject> Set<URI> queryByConstraint(Constraint constraint);
}
