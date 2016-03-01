/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.emc.storageos.db.client.model.NoInactiveIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.AbstractChangeTrackingMap;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSetMap;
import com.emc.storageos.db.client.model.AbstractSerializableNestedObject;
import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.ClockIndependent;
import com.emc.storageos.db.client.model.ClockIndependentValue;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DecommissionedIndex;
import com.emc.storageos.db.client.model.Encrypt;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.Id;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.PermissionsIndex;
import com.emc.storageos.db.client.model.PrefixIndex;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelIndex;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Ttl;
import com.emc.storageos.db.client.model.AggregatedIndex;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ByteBufferRange;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.StringSerializer;

/**
 * Column / data object field type metadata
 */
public class ColumnField {
    private static final Logger _log = LoggerFactory.getLogger(ColumnField.class);

    // types of columns object mapper supports
    public static enum ColumnType {
        Id,
        Primitive,
        NamedURI,
        TrackingSet,
        TrackingMap,
        TrackingSetMap,
        NestedObject
    }

    // types of indexing object mapper supports
    public static enum IndexKind {
        Prefix,
        ScopedLabel,
        Relation,
        NamedRelation,
        AltId,
        Permissions,
        Decommissioned,
    }

    private final DataObjectType _parentType;
    private final PropertyDescriptor _property;
    private String _name;
    private ColumnType _colType;
    private final Class _valueType;
    private Class<? extends DataObject> _mappedByType;
    private String _mappedByField;
    private Integer _ttl;

    private DbIndex _index;
    private boolean _indexByKey;
    private Class<? extends DataObject> _indexRefType;

    // encrypted field?
    private boolean _encrypt;

    // time independent?
    private Class<? extends ClockIndependentValue> clockIndValue;

    // deactivate object when last referenced object is removed
    private boolean deactivateIfEmpty;

    // not set for MultiValue[List|Map] types
    private CompositeColumnName compositeName;

    private boolean lazyLoaded;

    public boolean isLazyLoaded() {
        return lazyLoaded;
    }

    private List<ColumnField> refFields = new ArrayList<>();
    private List<ColumnField> dependentFields = new ArrayList<>();

    // Reference and Dependent fields indicate cross-reference and/or dependence of fields of an object.
    // They are used when a field can not be serialized on its own and need reference fields to complete index.
    // Refenced Field is a filed of this Dataobject that is accessed by the current field during serialization
    public List<ColumnField> getRefFields() {
        return refFields;
    }

    // Dependent Fields depend on (i.e. might need) the current field for serialization
    public List<ColumnField> getDependentFields() {
        return dependentFields;
    }

    /**
     * Constructor
     * 
     * @param pd
     */
    public ColumnField(DataObjectType doType, PropertyDescriptor pd) {
        _parentType = doType;
        _property = pd;
        _valueType = _property.getPropertyType();
        processProperty();
    }

    /**
     * Get property descriptor for this field
     * 
     * @return
     */
    public PropertyDescriptor getPropertyDescriptor() {
        return _property;
    }

    /**
     * Column type
     * 
     * @return
     */
    public ColumnType getType() {
        return _colType;
    }

    /**
     * Column name or prefix (for set and map)
     * 
     * @return
     */
    public String getName() {
        return _name;
    }

    /**
     * Get index CF
     * 
     * @return
     */
    public ColumnFamily<String, IndexColumnName> getIndexCF() {
        return _index.getIndexCF();
    }

    /**
     * Get reference type of the indexed field
     * 
     * @return
     */
    public Class<? extends DataObject> getIndexRefType() {
        return _indexRefType;
    }

    /**
     * Get parent type as string
     * 
     * @return
     */
    public Class<? extends DataObject> getDataObjectType() {
        return _parentType.getDataObjectClass();
    }

    /**
     * Get whether the column is encrypted
     * 
     * @return true if encrypted, false otherwise.
     */
    public boolean isEncrypted() {
        return _encrypt;
    }

    /**
     * Sets/overrides TTL value
     */
    public void setTtl(Integer ttl) {
        this._ttl = ttl;
    }

