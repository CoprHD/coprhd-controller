/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.Transient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.serializers.StringSerializer;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * Encapsulates data object type information
 */
public class DataObjectType {
    private static final Logger _log = LoggerFactory.getLogger(DataObjectType.class);
    private ColumnFamily<String, CompositeColumnName> _cf;
    private Class<? extends DataObject> _clazz;
    private ColumnField _idField;
    private Map<String, ColumnField> _columnFieldMap = new HashMap<String, ColumnField>();
    private EncryptionProvider _encryptionProvider;
    private List<ColumnField> _preprocessedFields;

    private Class<? extends DataObject> _instrumentedClazz;
    // a map of mapped by field name to its associated lazy loaded field
    // only for mapped by fields within the same class as the lazy loaded field
    private Map<String, ColumnField> _mappedByToLazyLoadedField;
    // a list of lazy loaded field for this class
    private List<ColumnField> _lazyLoadedFields;

    /**
     * Constructor
     * 
     * @param clazz data object class
     */
    public DataObjectType(Class<? extends DataObject> clazz) {
        _clazz = clazz;
        _preprocessedFields = new ArrayList<ColumnField>();
        _lazyLoadedFields = new ArrayList<ColumnField>();
        _mappedByToLazyLoadedField = new HashMap<String, ColumnField>();
        init();
    }

