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

package com.emc.storageos.db.client;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.joiner.Joiner;

/**
 * @author cgarber
 * 
 */
public interface DbModelClient {
    /**
     * retrieves one DbObject from persistence based on the class type and id
     * 
     * @param clazz class type to retrieve
     * @param id id of the object to retrieve
     * @return one DataObject instance
     * @throws DatabaseException
     */
    <T extends DataObject> T find(Class<T> clazz, URI id);

    /**
     * retrieves one {@link DataObject} instance from persistence based on a unique alternate Id.
     * Returns the first instance found and does not check that only one instance is found.
     * 
     * @param clazz
     * @param the name of the field that is an unique alternate id
     * @param value the value of the unique alternate id
     * @return one {@link DataObject} instance
     * @throws DatabaseException
     */
    <T extends DataObject> T findByUniqueAlternateId(Class<T> clazz, String field, String value);

    /**
     * retrieves all DbObjects of a certain type
     * 
     * @param clazz class type to retrieve
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    <T extends DataObject> Iterator<T> findAll(Class<T> clazz);

    /**
     * retrieves all DbObjects of a certain type
     * 
     * @param clazz class type to retrieve
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    <T extends DataObject> Iterator<T> find(Class<T> clazz, URI... ids);

    /**
     * retrieves all DbObjects of a certain type
     * 
     * @param clazz class type to retrieve
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    <T extends DataObject> Iterator<T> find(Class<T> clazz, Collection<URI> ids);

    /**
     * retrieves all DbObjects of a certain type filtered by one String field's value
     * 
     * @param clazz class type to retrieve
     * @param field field name to filter on
     * @param value field value to filter on
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    <T extends DataObject> Iterator<T> find(Class<T> clazz, String field, Object... value);

    /**
     * retrieves all DbObjects of a certain type filtered by one String field's value
     * 
     * @param clazz class type to retrieve
     * @param field field name to filter on
     * @param value field value to filter on
     * @return iterator pointing to the resulting list of DataObjects
     * @throws DatabaseException
     */
    <T extends DataObject> Iterator<T> find(Class<T> clazz, String field, Collection<? extends Object> value);

    /**
     * retrieves all DbObjects with a specified label
     * 
     * @param clazz
     * @param label
     * @return
     */
    <T extends DataObject> Iterator<T> findByLabel(Class<T> clazz, String label);

    /**
     * retrieves all DbObjects with a specified native GUID
     * 
     * @param clazz
     * @param nativeGuid
     * @return
     */
    <T extends DataObject> Iterator<T> findByNativeGuid(Class<T> clazz, String nativeGuid);

    /**
     * Defines an initial table that is queried without joining to some previous table
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @return
     */
    Joiner join(Class<? extends DataObject> clazz, String alias);

    /**
     * Defines an initial set of objects that are queried without joining to some previous table
     * Objects identified by ids are queried from the database
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param ids initial list of ids to start the join with
     * @return
     */
    Joiner join(Class<? extends DataObject> clazz, String alias, URI... ids);

    /**
     * Defines an initial set of objects that are queried without joining to some previous table
     * Objects identified by ids are queried from the database
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param ids initial list of ids to start the join with
     * @return
     */
    <T extends DataObject> Joiner join(Class<T> clazz, String alias, T... objs);

    /**
     * Defines an initial set of objects that are queried without joining to some previous table
     * Objects identified by ids are queried from the database
     * 
     * @param clazz defines the table to start the join with
     * @param alias name for the results of this table
     * @param filter initial list of ids or DataObjects to start the join with
     * @return
     */
    Joiner join(Class<? extends DataObject> clazz, String alias, Collection filter);

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
    Joiner join(Class<? extends DataObject> clazz, String alias, String field, Object... value);

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
    Joiner join(Class<? extends DataObject> clazz, String alias, String field, Collection<? extends Object> value);

    /**
     * persists the modified fields of one or more DataObjects
     * 
     * @param objs DataObjects to persist
     * @throws DatabaseException
     */
    <T extends DataObject> void update(T... objs);

    /**
     * persists the modified fields of one or more DataObjects
     * 
     * @param objs DataObjects to persist
     * @throws DatabaseException
     */
    void update(Collection<? extends DataObject> objs);

    /**
     * creates one or more new DbObjects in persistence
     * 
     * @param obj DataObjects to persist
     * @throws DatabaseException
     */
    <T extends DataObject> void create(T... objs);

    /**
     * creates one or more new DbObjects in persistence
     * 
     * @param obj DataObjects to persist
     * @throws DatabaseException
     */
    <T extends DataObject> void create(Collection<T> objs);

    /**
     * removes one or more DataObjects from persistence
     * 
     * @param obj
     * @throws DatabaseException
     */
    <T extends DataObject> void remove(T... objs);

    /**
     * removes one or more DataObjects from persistence
     * 
     * @param obj
     * @throws DatabaseException
     */
    void remove(Collection<? extends DataObject> objs);

}
