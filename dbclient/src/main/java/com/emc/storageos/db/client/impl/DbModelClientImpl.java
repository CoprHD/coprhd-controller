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

package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.joiner.Joiner;

/**
 * @author cgarber
 *
 */
public class DbModelClientImpl implements DbModelClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(DbModelClientImpl.class);
    
    private DbClient dbClient;
    
    public DbModelClientImpl(DbClient client) {
        this.dbClient = client;
    }

    private DbModelClientImpl() {
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
     * retrieves one DbObject from persistence based on the class type and id
     * @param clazz class type to retrieve
     * @param id id of the object to retrieve
     * @return one DataObject instance
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> T find(Class<T> clazz, URI id) throws DatabaseException {
        return getDbClient().queryObject(clazz, id);
    }
    
    /**
     * retrieves all DbObjects of a certain type
     * @param clazz class type to retrieve
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> Iterator<T> findAll(Class<T> clazz) throws DatabaseException {
        return getDbClient().queryIterativeObjects(clazz, getDbClient().queryByType(clazz, true));
    }
    
    /**
     * retrieves all DbObjects of a certain type
     * @param clazz class type to retrieve
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> Iterator<T> find(Class<T> clazz, URI...ids) throws DatabaseException {
        return getDbClient().queryIterativeObjects(clazz, Arrays.asList(ids), true);
    }
    
    /**
     * retrieves all DbObjects of a certain type
     * @param clazz class type to retrieve
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> Iterator<T> find(Class<T> clazz, Collection<URI> ids) throws DatabaseException {
        return getDbClient().queryIterativeObjects(clazz, ids, true);
    }
    
    /**
     * retrieves all DbObjects of a certain type filtered by one String field's value
     * @param clazz class type to retrieve
     * @param field field name to filter on
     * @param value field value to filter on
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> Iterator<T> find(Class<T> clazz, String field, Object...value) throws DatabaseException {
        return join(clazz, "one", field, value).go().iterator("one");
    }
    
    /**
     * retrieves all DbObjects of a certain type filtered by one String field's value
     * @param clazz class type to retrieve
     * @param field field name to filter on
     * @param value field value to filter on
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> Iterator<T> find(Class<T> clazz, String field, Collection<? extends Object> value) throws DatabaseException {
        return join(clazz, "one", field, value).go().iterator("one");
    }
    
    /**
     * retrieves all DbObjects with a specified label
     * @param clazz
     * @param label
     * @return
     */
    @Override
    public <T extends DataObject> Iterator<T> findByLabel(Class<T> clazz, String label) {
        return join(clazz, "one", "label", label).go().iterator("one");
    }
    
    /**
     * retrieves all DbObjects with a specified native GUID
     * @param clazz
     * @param nativeGuid
     * @return
     */
    @Override
    public <T extends DataObject> Iterator<T> findByNativeGuid(Class<T> clazz, String nativeGuid) {
        return join(clazz, "one", "nativeGuid", nativeGuid).go().iterator("one");
    }
    
    /**
     * retrieves one {@link DataObject} instance from persistence based on a unique alternate Id.
     * Returns the first instance found and does not check that only one instance is found.
     * @param clazz
     * @param the name of the field that is an unique alternate id
     * @param value the value of the unique alternate id
     * @return one {@link DataObject} instance 
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> T findByUniqueAlternateId(Class<T> clazz, String field, String nativeGuid) {
        Iterator<T> itr = join(clazz, "one", field, nativeGuid).go().iterator("one");
        while (itr.hasNext()) {
            return itr.next();
        }
        return null;
    }
    
    /**
     * Defines an initial table that is queried without joining to some previous table
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @return
     */
    @Override
    public Joiner join(Class<? extends DataObject> clazz, String alias) {
        return new Joiner(getDbClient()).join(clazz, alias);
    }

    /** 
     * Defines an initial set of objects that are queried without joining to some previous table
     * Objects identified by ids are queried from the database
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param ids initial list of ids to start the join with
     * @return
     */
    @Override
    public Joiner join(Class<? extends DataObject> clazz, String alias, URI...ids) {
        return new Joiner(getDbClient()).join(clazz, alias, ids);
    }
    
    /** 
     * Defines an initial set of objects that are queried without joining to some previous table
     * Objects identified by ids are queried from the database
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param ids initial list of ids to start the join with
     * @return
     */
    @Override
    public <T extends DataObject> Joiner join(Class<T> clazz, String alias, T...objs) {
        return new Joiner(getDbClient()).join(clazz, alias, objs);
    }

    /** 
     * Defines an initial set of objects that are queried without joining to some previous table
     * Objects identified by ids are queried from the database
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param filter initial list of ids or DataObjects to start the join with
     * @return
     */
    @Override
    public Joiner join(Class<? extends DataObject> clazz, String alias, Collection filter) {
        return new Joiner(getDbClient()).join(clazz, alias, filter);
    }
    
    /**
     * Defines an initial set of objects that are queried without joining to some previous table
     * matches initial list of objects to join with field equal to or one of value
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param field field to match
     * @param value value to match field to
     * @return
     */
    @Override
    public Joiner join(Class<? extends DataObject> clazz, String alias, String field, Object...value) {
        Joiner j = new Joiner(getDbClient()).join(clazz, alias);
        if (value.length==1) {
            j.match(field, value[0]);
        } else {
            j.match(field, Arrays.asList(value));
        }
        return j;
    }

    /**
     * Defines an initial set of objects that are queried without joining to some previous table
     * matches initial list of objects to join with field equal to or one of value
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param field field to match
     * @param value value to match field to
     * @return
     */
    @Override
    public Joiner join(Class<? extends DataObject> clazz, String alias, String field, Collection<? extends Object> value) {
        return new Joiner(getDbClient()).join(clazz, alias).match(field, value);
    }

    /**
     * persists the modified fields of one or more DataObjects
     * @param objs DataObjects to persist
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> void update(T...objs) throws DatabaseException {
        this.update(Arrays.asList(objs));
    }
    
    /**
     * persists the modified fields of one or more DataObjects
     * @param objs DataObjects to persist
     * @throws DatabaseException
     */
    @Override
    public void update(Collection<? extends DataObject> objs) throws DatabaseException {
        getDbClient().persistObject(objs);
    }
    
    /**
     * creates one or more new DbObjects in persistence
     * @param obj DataObjects to persist
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> void create(T...objs) throws DatabaseException {
        this.create(Arrays.asList(objs));
    }
    
    /**
     * creates one or more new DbObjects in persistence
     * @param obj DataObjects to persist
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> void create(Collection<T> objs) throws DatabaseException {
        getDbClient().createObject(objs);
    }
    
    /**
     * removes one or more DataObjects from persistence
     * @param obj
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> void remove(T...objs) throws DatabaseException {
        this.remove(Arrays.asList(objs));
    }
    
    /**
     * removes one or more DataObjects from persistence
     * @param obj
     * @throws DatabaseException
     */
    @Override
    public void remove(Collection<? extends DataObject> objs) throws DatabaseException {
        getDbClient().markForDeletion(objs);
    }

}