    /**
     * Get current TTL configuration
     * 
     * @return
     */
    public Integer getTtl() {
        return _ttl;
    }

    /**
     * Get value of deactivateIfEmpty
     * 
     * @return
     */
    public boolean deactivateIfEmpty() {
        return deactivateIfEmpty;
    }

    /**
     * Returns prefix index row key
     * 
     * @param text
     * @return
     */
    public String getPrefixIndexRowKey(String text) {
        if (_index instanceof PrefixDbIndex) {
            return ((PrefixDbIndex) _index).getRowKey(text);
        }

        if (_index instanceof ScopedLabelDbIndex) {
            return ((ScopedLabelDbIndex) _index).getRowKey(text);
        }

        throw new RuntimeException(String.format("The index %s is not a PrefixDbIndex or ScopedLabelDbIndex", _index));
    }

    /**
     * Returns scoped prefix index row key
     * 
     * @param label
     * @return
     */
    public String getPrefixIndexRowKey(ScopedLabel label) {
        if (label.getScope() != null) {
            return String.format("%s:%s", label.getScope(), getPrefixIndexRowKey(label.getLabel()));
        } else {
            return getPrefixIndexRowKey(label.getLabel());
        }
    }

    /**
     * Build column slice range for given prefix
     * 
     * @param prefix
     * @return
     */
    public ByteBufferRange buildPrefixRange(String prefix, int pageSize) {
        String target = prefix.toLowerCase();
        return CompositeColumnNameSerializer.get().buildRange()
                .withPrefix(_parentType.getDataObjectClass().getSimpleName())
                .greaterThanEquals(target)
                .lessThanEquals(target + Character.MAX_VALUE)
                .limit(pageSize)
                .build();
    }

    /**
     * Build column slice range for given string
     * 
     * @param prefix
     * @return
     */
    public ByteBufferRange buildMatchRange(String prefix, int pageSize) {
        String target = prefix.toLowerCase();
        return CompositeColumnNameSerializer.get().buildRange()
                .withPrefix(_parentType.getDataObjectClass().getSimpleName())
                .greaterThanEquals(target)
                .lessThanEquals(target)
                .limit(pageSize)
                .build();
    }

    /**
     * Deserializes column into object field
     * 
     * @param column column to deserialize
     * @param obj object containing this field
     * @throws DatabaseException
     */
    public void deserialize(Column<CompositeColumnName> column, Object obj) {
        if (_encrypt && _parentType.getEncryptionProvider() != null) {
            deserializeEncryptedColumn(column, obj, _parentType.getEncryptionProvider());
        } else {
            ColumnValue.setField(column, _property, obj);
        }
    }

    /**
     * Deserializes an encrypted column into object field
     * 
     * @param column column to deserialize
     * @param obj object containing this field
     * @param encryptionProvider the encryption provider used to decrypt the column
     * @throws DatabaseException
     */
    public void deserializeEncryptedColumn(Column<CompositeColumnName> column, Object obj,
            EncryptionProvider encryptionProvider) {
        if (!_encrypt) {
            throw new IllegalArgumentException("column is not encrypted");
        }

        if (encryptionProvider == null) {
            throw new IllegalArgumentException("null encryption provider");
        }
        ColumnValue.setEncryptedStringField(column, _property, obj, encryptionProvider);
    }

    /**
     * Generate queries for removing given column. Caller is expected to execute generated queries
     * 
     * @param recordKey record row key
     * @param column column to
     * @param mutator row mutator that holds remove queries
     * @param fieldColumnMap column map for the record. it might need to remove indexes for dependent fields
     */
    public boolean removeColumn(String recordKey, Column<CompositeColumnName> column, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        CompositeColumnName columnName = column.getName();

        // remove record
        mutator.getRecordColumnList(_parentType.getCF(), recordKey).deleteColumn(columnName);

        if (_index == null || column instanceof ColumnWrapper || isDeletionMark(column)) {
            return false;
        }

        // remove index
        return _index.removeColumn(recordKey, column, _parentType.getDataObjectClass().getSimpleName(), mutator, fieldColumnMap);
    }

