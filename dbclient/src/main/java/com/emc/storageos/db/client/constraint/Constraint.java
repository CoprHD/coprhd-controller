/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.Iterator;
import java.util.UUID;

import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Top level marker for constraints
 */
public interface Constraint {
    /**
     * Encapsulates query result out parameter
     */
    interface QueryResult<T> {
        /**
         * Set query result
         * 
         * @param iterator iterator through query results
         */
        void setResult(Iterator<T> iterator);

        /**
         * Creates a single query hit
         * 
         * @param uri
         * @return
         */
        T createQueryHit(URI uri);

        /**
         * Creates a single query hit
         * 
         * 
         * 
         * @param uri
         * @param name
         * @param timestamp The time the entry was added to the index
         * @return
         */
        T createQueryHit(URI uri, String name, UUID timestamp);

        /**
         * Creates a single query hit
         * 
         * @param uri
         * @param name
         * @return
         */
        T createQueryHit(URI uri, Object entry);

    }
    
    void setDbClientContext(DbClientContext dbClientContext);

    /**
     * Execute this query
     * 
     * todo make it return stuff in chunks
     * 
     * @return
     */
    <T> void execute(QueryResult<T> result);

    /**
     * Return the data objec type for this query
     * 
     * @return
     */
    Class<? extends DataObject> getDataObjectType();

    ConstraintDescriptor toConstraintDescriptor();
}