    /**
     * Sets encryption provider
     * 
     * @param encryptionProvider
     */
    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        _encryptionProvider = encryptionProvider;
    }

    /**
     * Get encryption provider
     * 
     * @return
     */
    public EncryptionProvider getEncryptionProvider() {
        return _encryptionProvider;
    }

    /**
     * Returns data object type
     * 
     * @return
     */
    public Class<? extends DataObject> getDataObjectClass() {
        return _clazz;
    }

    /**
     * Column family for this data object type
     * 
     * @return
     */
    public ColumnFamily<String, CompositeColumnName> getCF() {
        return _cf;
    }

    /**
     * Get column field with given name
     * 
     * @param name
     * @return
     */
    public ColumnField getColumnField(String name) {
        return (name != null && name.equals(_idField.getName())) ? _idField : _columnFieldMap.get(name);
    }

    /**
     * Return all column fields in this data object type
     * 
     * @return
     */
    public Collection<ColumnField> getColumnFields() {
        return Collections.unmodifiableCollection(_columnFieldMap.values());
    }

    public static boolean isColumnField(String className, PropertyDescriptor pd) {
        if (pd.getName().equals("class")) {
            return false;
        }

        Method readMethod = pd.getReadMethod();
        Method writeMethod = pd.getWriteMethod();
        if (readMethod == null || writeMethod == null) {
            _log.info("{}.{} no getter or setter method, skip", className, pd.getName());
            return false;
        }
        // Skip Transient Properties
        if (readMethod.isAnnotationPresent(Transient.class) || writeMethod.isAnnotationPresent(Transient.class)) {
            _log.info("{}.{} has Transient annotation, skip", className, pd.getName());
            return false;
        }
        return true;
    }

    /**
     * Initializes cf & all column metadata for this data type
     */
    private void init() {
        String cfName = _clazz.getAnnotation(Cf.class).value();
        _cf = new ColumnFamily<String, CompositeColumnName>(cfName,
                StringSerializer.get(),
                CompositeColumnNameSerializer.get());
        BeanInfo bInfo;
        try {
            bInfo = Introspector.getBeanInfo(_clazz);
        } catch (IntrospectionException ex) {
            throw DatabaseException.fatals.serializationFailedInitializingBeanInfo(_clazz, ex);
        }
        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();

        for (int i = 0; i < pds.length; i++) {
            PropertyDescriptor pd = pds[i];
            // skip class property
            if (!isColumnField(bInfo.getBeanDescriptor().getBeanClass().getName(), pd)) {
                _log.info("Not column field, skip {}.{}", bInfo.getBeanDescriptor().getBeanClass().getName(), pd.getName());
                continue;
            }

            ColumnField col = new ColumnField(this, pd);
            if (col.getType() == ColumnField.ColumnType.Id) {
                _idField = col;
                continue;
            }
            _columnFieldMap.put(col.getName(), col);

            if (col.isLazyLoaded()) {
                _lazyLoadedFields.add(col);
            }
        }

        // Need to resolve field cross references here....
        Collection<ColumnField> fields = _columnFieldMap.values();
        for (ColumnField field : fields) {
            DbIndex index = field.getIndex();
            if (index instanceof AggregateDbIndex) {
                String[] groupByArr = ((AggregateDbIndex) index).getGroupBy();
                for (String groupByName : groupByArr) {
                    ColumnField groupField = _columnFieldMap.get(groupByName);
                    // Right now the "group field must have its own index.
                    // The index for this field will be cleared together with the index of the referenced field
                    if (groupField == null || groupField.getIndex() == null) {
                        DatabaseException.fatals.invalidAnnotation("AggregateIndex", "property " + groupByName +
                                " does not have a valid value or referenced another indexed field");
                    }
                    ((AggregateDbIndex) index).addGroupByField(_columnFieldMap.get(groupByName));
                    if (groupField != null && groupField.getDependentFields() != null) {
                        groupField.getDependentFields().add(field);
                    }
                    field.getRefFields().add(groupField);
                }
                if (!field.getRefFields().isEmpty()) {
                    _preprocessedFields.add(field);
                }
            }
        }

        // initialization for lazy loading
        lazyLoadInit();

    }

    public boolean needPreprocessing() {
        return !_preprocessedFields.isEmpty();
    }

    /**
     * Deserializes row into data object instance
     * 
     * @param clazz data object class
     * @param row row
     * @param cleanupList old columns that need to be deleted
     * @param <T> data object class
     * @return data object instance
     * @throws DatabaseException
     */
    public <T extends DataObject> T deserialize(Class<T> clazz, Row<String, CompositeColumnName> row, IndexCleanupList cleanupList) {
        return deserialize(clazz, row, cleanupList, null);
    }

    public <T extends DataObject> T deserialize(Class<T> clazz, Row<String, CompositeColumnName> row, IndexCleanupList cleanupList,
            LazyLoader lazyLoader) {
        if (!_clazz.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException();
        }
        try {
            String key = row.getKey();
            Class<? extends DataObject> type = (_instrumentedClazz == null) ? clazz : _instrumentedClazz;
            DataObject obj = DataObject.createInstance(type, URI.create(row.getKey()));
            Iterator<Column<CompositeColumnName>> it = row.getColumns().iterator();
            while (it.hasNext()) {
                Column<CompositeColumnName> column = it.next();
                cleanupList.add(key, column);
                ColumnField columnField = _columnFieldMap.get(column.getName().getOne());
                if (columnField != null) {
                    columnField.deserialize(column, obj);
                } else {
                    _log.debug("an unexpected column in db, it might because geo system has multiple vdc but in different version");
                }
            }
            cleanupList.addObject(key, obj);
            obj.trackChanges();

            setLazyLoaders(obj, lazyLoader);

            return clazz.cast(obj);
        } catch (final InstantiationException e) {
            throw DatabaseException.fatals.deserializationFailed(clazz, e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.deserializationFailed(clazz, e);
        }
    }

    /**
     * Serializes data object into database updates
     * 
     * @param mutator row mutator to hold insertion queries
     * @param val object to persist
     * @throws DatabaseException
     */
    public boolean serialize(RowMutator mutator, DataObject val) {
        return serialize(mutator, val, null);
    }

    /**
     * Serializes data object into database updates
     * 
     * @param mutator row mutator to hold insertion queries
     * @param val object to persist
     * @param lazyLoader lazy loader helper class; can be null
     * @return
     * @throws DatabaseException
     */
    public boolean serialize(RowMutator mutator, DataObject val, LazyLoader lazyLoader) {
        if (!_clazz.isInstance(val)) {
            throw new IllegalArgumentException();
        }
        try {
            boolean indexFieldsModified = false;
            URI id = (URI) _idField.getPropertyDescriptor().getReadMethod().invoke(val);
            if (id == null) {
                throw new IllegalArgumentException();
            }
            for (ColumnField field : this._columnFieldMap.values()) {
                setMappedByField(val, field);
                indexFieldsModified |= field.serialize(val, mutator);
            }

            setLazyLoaders(val, lazyLoader);

            return indexFieldsModified;
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.serializationFailedId(val.getId(), e);
        } catch (final InvocationTargetException e) {
            throw DatabaseException.fatals.serializationFailedId(val.getId(), e);
        }
    }

    <T extends DataObject> void
            deserializeColumns(T object, Row<String, CompositeColumnName> row, List<ColumnField> columns, boolean clear) {

        Map<String, ColumnField> columnMap = new HashMap<String, ColumnField>();
        for (ColumnField column : columns) {
            columnMap.put(column.getName(), column);
        }
        Iterator<Column<CompositeColumnName>> it = row.getColumns().iterator();
        while (it.hasNext()) {
            Column<CompositeColumnName> column = it.next();
            ColumnField columnField = columnMap.get(column.getName().getOne());
            if (columnField != null) {
                columnField.deserialize(column, object);
            }
        }
        if (clear) {
            for (String columnField : columnMap.keySet()) {
                object.clearChangedValue(columnField);
            }
        }
        else {
            for (String columnField : columnMap.keySet()) {
                object.markChangedValue(columnField);
            }
        }
    }

    List<ColumnField> getRefUnsetColumns(DataObject object) {
        Map<String, ColumnField> refColumns = new HashMap<String, ColumnField>();
        for (ColumnField field : _preprocessedFields) {
            // we only with primitive types only, no StringSets, etc types are allowed
            if (!object.isChanged(field.getName())) {
                continue;
            }
            List<ColumnField> fields = field.getRefFields();
            for (ColumnField refField : fields) {
                if (!object.isChanged(refField.getName())) {
                    refColumns.put(refField.getName(), refField);
                }
            }
        }
        return new ArrayList<>(refColumns.values());
    }

    List<ColumnField> getDependentForModifiedColumns(DataObject object) {
        Map<String, ColumnField> depColumns = new HashMap<String, ColumnField>();
        Iterator<ColumnField> fieldIt = _columnFieldMap.values().iterator();
        while (fieldIt.hasNext()) {
            ColumnField field = fieldIt.next();
            // we deal only with primitive types only, no StringSets, etc types are allowed
            if (!field.getDependentFields().isEmpty() &&
                    object.isChanged(field.getName())) {
                for (ColumnField depField : field.getDependentFields()) {
                    if (!object.isChanged(depField.getName())) {
                        depColumns.put(depField.getName(), depField);
                    }
                }
            }
        }
        return new ArrayList<>(depColumns.values());
    }

    /**
     * sets up lazy loading for all lazy loading fields in this model class
     */
    private void lazyLoadInit() {
        for (ColumnField field : _lazyLoadedFields) {

            ColumnField mappedByField = getColumnField(field.getMappedByField());
            if (mappedByField != null) {
                _mappedByToLazyLoadedField.put(mappedByField.getName(), field);
            }
        }
        instrumentModelClasses();
    }

    /**
     * instrument model classes to override getter and setter methods to enable lazy loading
     */
    private void instrumentModelClasses() {

        long start = Calendar.getInstance().getTime().getTime();

        if (_lazyLoadedFields.isEmpty()) {
            // no error here; just means there are no lazy loaded fields in this model class
            return;
        }

        CtClass instrumentedClass = null;

        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass modelClass = pool.get(_clazz.getCanonicalName());
            String instrumentedClassName = _clazz.getPackage().getName() +
                    ".vipr-dbmodel$$" + _clazz.getSimpleName();
            instrumentedClass = pool.getAndRename(LazyLoadedDataObject.class.getName(), instrumentedClassName);
            if (instrumentedClass != null) {
                instrumentedClass.setSuperclass(modelClass);
            }
        } catch (CannotCompileException e) {
            _log.error(String.format("Compile error instrumenting data model class %s", _clazz.getCanonicalName()));
            _log.error(e.getMessage(), e);
            throw DatabaseException.fatals.serializationFailedClass(_clazz, e);
        } catch (NotFoundException e) {
            _log.error(String.format("Javassist could not find data model class %s", _clazz.getCanonicalName()));
            _log.error(e.getMessage(), e);
            throw DatabaseException.fatals.serializationFailedClass(_clazz, e);
        }
        long totalClassTime = Calendar.getInstance().getTime().getTime() - start;

        long startFieldTime = Calendar.getInstance().getTime().getTime();
        for (ColumnField field : _lazyLoadedFields) {
            PropertyDescriptor pd = field.getPropertyDescriptor();
            String fieldName = field.getName();

            try {

                CtClass modelClass = ClassPool.getDefault().get(pd.getReadMethod().getDeclaringClass().getCanonicalName());

                String quotedFieldName = "\"" + fieldName + "\"";
                if (DataObject.class.isAssignableFrom(pd.getPropertyType())) {

                    // override the getter for the lazy loaded object and add code to load the object
                    CtMethod readMethod = modelClass.getDeclaredMethod(pd.getReadMethod().getName());
                    // read method checks for isLoaded and loads if not loaded
                    CtMethod instReadMethod = CtNewMethod.delegator(readMethod, instrumentedClass);
                    String before = String.format("load(%s, this);", quotedFieldName);
                    _log.debug(String.format("creating new method %s for instrumented class %s: %s",
                            instReadMethod.getName(), instrumentedClass.getName(), before));
                    instReadMethod.insertBefore(before);
                    instrumentedClass.addMethod(instReadMethod);

                    // override the setter for the mapped by field and add code to invalidate the
                    // lazy loaded object (so that it will be re-loaded the next time it's accessed)
                    String mappedByFieldName = field.getMappedByField();
                    ColumnField mappedByField = getColumnField(mappedByFieldName);
                    if (mappedByField != null) {
                        CtMethod mappedByWriteMethod = modelClass.getDeclaredMethod(mappedByField.getPropertyDescriptor().getWriteMethod()
                                .getName());
                        CtMethod instMappedByWriteMethod = CtNewMethod.delegator(mappedByWriteMethod, instrumentedClass);
                        String mappedByCode = String.format("invalidate(%s);", quotedFieldName);
                        _log.debug(String.format("creating new method %s for instrumented class %s: %s",
                                instMappedByWriteMethod.getName(), instrumentedClass.getName(), mappedByCode));
                        instMappedByWriteMethod.insertAfter(mappedByCode);
                        instrumentedClass.addMethod(instMappedByWriteMethod);
                    }
                }

                CtMethod writeMethod = modelClass.getDeclaredMethod(pd.getWriteMethod().getName());
                CtMethod instWriteMethod = CtNewMethod.delegator(writeMethod, instrumentedClass);
                String writeMethodDef = String.format("refreshMappedByField(%s, this);", quotedFieldName);
                _log.debug(String.format("creating new method %s for instrumented class %s: %s",
                        instWriteMethod.getName(), instrumentedClass.getName(), writeMethodDef));
                instWriteMethod.insertAfter(writeMethodDef);
                instrumentedClass.addMethod(instWriteMethod);
            } catch (CannotCompileException e) {
                _log.error(String.format("Compile error instrumenting data model class %s", _clazz.getCanonicalName()));
                _log.error(e.getMessage(), e);
                throw DatabaseException.fatals.serializationFailedClass(_clazz, e);
            } catch (NotFoundException e) {
                _log.error(String.format("Field %s in data model class %s must have both a write method and a read method", fieldName,
                        _clazz.getCanonicalName()));
                _log.error(e.getMessage(), e);
                throw DatabaseException.fatals.serializationFailedClass(_clazz, e);
            }
        }

        long totalFieldTime = Calendar.getInstance().getTime().getTime() - startFieldTime;

        start = Calendar.getInstance().getTime().getTime();
        if (instrumentedClass != null) {
            try {
                _instrumentedClazz = instrumentedClass.toClass();
                // detach isn't necessary to get it to work, but it releases memory
                instrumentedClass.detach();
            } catch (CannotCompileException e) {
                _log.error(e.getMessage(), e);
            }
        }
        totalClassTime += Calendar.getInstance().getTime().getTime() - start;

        _log.info(String.format("Class instrumentation for %s: total time: %d; class time: %d; field time: %d; avg per field: %f",
                _clazz.getName(), totalClassTime + totalFieldTime, totalClassTime, totalFieldTime, (float) totalFieldTime
                        / (float) _lazyLoadedFields.size()));
    }

    private void setLazyLoaders(DataObject obj, LazyLoader lazyLoader) {

        if (lazyLoader == null) {
            return;
        }

        DataObjectInstrumented instrumentedObj = null;
        if (DataObjectInstrumented.class.isAssignableFrom(obj.getClass())) {
            instrumentedObj = (DataObjectInstrumented) obj;
            instrumentedObj.initLazyLoading(lazyLoader);
        }

        for (ColumnField lazyLoadedField : _lazyLoadedFields) {

            // if the mapped by field is a stringset in the same class, we need to keep that
            // in sync with the lazy loaded list, so find the mapped by field and set it in
            // the lazy loaded list
            StringSet mappedBy = null;
            ColumnField mappedByField = getColumnField(lazyLoadedField.getMappedByField());
            if (mappedByField != null) {
                try {
                    if (StringSet.class.isAssignableFrom(mappedByField.getPropertyDescriptor().getPropertyType())) {
                        Object mappedByFieldValue = mappedByField.getPropertyDescriptor().getReadMethod().invoke(obj);
                        if (mappedByFieldValue == null) {
                            mappedBy = (StringSet) mappedByField.getPropertyDescriptor().getPropertyType().newInstance();
                            mappedByField.getPropertyDescriptor().getWriteMethod().invoke(obj, mappedBy);
                        } else {
                            mappedBy = (StringSet) mappedByFieldValue;
                        }
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
                    // this is not an error -- if we can't call the getter for the stringset or create a new one, no
                    // one else can either and so there's no need to keep it in sync with the lazy loaded list
                    _log.warn(e.getMessage());
                }
            }
            lazyLoadedField.prepareForLazyLoading(obj, lazyLoader, mappedBy);
        }
        if (instrumentedObj != null) {
            // now that setup is complete, we can enable lazy loading
            instrumentedObj.enableLazyLoading();
        }
    }

    Class<? extends DataObject> getInstrumentedType() {
        return _instrumentedClazz;
    }

    // if the mapped by field for a lazy loaded field is null and the lazy loaded field is not null
    // we can set the mapped by field to match the lazy loaded field
    // this covers the case where we create a new DataObject instance, set the lazy loaded field
    // without setting the mapped by field and then persist it.
    private void setMappedByField(DataObject obj, ColumnField mappedByField) {

        ColumnField lazyLoadedField = _mappedByToLazyLoadedField.get(mappedByField.getName());
        if (lazyLoadedField == null) {
            return;
        }
        if (mappedByField.getPropertyDescriptor().getReadMethod() == null) {
            _log.error("mapped by field " + mappedByField.getName() + " for lazy loaded field " + lazyLoadedField.getName()
                    + " must have a read method");
            return;
        }
        if (lazyLoadedField.getPropertyDescriptor().getReadMethod() == null) {
            _log.error("lazy loaded field " + lazyLoadedField.getName() + " must have a read method");
            return;
        }
        try {
            Object mappedByValue = mappedByField.getPropertyDescriptor().getReadMethod().invoke(obj);
            Object lazyLoadedValue = lazyLoadedField.getPropertyDescriptor().getReadMethod().invoke(obj);

            if (null == mappedByValue && null != lazyLoadedValue) {
                if (DataObject.class.isAssignableFrom(lazyLoadedValue.getClass()) &&
                        URI.class.isAssignableFrom(mappedByField.getPropertyDescriptor().getPropertyType())) {
                    DataObject lazyLoadedDbObj = (DataObject) lazyLoadedValue;
                    mappedByField.getPropertyDescriptor().getWriteMethod().invoke(obj, lazyLoadedDbObj.getId());
                } else if (Collection.class.isAssignableFrom(lazyLoadedValue.getClass()) &&
                        StringSet.class.isAssignableFrom(mappedByField.getPropertyDescriptor().getPropertyType())) {
                    StringSet stringSet = new StringSet();
                    for (Object listElem : (Collection) lazyLoadedValue) {
                        if (DataObject.class.isAssignableFrom(listElem.getClass())) {
                            stringSet.add(((DataObject) listElem).getId().toString());
                        }
                    }
                    mappedByField.getPropertyDescriptor().getWriteMethod().invoke(obj, stringSet);
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            _log.error(e.getMessage(), e);
        }
    }
}
