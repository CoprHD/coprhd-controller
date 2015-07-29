/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.ColumnField.ColumnType;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.DbClientCallbackEvent;
import com.emc.storageos.db.joiner.Joiner;

/**
 * @author cgarber
 * 
 */
public class LazyLoader {

    /**
     * 
     */
    private static final String JOINER_ALIAS_ONE = "one";

    /**
     * 
     */
    private static final String JOINER_ALIAS_TWO = "two";

    private static final Logger log = LoggerFactory.getLogger(LazyLoader.class);

    private DbClient dbClient;

    /**
     * @param dbClient
     * @param _doType
     */
    public LazyLoader(DbClient dbClient) {
        super();
        this.dbClient = dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    private ColumnField getMappedByField(ColumnField col, DataObjectType doType) {
        ColumnField mappedByCol = doType.getColumnField(col.getMappedByField());
        if (mappedByCol == null) {
            mappedByCol = TypeMap.getDoType(col.getMappedByType()).getColumnField(col.getMappedByField());
        }
        return mappedByCol;
    }

    /**
     * @param clazz class type of the return list
     * @param parentId id of the owning object
     * @param col field to be lazy loaded
     * @param retList return list
     */
    public <T extends DataObject> Iterator<T> load(String lazyLoadedFieldName, T obj, Collection<T> collection, DbClientCallbackEvent cb) {
        DataObjectType doType = TypeMap.getDoType(obj.getClass());
        ColumnField lazyLoadedField = doType.getColumnField(lazyLoadedFieldName);
        if (lazyLoadedField == null) {
            throw new IllegalStateException(
                    String.format(
                            "lazy loaded field %s in class %s not found; make sure the argument passed into refreshMappedByField matches the @Name annotation on the getter method",
                            lazyLoadedFieldName, obj.getClass()));
        }
        ColumnField mappedByField = getMappedByField(lazyLoadedField, doType);
        if (mappedByField == null) {
            throw new IllegalStateException(String.format("lazy loaded field %s in class %s could not be found;"
                    + " make sure the mappedBy argument in the @Relation annotation matches the @Name annotation on the mapped by field",
                    lazyLoadedFieldName, obj.getClass()));
        }
        Joiner j = queryObjects(obj, cb, lazyLoadedField, mappedByField, JOINER_ALIAS_TWO);
        if (collection != null) {
            collection.addAll((Collection<? extends T>) j.list(JOINER_ALIAS_TWO));
        }
        return j.iterator(JOINER_ALIAS_TWO);
    }

    /**
     * @param parentObj
     * @param cb
     * @param col
     * @param mappedByCol
     * @return
     */
    private Joiner
            queryObjects(DataObject parentObj, DbClientCallbackEvent cb, ColumnField col, ColumnField mappedByCol, String joinerAlias) {
        Joiner j = new Joiner(dbClient);
        if (mappedByCol.getType().equals(ColumnType.TrackingSet)) {
            // for instance A has a list of instances B
            // the mapped by field is a StringSet within the same class as the lazy loaded list (instance A)
            try {
                Object val = mappedByCol.getPropertyDescriptor().getReadMethod().invoke(parentObj);
                if (val == null) {
                    return null;
                }
                if (AbstractChangeTrackingSet.class.isAssignableFrom(val.getClass())) {
                    if (isStringSetOfURIs(val)) {
                        j.join(col.getMappedByType(), joinerAlias, stringSetToURISet((StringSet) val)).go();
                    } else {
                        // TODO either support or throw an unsupported exception
                        // this would cover instances where we want to join on a non-URI type field
                        // and is not currently supported by the joiner class
                        j.join(parentObj.getClass(), JOINER_ALIAS_ONE, parentObj.getId())
                                .join(JOINER_ALIAS_ONE, mappedByCol.getName(), col.getMappedByType(), joinerAlias).go();
                    }
                    // call the setCallback method on the mapped field
                    // the callback is used to inalidate the lazy loaded list if the StringSet changes
                    ((AbstractChangeTrackingSet) val).setCallback(cb);
                }

            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                // TODO Auto-generated catch block
                log.error("could not set callback method in mapped by field " + mappedByCol.getName() + " for lazy loaded field " +
                        col.getName() + " in memory values of the lazy loaded list may become stale if the TrackingSet is modified");
                log.error(e.getMessage(), e);
            }
        } else {
            // for instance A has a list of instances B
            // the mapped by field is a URI or NamedURI field in each instance B
            j.join(parentObj.getClass(), JOINER_ALIAS_ONE, parentObj.getId())
                    .join(JOINER_ALIAS_ONE, col.getMappedByType(), JOINER_ALIAS_TWO, mappedByCol.getName()).go();
        }
        return j;
    }

    private Set<URI> stringSetToURISet(StringSet objs) {
        Set<URI> ret = new HashSet<URI>();
        for (String obj : objs) {
            ret.add(URI.create(obj));
        }
        return ret;
    }

    /**
     * @param val
     * @return
     */
    private boolean isStringSetOfURIs(Object val) {
        // if only we had URISet extends AbstractChangeTrackingSet<URI>
        if (StringSet.class.isAssignableFrom(val.getClass()) &&
                ((StringSet) val).iterator().hasNext()) {
            return true;
        }
        return false;
    }

    /**
     * @param lazyLoadedFieldName
     * @param _id
     * @param fieldValue
     */
    public <T extends DataObject> void load(String lazyLoadedFieldName, DataObject obj) {
        DataObjectType doType = TypeMap.getDoType(obj.getClass());
        ColumnField lazyLoadedField = doType.getColumnField(lazyLoadedFieldName);

        if (lazyLoadedField == null) {
            throw new IllegalStateException(
                    String.format(
                            "lazy loaded field %s in class %s not found; make sure the argument passed into refreshMappedByField matches the @Name annotation on the getter method",
                            lazyLoadedFieldName, obj.getClass()));
        }

        if (!lazyLoadedField.isLazyLoaded()) {
            log.debug("skipping; field %s in class %s is not a lazy loadable field", lazyLoadedFieldName, obj.getClass());
            return;
        }

        if (!DataObject.class.isAssignableFrom(lazyLoadedField.getPropertyDescriptor().getPropertyType())) {
            log.debug("skipping; field %s in class %s is a collection; lazy loading is handled by LazyLoadedCollection",
                    lazyLoadedFieldName, obj.getClass());
            return;
        }

        // make sure the lazy loaded field has a setter method
        Method lazyLoadedFieldWriteMethod = lazyLoadedField.getPropertyDescriptor().getWriteMethod();
        if (lazyLoadedFieldWriteMethod == null) {
            throw new IllegalStateException(String.format("lazy loaded field %s in class %s must have a write method", lazyLoadedFieldName,
                    obj.getClass()));
        }

        try {

            T retObj = null;
            ColumnField mappedByField = doType.getColumnField(lazyLoadedField.getMappedByField());
            if (mappedByField == null) {

                // mapped by field is a collection in the related class; use joiner to get the lazy loaded object

                mappedByField = TypeMap.getDoType(lazyLoadedField.getMappedByType()).getColumnField(lazyLoadedField.getMappedByField());
                if (mappedByField == null) {
                    throw new IllegalStateException(
                            String.format(
                                    "lazy loaded field %s in class %s could not be found;"
                                            + " make sure the mappedBy argument in the @Relation annotation matches the @Name annotation on the mapped by field",
                                    lazyLoadedFieldName, obj.getClass()));
                }
                Joiner j = new Joiner(dbClient);
                j.join(obj.getClass(), JOINER_ALIAS_ONE, obj.getId())
                        .join(JOINER_ALIAS_ONE, lazyLoadedField.getMappedByType(), JOINER_ALIAS_TWO, mappedByField.getName()).go();
                if (j.iterator(JOINER_ALIAS_TWO).hasNext()) {
                    retObj = (T) j.iterator(JOINER_ALIAS_TWO).next();
                }
            } else {

                // the mapped by field is a URI field in the same class as the lazy loaded field

                Method mappedByFieldReadMethod = mappedByField.getPropertyDescriptor().getReadMethod();
                if (mappedByFieldReadMethod == null) {
                    throw new IllegalStateException(String.format(
                            "mapped by field %s mapped to lazy loaded field %s in class %s must have a read method",
                            mappedByField.getName(), lazyLoadedFieldName, obj.getClass()));
                }

                // check the mapped by type is URI (supported type)
                Class mappedByObjType = mappedByFieldReadMethod.getReturnType();
                if (!URI.class.isAssignableFrom(mappedByObjType)) {
                    throw new IllegalStateException(String.format(
                            "lazy loaded field %s in class %s has mapped by field %s with an unsupported type: %s;"
                                    + " the mapped by field for a DataObject must be a URI",
                            lazyLoadedFieldName, obj.getClass(), mappedByField.getName(), mappedByObjType.getName()));
                }

                URI id = (URI) mappedByFieldReadMethod.invoke(obj);

                // id could be null if the mapped by field is not set to anything in persistence
                if (id != null) {
                    retObj = (T) dbClient.queryObject(lazyLoadedField.getMappedByType(), id);
                }
            }
            lazyLoadedFieldWriteMethod.invoke(obj, retObj);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * refreshes the mapped by field when the lazy loaded field is replaced by another value
     * 
     * @param obj
     * @param lazyLoadedFieldName
     */
    public void refreshMappedByField(String lazyLoadedFieldName, DataObject obj) {

        DataObjectType doType = TypeMap.getDoType(obj.getClass());

        // make sure the lazy loaded field is a valid field
        ColumnField lazyLoadedField = doType.getColumnField(lazyLoadedFieldName);
        if (lazyLoadedField == null) {
            throw new IllegalStateException(
                    String.format(
                            "lazy loaded field %s in class %s not found; make sure the argument passed into refreshMappedByField matches the @Name annotation on the getter method",
                            lazyLoadedFieldName, obj.getClass()));
        }

        // make sure the lazy loaded field has a getter and setter
        Method lazyLoadedFieldReadMethod = lazyLoadedField.getPropertyDescriptor().getReadMethod();
        if (lazyLoadedFieldReadMethod == null) {
            throw new IllegalStateException(String.format("lazy loaded field %s in class %s must have a read method", lazyLoadedFieldName,
                    obj.getClass()));
        }

        // make sure the lazy loaded field is a supported type
        Class lazyLoadedObjType = doType.getColumnField(lazyLoadedFieldName).getPropertyDescriptor().getPropertyType();
        if (!Set.class.isAssignableFrom(lazyLoadedObjType) &&
                !List.class.isAssignableFrom(lazyLoadedObjType) &&
                !DataObject.class.isAssignableFrom(lazyLoadedObjType)) {
            throw new IllegalStateException(String.format("lazy loaded field %s in class %s is an unsupported type: %s; "
                    + "supported type are DataObject, List and Set", lazyLoadedFieldName, obj.getClass(), lazyLoadedObjType.getName()));
        }

        // get the mapped by field
        ColumnField mappedByField = null;
        if (doType != null) {
            String mappedByFieldName = doType.getColumnField(lazyLoadedFieldName).getMappedByField();
            mappedByField = doType.getColumnField(mappedByFieldName);
            if (mappedByField == null) {
                // mappedByField will be null if the mapped by field is in another class
                // in this case, we can't sync up the the lazy loaded field with the mapped by field
                return;
            }
        }

        // make sure the lazy loaded field has a getter and setter
        Method mappedByFieldReadMethod = mappedByField.getPropertyDescriptor().getReadMethod();
        Method mappedByFieldWriteMethod = mappedByField.getPropertyDescriptor().getWriteMethod();
        if (mappedByFieldReadMethod == null || mappedByFieldWriteMethod == null) {
            throw new IllegalStateException(String.format(
                    "mapped by field %s mapped to lazy loaded field %s in class %s must have both a read method and a write method",
                    mappedByField.getName(), lazyLoadedFieldName, obj.getClass()));
        }

        // get lazy loaded object
        if (Collection.class.isAssignableFrom(lazyLoadedObjType)) {

            // make sure the mapped by type is a supported type (StringSet)
            Class mappedByObjType = mappedByField.getPropertyDescriptor().getReadMethod().getReturnType();
            if (!StringSet.class.isAssignableFrom(mappedByObjType)) {
                throw new IllegalStateException(String.format(
                        "lazy loaded field %s in class %s has mapped by field %s with an unsupported type: %s;"
                                + " the mappedby field for a collection must be a StringSet",
                        lazyLoadedFieldName, obj.getClass(), mappedByField.getName(), lazyLoadedObjType.getName()));
            }

            refreshMappedByStringSet(obj, lazyLoadedFieldReadMethod, mappedByFieldReadMethod, mappedByFieldWriteMethod, mappedByObjType);

        } else if (DataObject.class.isAssignableFrom(lazyLoadedObjType)) {

            Class mappedByObjType = mappedByFieldReadMethod.getReturnType();
            if (!URI.class.isAssignableFrom(mappedByObjType)) {
                throw new IllegalStateException(String.format(
                        "lazy loaded field %s in class %s has mapped by field %s with an unsupported type: %s;"
                                + " the mapped by field for a DataObject must be a URI",
                        lazyLoadedFieldName, obj.getClass(), mappedByField.getName(), mappedByObjType.getName()));
            }

            refreshMappedByDataObject(obj, lazyLoadedFieldReadMethod, mappedByFieldWriteMethod);
        }
    }

    /**
     * @param obj
     * @param lazyLoadedFieldReadMethod
     * @param mappedByFieldWriteMethod
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private void refreshMappedByDataObject(DataObject obj, Method lazyLoadedFieldReadMethod, Method mappedByFieldWriteMethod) {
        try {

            DataObject lazyLoadedObj = (DataObject) lazyLoadedFieldReadMethod.invoke(obj);
            if (lazyLoadedObj == null) {
                mappedByFieldWriteMethod.invoke(obj, null);
            } else {
                mappedByFieldWriteMethod.invoke(obj, lazyLoadedObj.getId());
            }

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // we've done all the checking we can; if we end up here, it's a programming error
            log.error(e.getMessage(), e);
        }
    }

    /**
     * @param obj
     * @param lazyLoadedFieldReadMethod
     * @param mappedByFieldReadMethod
     * @param mappedByFieldWriteMethod
     * @param mappedByObjType
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
    private void refreshMappedByStringSet(DataObject obj, Method lazyLoadedFieldReadMethod, Method mappedByFieldReadMethod,
            Method mappedByFieldWriteMethod, Class mappedByObjType) {

        try {
            Collection<DataObject> lazyLoadedFieldValue = (Collection) lazyLoadedFieldReadMethod.invoke(obj);
            StringSet mappedByFieldValue = (StringSet) mappedByFieldReadMethod.invoke(obj);

            // if the lazy loaded collection is null or empty, clear the mapped by stringset;
            // otherwise, set the mapped by stringset to the list of id's in the lazy loaded collection
            if (lazyLoadedFieldValue == null || lazyLoadedFieldValue.isEmpty()) {
                if (mappedByFieldValue != null) {
                    mappedByFieldValue.clear();
                    mappedByFieldWriteMethod.invoke(obj, mappedByFieldValue);
                }
            } else {
                if (mappedByFieldValue == null) {
                    mappedByFieldValue = (StringSet) mappedByObjType.newInstance();
                }
                DbClientCallbackEvent cb = mappedByFieldValue.getCallback();
                mappedByFieldValue.setCallback(null);
                copyCollectionToStringSet(lazyLoadedFieldValue, mappedByFieldValue);
                mappedByFieldValue.setCallback(cb);
                mappedByFieldWriteMethod.invoke(obj, mappedByFieldValue);
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            // we've done all the checking we can; if we end up here, it's a programming error
            log.error(e.getMessage(), e);
        }
    }

    /**
     * @param lazyLoadedFieldValue
     * @param mappedByFieldValue
     */
    private void copyCollectionToStringSet(Collection<DataObject> lazyLoadedFieldValue, StringSet mappedByFieldValue) {
        Set<String> newSet = new HashSet<String>();
        for (DataObject listElem : lazyLoadedFieldValue) {
            newSet.add(listElem.getId().toString());
        }
        HashSet<String> toBeRemoved = new HashSet<String>();
        for (String id : mappedByFieldValue) {
            if (!newSet.contains(id)) {
                toBeRemoved.add(id);
            }
        }
        mappedByFieldValue.removeAll(toBeRemoved);
        for (DataObject snapshot : lazyLoadedFieldValue) {
            mappedByFieldValue.add(snapshot.getId().toString());
        }
    }

}
