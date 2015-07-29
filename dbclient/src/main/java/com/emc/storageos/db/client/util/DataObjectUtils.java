/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * A utility class for implementing {@link DataObject} common functions.
 * 
 * @author elalih
 * 
 */
public class DataObjectUtils {
    /**
     * This function returns the property value for an object of {@link DataObject} subtype.
     * 
     * @param clzz the object class that should be a subtype of {@link DataObject}
     * @param dataObject the instance of clzz
     * @param property the string name of the property
     * @return the value of the property
     */
    public static <T extends DataObject> Object getPropertyValue(Class<T> clzz, DataObject dataObject, String property) {
        try {
            DataObjectType doType = TypeMap.getDoType(clzz);
            ColumnField field = doType.getColumnField(property);
            return field.getPropertyDescriptor().getReadMethod().invoke(dataObject);
        } catch (Exception ex) {
            throw DatabaseException.fatals.failedToReadPropertyValue(clzz, property, ex);
        }
    }

    /**
     * This method invokes a parameterless function. The function is expected to return a value.
     * This is use when some manipulation of the property is required
     * 
     * @param clzz the data object class
     * @param dataObject the data object
     * @param methodName the name of the method
     * @return
     * @throws Exception
     */
    public static <T extends DataObject> Object invokeMethod(Class<T> clzz, DataObject dataObject,
            String methodName) throws Exception {
        Method method = String.class.getDeclaredMethod(methodName, new Class[] {});
        return (Object) method.invoke(dataObject, new Object[] {});

    }

    /**
     * Finds an DataObject in a collection by matching it by Id
     * 
     * @param col the collection
     * @param obj the object to be found
     * @return the object in the collection with the same Id and obj. Returns null if no match is found.
     */
    public static <T extends DataObject> T findInCollection(Collection<T> col, T obj) {
        if (col != null && obj != null) {
            return findInCollection(col, obj.getId());
        }
        return null;
    }

    /**
     * Returns if the object is found in the collection when the collection contains
     * and object of the same Id but different instance.
     * 
     * @param col the collection
     * @param obj the object being searched for
     * @return true if an object with the same Id is found; false otherwise.
     */
    public static <T extends DataObject> boolean collectionContains(Collection<T> col, T obj) {
        return findInCollection(col, obj) != null;
    }

    /**
     * Finds an DataObject in a collection by matching it by Id
     * 
     * @param col the collection
     * @param id the object id URI
     * @return the object in the collection with the ID id. Returns null if no match is found.
     */
    public static <T extends DataObject> T findInCollection(Collection<T> col, URI id) {
        if (col != null && id != null) {
            for (T t : col) {
                if (t.getId().equals(id)) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Finds an DataObject in a collection by matching it by Id
     * 
     * @param col the collection
     * @param id the object id URI as a string
     * @return the object in the collection with the ID id. Returns null if no match is found.
     */
    public static <T extends DataObject> T findInCollection(Collection<T> col, String id) {
        if (col != null && id != null) {
            for (T t : col) {
                if (t.getId().toString().equals(id)) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Returns a map of DataObject by Id
     * 
     * @param col the collection being changed into a map
     * @return a map of DataObject by Id
     */
    public static <T extends DataObject> Map<URI, T> toMap(Collection<T> col) {
        Map<URI, T> map = new HashMap<URI, T>();
        if (col != null) {
            for (T t : col) {
                map.put(t.getId(), t);
            }
        }
        return map;
    }

    /**
     * Finds an DataObject in a collection by matching its property to a value. This method
     * assumes only one object in the collection can be matched.
     * 
     * @param col the collection
     * @param property the property to be checked
     * @param value the property value to be matched.
     * @return the object in the collection for which the property value matched the passed in
     *         value. Returns null if no match is found.
     */
    public static <T extends DataObject> T findByProperty(Collection<T> col, String property, Object value) {
        if (col != null && property != null) {
            Object val = null;
            for (T t : col) {
                val = getPropertyValue(t.getClass(), t, property);
                if ((val == value) || (val != null && val.equals(value))) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Utility functions that returns the iterator entries as a list
     * 
     * @param itr the iterator
     * @return a list that holds the iterator entries
     */
    public static List<URI> iteratorToList(URIQueryResultList itr) {
        List<URI> uris = new ArrayList<URI>();
        for (URI uri : itr) {
            uris.add(uri);
        }
        return uris;
    }

    /**
     * Utility functions that returns the iterator entries as a list
     * 
     * @param itr the iterator
     * @return a list that holds the iterator entries
     */
    public static <T extends DataObject> List<T> iteratorToList(Iterator<T> itr) {
        List<T> objs = new ArrayList<T>();
        while (itr.hasNext()) {
            objs.add(itr.next());
        }
        return objs;
    }
}