    private void addDeletionMark(String recordKey, CompositeColumnName colName, RowMutator mutator) {
        addColumn(recordKey, colName, null, mutator);
    }

    public static boolean isDeletionMark(Column<CompositeColumnName> column) {
        return !column.getByteBufferValue().hasRemaining();
    }

    private boolean removeColumn(String recordKey, Column<CompositeColumnName> column, RowMutator mutator) {
        return removeColumn(recordKey, column, mutator, null);
    }

    public boolean removeIndex(String recordKey, Column<CompositeColumnName> column, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap,
            DataObject obj) {

        // remove index
        return _index.removeColumn(recordKey, column, _parentType.getDataObjectClass().getSimpleName(), mutator, fieldColumnMap, obj);
    }

    public boolean removeIndex(String recordKey, Column<CompositeColumnName> column, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {

        // remove index
        return _index.removeColumn(recordKey, column, _parentType.getDataObjectClass().getSimpleName(), mutator, fieldColumnMap);
    }

    /**
     * Generate queries for inserting a given column. Caller is expected to
     * execute generated queries
     * 
     * @param recordKey record key
     * @param column column name
     * @param val column value
     * @param mutator mutator that holds insertion queries
     * @param obj for which the column is added
     * @throws DatabaseException
     */
    private boolean addColumn(String recordKey, CompositeColumnName column, Object val,
            RowMutator mutator, DataObject obj) {
        if (_encrypt && _parentType.getEncryptionProvider() != null) {
            val = _parentType.getEncryptionProvider().encrypt((String) val);
        }

        // insert record
        ColumnListMutation<CompositeColumnName> recordColList =
                mutator.getRecordColumnList(_parentType.getCF(), recordKey);
        ColumnValue.setColumn(recordColList, column, val, _ttl);

        if (_index == null || val == null) {
            return false;
        }

        // insert index
        return _index.addColumn(recordKey, column, val, _parentType.getDataObjectClass().getSimpleName(),
                mutator, _ttl, obj);
    }

    private boolean addColumn(String recordKey, CompositeColumnName column, Object val,
            RowMutator mutator) {
        return addColumn(recordKey, column, val, mutator, null);
    }

    /**
     * Serializes object field into database updates
     * 
     * @param obj data object to serialize
     * @param mutator row mutator to hold insertion queries
     * @return boolean
     * @throws DatabaseException
     */
    public boolean serialize(DataObject obj, RowMutator mutator) {
        try {
            String id = obj.getId().toString();

            if (isLazyLoaded() || _property.getReadMethod() == null) {
                return false;
            }

            Object val = _property.getReadMethod().invoke(obj);
            if (val == null) {
                return false;
            }
            boolean changed = false;
            switch (_colType) {
                case NamedURI:
                case Primitive: {
                    if (!obj.isChanged(_name)) {
                        return false;
                    }
                    changed = addColumn(id, getColumnName(null, mutator), val, mutator, obj);
                    break;
                }
                case TrackingSet: {
                    AbstractChangeTrackingSet valueSet = (AbstractChangeTrackingSet) val;
                    Set<?> addedSet = valueSet.getAddedSet();
                    if (addedSet != null) {
                        Iterator<?> it = valueSet.getAddedSet().iterator();
                        while (it.hasNext()) {
                            Object itVal = it.next();
                            String targetVal = valueSet.valToString(itVal);
                            changed |= addColumn(id, getColumnName(targetVal, mutator), itVal, mutator);
                        }
                    }
                    Set<?> removedVal = valueSet.getRemovedSet();
                    if (removedVal != null) {
                        Iterator<?> removedIt = removedVal.iterator();
                        while (removedIt.hasNext()) {
                            String targetVal = valueSet.valToString(removedIt.next());
                            if (_index == null) {
                                changed |= removeColumn(id, new ColumnWrapper(getColumnName(targetVal, mutator), targetVal), mutator);
                            } else {
                                addDeletionMark(id, getColumnName(targetVal, mutator), mutator);
                                changed = true;
                            }
                        }
                    }
                    break;
                }
                case TrackingMap: {
                    AbstractChangeTrackingMap valueMap = (AbstractChangeTrackingMap) val;
                    Set<String> changedSet = valueMap.getChangedKeySet();
                    if (changedSet != null) {
                        Iterator<String> it = valueMap.getChangedKeySet().iterator();
                        while (it.hasNext()) {
                            String key = it.next();
                            Object entryVal = valueMap.get(key);
                            CompositeColumnName colName = getColumnName(key, mutator);
                            if (clockIndValue != null) {
                                int ordinal = ((ClockIndependentValue) entryVal).ordinal();
                                colName = getColumnName(key, String.format("%08d", ordinal), mutator);
                            }
                            changed |= addColumn(id, colName, valueMap.valToByte(entryVal),
                                    mutator);
                        }
                    }
                    Set<String> removedKey = valueMap.getRemovedKeySet();
                    if (removedKey != null) {
                        Iterator<String> removedIt = removedKey.iterator();
                        while (removedIt.hasNext()) {
                            String key = removedIt.next();
                            CompositeColumnName colName = getColumnName(key, mutator);
                            if (clockIndValue != null) {
                                Object removedVal = valueMap.getRemovedValue(key);
                                if (removedVal != null) {
                                    colName = getColumnName(key, String.format("%08d",
                                            ((ClockIndependentValue) removedVal).ordinal()), mutator);
                                }
                            }

                            if (_index == null) {
                                changed |= removeColumn(id, new ColumnWrapper(colName, null), mutator);
                            } else {
                                addDeletionMark(id, colName, mutator);
                                changed = true;
                            }
                        }
                    }
                    break;
                }
                case TrackingSetMap: {
                    AbstractChangeTrackingSetMap valueMap = (AbstractChangeTrackingSetMap) val;

                    Set<String> keys = valueMap.keySet();
                    if (keys != null) {
                        Iterator<String> it = keys.iterator();

                        while (it.hasNext()) {
                            String key = it.next();
                            AbstractChangeTrackingSet valueSet = valueMap.get(key);
                            Set<?> addedSet = valueSet.getAddedSet();
                            if (addedSet != null) {
                                Iterator<?> itSet = valueSet.getAddedSet().iterator();
                                while (itSet.hasNext()) {
                                    String value = valueSet.valToString(itSet.next());
                                    changed |= addColumn(id, getColumnName(key, value, mutator), value, mutator);
                                }
                            }
                            Set<?> removedVal = valueSet.getRemovedSet();
                            if (removedVal != null) {
                                Iterator<?> removedIt = removedVal.iterator();
                                while (removedIt.hasNext()) {
                                    String targetVal = valueSet.valToString(removedIt.next());
                                    if (_index == null) {
                                        changed |= removeColumn(id,
                                                new ColumnWrapper(getColumnName(key, targetVal, mutator), targetVal), mutator);
                                    } else {
                                        addDeletionMark(id, getColumnName(key, targetVal, mutator), mutator);
                                        changed = true;
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
                case NestedObject: {
                    if (!obj.isChanged(_name)) {
                        break;
                    }
                    AbstractSerializableNestedObject nestedObject = (AbstractSerializableNestedObject) val;
                    changed |= addColumn(id, getColumnName(null, mutator), nestedObject.toBytes(), mutator);
                }
            }
            return changed;
        } catch (final InvocationTargetException e) {
            throw DatabaseException.fatals.serializationFailedId(obj.getId(), e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.serializationFailedId(obj.getId(), e);
        }
    }

    /**
     * Get column name for this field
     * 
     * @param two second component of column name
     * @param three third component of column name
     * @param mutator row mutator with timestamp
     * @return
     */
    private CompositeColumnName getColumnName(String two, String three, RowMutator mutator) {
        switch (_colType) {
            case Id: {
                return compositeName;
            }
            case NamedURI:
            case Primitive:
            case NestedObject: {
                if (isInactiveField() || needIndexConsistency()) {
                    return new CompositeColumnName(_name, null, mutator.getTimeUUID());
                } else {
                    return compositeName;
                }
            }
            case TrackingMap:
            case TrackingSet: {
                if (_index == null) {
                    return new CompositeColumnName(_name, two, three);
                } else {
                    return new CompositeColumnName(_name, two, three, mutator.getTimeUUID());
                }
            }
            case TrackingSetMap: {
                if (_index == null) {
                    return new CompositeColumnName(_name, two, three);
                } else {
                    return new CompositeColumnName(_name, two, three, mutator.getTimeUUID());
                }
            }
        }
        return null;
    }

    /**
     * Time UUID is always required for inactive field. We depends on the
     * timestamp to validate modification time of inactive field
     * 
     * @return
     */
    private boolean isInactiveField() {
        return _property.getName().equals(DataObject.INACTIVE_FIELD_NAME);
    }

    /**
     * Time UUID is required to ensure index CF and object CF consistency: both
     * are updated in single shot - all or nothing
     * 
     * @return
     */
    private boolean needIndexConsistency() {
        return _index != null && _index.needConsistency();
    }

    /**
     * Overload without third component
     * 
     * @param two
     * @param mutator
     * @return
     */
    private CompositeColumnName getColumnName(String two, RowMutator mutator) {
        return getColumnName(two, null, mutator);
    }

    /**
     * Sets the field as changed, for a complete overwrite
     * used from migration handlers to create index entries
     * 
     * @param obj
     * @return
     * @throws DatabaseException
     */
    public void setChanged(DataObject obj) {
        try {
            Object val = _property.getReadMethod().invoke(obj);
            if (val == null) {
                return;
            }
            switch (_colType) {
                case TrackingSet: {
                    AbstractChangeTrackingSet valueSet = (AbstractChangeTrackingSet) val;
                    valueSet.markAllForOverwrite();
                    break;
                }
                case TrackingMap: {
                    AbstractChangeTrackingMap valueMap = (AbstractChangeTrackingMap) val;
                    valueMap.markAllForOverwrite();
                    break;
                }
                case TrackingSetMap: {
                    AbstractChangeTrackingSetMap valueMap = (AbstractChangeTrackingSetMap) val;
                    Set<String> keys = valueMap.keySet();
                    if (keys != null) {
                        Iterator<String> it = keys.iterator();
                        while (it.hasNext()) {
                            String key = it.next();
                            AbstractChangeTrackingSet valueSet = valueMap.get(key);
                            valueSet.markAllForOverwrite();
                        }
                    }
                    break;
                }
            }
        } catch (final InvocationTargetException e) {
            throw DatabaseException.fatals.serializationFailedId(obj.getId(), e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.serializationFailedId(obj.getId(), e);
        }
    }

    /**
     * Helper to reflect on PropertyDescriptor and fill out _type and _name
     * information;
     */
    private void processProperty() {
        // ignore if no get method
        if (_property.getReadMethod() == null) {
            return;
        }

        Method readMethod = _property.getReadMethod();

        Annotation[] annotations = readMethod.getAnnotations();

        ColumnFamily<String, IndexColumnName> indexCF = null;
        int minPrefixChars;

        boolean isLazyLoadable = false;
        boolean hasRelationIndex = false;

        for (int i = 0; i < annotations.length; i++) {
            Annotation a = annotations[i];
            if (a instanceof Id) {
                _colType = ColumnType.Id;
                _name = "Id";
            } else if (a instanceof Name) {
                _name = ((Name) a).value();
                if (Number.class.isAssignableFrom(_valueType) ||
                        _valueType == URI.class ||
                        _valueType == String.class ||
                        _valueType == Date.class ||
                        _valueType == Boolean.class ||
                        _valueType == Byte.class ||
                        _valueType == Long.class ||
                        _valueType == byte[].class ||
                        _valueType.isEnum() ||
                        _valueType == Calendar.class) {
                    _colType = ColumnType.Primitive;
                    compositeName = new CompositeColumnName(_name);
                } else if (NamedURI.class == _valueType) {
                    _colType = ColumnType.NamedURI;
                    compositeName = new CompositeColumnName(_name);
                } else if (AbstractChangeTrackingSet.class.isAssignableFrom(_valueType)) {
                    _colType = ColumnType.TrackingSet;
                } else if (AbstractChangeTrackingMap.class.isAssignableFrom(_valueType)) {
                    _colType = ColumnType.TrackingMap;
                } else if (AbstractChangeTrackingSetMap.class.isAssignableFrom(_valueType)) {
                    _colType = ColumnType.TrackingSetMap;
                } else if (AbstractSerializableNestedObject.class.isAssignableFrom(_valueType)) {
                    _colType = ColumnType.NestedObject;
                    compositeName = new CompositeColumnName(_name);
                } else if (Collection.class.isAssignableFrom(_valueType)
                        || DataObject.class.isAssignableFrom(_valueType)) {
                    isLazyLoadable = true;
                } else {
                    throw new IllegalArgumentException(_name + " " + _valueType + " " + _property
                            + " " + _parentType.getDataObjectClass());
                }
            } else if (a instanceof Ttl) {
                _ttl = ((Ttl) a).value();
            } else if (a instanceof RelationIndex) {
                indexCF = new ColumnFamily<String, IndexColumnName>(
                        ((RelationIndex) a).cf(), StringSerializer.get(), IndexColumnNameSerializer.get());
                _indexRefType = ((RelationIndex) a).type();
                deactivateIfEmpty = ((RelationIndex) a).deactivateIfEmpty();
                _index = new RelationDbIndex(indexCF);
            } else if (a instanceof AlternateId) {
                indexCF = new ColumnFamily<String, IndexColumnName>(((AlternateId) a).value(), StringSerializer.get(),
                        IndexColumnNameSerializer.get());
                _index = new AltIdDbIndex(indexCF);
            } else if (a instanceof NamedRelationIndex) {
                indexCF = new ColumnFamily<String, IndexColumnName>(((NamedRelationIndex) a).cf(), StringSerializer.get(),
                        IndexColumnNameSerializer.get());
                _indexRefType = ((NamedRelationIndex) a).type();
                _index = new NamedRelationDbIndex(indexCF);
            } else if (a instanceof PrefixIndex) {
                indexCF = new ColumnFamily<String, IndexColumnName>(((PrefixIndex) a).cf(), StringSerializer.get(),
                        IndexColumnNameSerializer.get());
                minPrefixChars = ((PrefixIndex) a).minChars();
                _index = new PrefixDbIndex(indexCF, minPrefixChars);
            } else if (a instanceof PermissionsIndex && AbstractChangeTrackingSetMap.class.isAssignableFrom(_valueType)) {
                indexCF = new ColumnFamily<String, IndexColumnName>(((PermissionsIndex) a).value(), StringSerializer.get(),
                        IndexColumnNameSerializer.get());
                _index = new PermissionsDbIndex(indexCF);
            } else if (a instanceof Encrypt && _valueType == String.class) {
                _encrypt = true;
            } else if (a instanceof ScopedLabelIndex) {
                ScopedLabelIndex scopeLabelIndex = (ScopedLabelIndex) a;
                indexCF = new ColumnFamily<String, IndexColumnName>(scopeLabelIndex.cf(),
                        StringSerializer.get(), IndexColumnNameSerializer.get());
                minPrefixChars = scopeLabelIndex.minChars();
                _index = new ScopedLabelDbIndex(indexCF, minPrefixChars);
            } else if (a instanceof ClockIndependent) {
                clockIndValue = ((ClockIndependent) a).value();
            } else if (a instanceof DecommissionedIndex && Boolean.class.isAssignableFrom(_valueType)) {
                if (!_property.getName().equals(DataObject.INACTIVE_FIELD_NAME)
                        || _parentType.getDataObjectClass().getAnnotation(NoInactiveIndex.class) == null) {
                    indexCF = new ColumnFamily<String, IndexColumnName>(((DecommissionedIndex) a).value(), StringSerializer.get(),
                            IndexColumnNameSerializer.get());
                    _index = new DecommissionedDbIndex(indexCF);
                }
            } else if (a instanceof IndexByKey &&
                    (AbstractChangeTrackingMap.class.isAssignableFrom(_valueType) ||
                    AbstractChangeTrackingSet.class.isAssignableFrom(_valueType))) {
                _indexByKey = true;
            } else if (a instanceof Relation) {
                hasRelationIndex = true;
                if (((Relation) a).type().equals(DataObject.class)) {
                    _mappedByType = _valueType;
                } else {
                    _mappedByType = ((Relation) a).type();
                }
                _mappedByField = ((Relation) a).mappedBy();
            } else if (a instanceof AggregatedIndex) {
                indexCF = new ColumnFamily<String, IndexColumnName>(
                        ((AggregatedIndex) a).cf(), StringSerializer.get(), IndexColumnNameSerializer.get());
                String groupBy = ((AggregatedIndex) a).groupBy();
                boolean global = ((AggregatedIndex) a).classGlobal();
                _index = new AggregateDbIndex(indexCF, groupBy, global);
            }
        }

        if (_name == null) {
            String className = _parentType.getDataObjectClass().getName();
            String fieldName = _property.getName();
            throw new IllegalArgumentException(
                    String.format("@Name annotation missing from field '%s' in class '%s'", fieldName, className));
        }

        if (_index != null) {
            _index.setFieldName(_name);
            _index.setIndexByKey(_indexByKey);
        }

        if (isLazyLoadable && hasRelationIndex) {
            lazyLoaded = true;
        }
    }

    public final DbIndex getIndex() {
        return _index;
    }

    private Object getFieldValue(DataObject obj) {
        try {
            return _property.getReadMethod().invoke(obj);
        } catch (final InvocationTargetException e) {
            throw DatabaseException.fatals.serializationFailedId(obj.getId(), e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.serializationFailedId(obj.getId(), e);
        }
    }

    public static Object getFieldValue(ColumnField field, DataObject obj) {
        return field.getFieldValue(obj);
    }

    void check() {
        Method method = _property.getReadMethod();
        Annotation[] annotations = method.getAnnotations();
        for (Annotation a : annotations) {
            boolean foundError = false;
            if (a instanceof IndexByKey) {
                String errMsgHeader = String.format("The method %s.%s() has @IndexByKey but:",
                        _parentType.getDataObjectClass().getName(), method.getName());

                String errMsg = errMsgHeader;

                if (_index == null) {
                    errMsg += "\nwithout an index annotation";

                    _log.error(errMsg);

                    foundError = true;
                }

                if (!AbstractChangeTrackingMap.class.isAssignableFrom(_valueType) &&
                        !AbstractChangeTrackingSet.class.isAssignableFrom(_valueType)) {

                    String warnMsg = errMsgHeader + "\nThe return type should be subclass of AbstractChangeTrackingMap/Set";
                    _log.warn(warnMsg);
                }

                if (foundError) {
                    throw DatabaseException.fatals.invalidAnnotation("@IndexByKey", errMsg);
                }
            }
        }
    }

    /**
     * @return the _mapedByType
     */
    public Class<? extends DataObject> getMappedByType() {
        return _mappedByType;
    }

    /**
     * @return the _mappedByField
     */
    public String getMappedByField() {
        return _mappedByField;
    }

    public void prepareForLazyLoading(DataObject obj, LazyLoader lazyLoader, StringSet mappedBy) {
        if (isLazyLoaded()) {
            try {
                if (java.util.List.class.isAssignableFrom(_valueType)) {
                    LazyLoadedList list = new LazyLoadedList(_name, obj, lazyLoader, mappedBy);
                    _property.getWriteMethod().invoke(obj, list);
                } else if (java.util.Set.class.isAssignableFrom(_valueType)) {
                    LazyLoadedSet list = new LazyLoadedSet(_name, obj, lazyLoader, mappedBy);
                    _property.getWriteMethod().invoke(obj, list);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                _log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Get whether this field has IndexByKey annotation
     * @return true if has, false otherwise.
     */
    public boolean isIndexByKey() {
        return _indexByKey;
    }
}
