/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;

import org.apache.cassandra.serializers.BooleanSerializer;
import org.apache.cassandra.serializers.DoubleSerializer;
import org.apache.cassandra.serializers.FloatSerializer;
import org.apache.cassandra.serializers.Int32Serializer;
import org.apache.cassandra.serializers.LongSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;

import com.datastax.driver.core.Row;
import com.emc.storageos.db.client.javadriver.CassandraRow;
import com.emc.storageos.db.client.model.AbstractChangeTrackingMap;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSetMap;
import com.emc.storageos.db.client.model.AbstractSerializableNestedObject;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.Column;

/**
 * Utility class for de/serializing an individual column
 */
public class ColumnValue {
    /**
     * Decrypts and sets data object field value
     * 
     * @param column encrypted column value
     * @param pd property
     * @param obj wrapper object
     * @param provider encryption provider
     */
    public static void setEncryptedStringField(Column<CompositeColumnName> column,
            PropertyDescriptor pd, Object obj, EncryptionProvider provider) {
        byte[] encrypted = column.getByteArrayValue();
        String val = provider.decrypt(encrypted);
        try {
            pd.getWriteMethod().invoke(obj, val);
        } catch (final InvocationTargetException e) {
            throw DatabaseException.fatals.deserializationFailedEncryptedProperty(pd.getName(), e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.deserializationFailedEncryptedProperty(pd.getName(), e);
        }
    }
    
    public static void setEncryptedStringField(CassandraRow cassandraRow,
            PropertyDescriptor pd, Object obj, EncryptionProvider provider) {
        
        // TODO DATASTAX need to verify
        byte[] encrypted = cassandraRow.getRow().getBytes(5).array();
        String val = provider.decrypt(encrypted);
        try {
            pd.getWriteMethod().invoke(obj, val);
        } catch (final InvocationTargetException e) {
            throw DatabaseException.fatals.deserializationFailedEncryptedProperty(pd.getName(), e);
        } catch (final IllegalAccessException e) {
            throw DatabaseException.fatals.deserializationFailedEncryptedProperty(pd.getName(), e);
        }
    }

    public static void setField(Column<CompositeColumnName> column, PropertyDescriptor pd,
            Object obj) {
        try {
            Class type = pd.getPropertyType();
            Object objValue = null;
            if (AbstractChangeTrackingSetMap.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingSetMap<?> trackingMap = (AbstractChangeTrackingSetMap<?>) objValue;
                String entryValue = column.getStringValue();
                if (entryValue == null || entryValue.isEmpty()) {
                    trackingMap.removeNoTrack(column.getName().getTwo(), column.getName().getThree());
                } else {
                    trackingMap.putNoTrack(column.getName().getTwo(), entryValue);
                }
            } else if (AbstractChangeTrackingMap.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingMap<?> trackingMap = (AbstractChangeTrackingMap<?>) objValue;
                byte[] entryValue = column.getByteArrayValue();
                if (entryValue == null || entryValue.length == 0) {
                    trackingMap.removeNoTrack(column.getName().getTwo());
                } else {
                    trackingMap.putNoTrack(column.getName().getTwo(), column.getByteArrayValue());
                }
            } else if (AbstractChangeTrackingSet.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingSet trackingSet = (AbstractChangeTrackingSet) objValue;
                String entryValue = column.getStringValue();
                if (entryValue == null || entryValue.isEmpty()) {
                    trackingSet.removeNoTrack(column.getName().getTwo());
                } else {
                    trackingSet.addNoTrack(entryValue);
                }
            } else {
                objValue = getPrimitiveColumnValue(column, pd);
            }
            pd.getWriteMethod().invoke(obj, objValue);
        } catch (IllegalAccessException e) {
            // should never get here
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        } catch (InvocationTargetException e) {
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        } catch (InstantiationException e) {
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        }
    }
    
    public static void setField(CassandraRow cassandraRow, PropertyDescriptor pd,
            Object obj) {
        try {
            Class type = pd.getPropertyType();
            Object objValue = null;
            if (AbstractChangeTrackingSetMap.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingSetMap<?> trackingMap = (AbstractChangeTrackingSetMap<?>) objValue;
                String entryValue = UTF8Serializer.instance.deserialize(cassandraRow.getRow().getBytes(5));
                if (entryValue == null || entryValue.isEmpty()) {
                    trackingMap.removeNoTrack(cassandraRow.getCompositeColumnName().getTwo(), cassandraRow.getCompositeColumnName().getThree());
                } else {
                    trackingMap.putNoTrack(cassandraRow.getCompositeColumnName().getTwo(), entryValue);
                }
            } else if (AbstractChangeTrackingMap.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingMap<?> trackingMap = (AbstractChangeTrackingMap<?>) objValue;
                byte[] entryValue = cassandraRow.getRow().getBytes(5).array();
                if (entryValue == null || entryValue.length == 0) {
                    trackingMap.removeNoTrack(cassandraRow.getCompositeColumnName().getTwo());
                } else {
                    trackingMap.putNoTrack(cassandraRow.getCompositeColumnName().getTwo(), cassandraRow.getRow().getBytes(5).array());
                }
            } else if (AbstractChangeTrackingSet.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingSet trackingSet = (AbstractChangeTrackingSet) objValue;
                String entryValue = UTF8Serializer.instance.deserialize(cassandraRow.getRow().getBytes(5));
                if (entryValue == null || entryValue.isEmpty()) {
                    trackingSet.removeNoTrack(cassandraRow.getCompositeColumnName().getTwo());
                } else {
                    trackingSet.addNoTrack(entryValue);
                }
            } else {
                objValue = getPrimitiveColumnValue(cassandraRow.getRow(), 5, pd);
            }
            pd.getWriteMethod().invoke(obj, objValue);
        } catch (IllegalAccessException e) {
            // should never get here
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        } catch (InvocationTargetException e) {
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        } catch (InstantiationException e) {
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        }
    }
    
    public static <T> Object getPrimitiveColumnValue(Row row, PropertyDescriptor pd) {
        return getPrimitiveColumnValue(row, 5, pd);
    }
    
    public static <T> Object getPrimitiveColumnValue(Row row, int columnIndex, PropertyDescriptor pd) {
        Object objValue = null;
        try {
            Class type = pd.getPropertyType();
            if (AbstractSerializableNestedObject.class.isAssignableFrom(type)) {
                objValue = type.newInstance();
                AbstractSerializableNestedObject value = (AbstractSerializableNestedObject) objValue;
                value.loadBytes(row.getBytes(columnIndex).array());
            } else if (type == String.class) {
                objValue = UTF8Serializer.instance.deserialize(row.getBytes(columnIndex));
            } else if (type == URI.class) {
                objValue = URI.create(UTF8Serializer.instance.deserialize(row.getBytes(columnIndex)));
            } else if (type == Byte.class) {
                objValue = (byte) (Int32Serializer.instance.deserialize(row.getBytes(columnIndex)) & 0xff);
            } else if (type == Boolean.class) {
                objValue = BooleanSerializer.instance.deserialize(row.getBytes(columnIndex));
            } else if (type == Short.class) {
                objValue = (short) (row.getBytes(columnIndex).getShort());
            } else if (type == Integer.class) {
                objValue = Int32Serializer.instance.deserialize(row.getBytes(columnIndex));
            } else if (type == Long.class) {
                objValue = LongSerializer.instance.deserialize(row.getBytes(columnIndex));
            } else if (type == Float.class) {
                objValue = FloatSerializer.instance.deserialize(row.getBytes(columnIndex));
            } else if (type == Double.class) {
                objValue = DoubleSerializer.instance.deserialize(row.getBytes(columnIndex));
            } else if (type == Date.class) {
                objValue = new Date(LongSerializer.instance.deserialize(row.getBytes(columnIndex)));
            } else if (type == NamedURI.class) {
                objValue = NamedURI.fromString(UTF8Serializer.instance.deserialize(row.getBytes(columnIndex)));
            } else if (type == byte[].class) {
                objValue = row.getBytes(columnIndex).array();
            } else if (type == ScopedLabel.class) {
                objValue = ScopedLabel.fromString(UTF8Serializer.instance.deserialize(row.getBytes(columnIndex)));
            } else if (type.isEnum()) {
                objValue = Enum.valueOf(type, UTF8Serializer.instance.deserialize(row.getBytes(columnIndex)));
            } else if (type == Calendar.class) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(LongSerializer.instance.deserialize(row.getBytes(columnIndex)));
                objValue = cal;
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (IllegalAccessException e) {
            // should never get here
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        } catch (InstantiationException e) {
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        }
        return objValue;
    }

    public static <T> Object getPrimitiveColumnValue(Column<T> column, PropertyDescriptor pd) {
        Object objValue = null;
        try {
            Class type = pd.getPropertyType();
            if (AbstractSerializableNestedObject.class.isAssignableFrom(type)) {
                objValue = type.newInstance();
                AbstractSerializableNestedObject value = (AbstractSerializableNestedObject) objValue;
                value.loadBytes(column.getByteArrayValue());
            } else if (type == String.class) {
                objValue = column.getStringValue();
            } else if (type == URI.class) {
                objValue = URI.create(column.getStringValue());
            } else if (type == Byte.class) {
                objValue = (byte) (column.getIntegerValue() & 0xff);
            } else if (type == Boolean.class) {
                objValue = column.getBooleanValue();
            } else if (type == Short.class) {
                objValue = (short) (column.getShortValue());
            } else if (type == Integer.class) {
                objValue = column.getIntegerValue();
            } else if (type == Long.class) {
                objValue = column.getLongValue();
            } else if (type == Float.class) {
                objValue = (float) column.getDoubleValue();
            } else if (type == Double.class) {
                objValue = column.getDoubleValue();
            } else if (type == Date.class) {
                objValue = column.getDateValue();
            } else if (type == NamedURI.class) {
                objValue = NamedURI.fromString(column.getStringValue());
            } else if (type == byte[].class) {
                objValue = column.getByteArrayValue();
            } else if (type == ScopedLabel.class) {
                objValue = ScopedLabel.fromString(column.getStringValue());
            } else if (type.isEnum()) {
                objValue = Enum.valueOf(type, column.getStringValue());
            } else if (type == Calendar.class) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(column.getLongValue());
                objValue = cal;
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (IllegalAccessException e) {
            // should never get here
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        } catch (InstantiationException e) {
            throw DatabaseException.fatals.deserializationFailedProperty(pd.getName(), e);
        }
        return objValue;
    }

    /**
     * Serialize field into db query
     * 
     * @param columns batch query
     * @param name composite column name
     * @param val column value
     * @param ttl time to live in seconds for this column
     * @throws DatabaseException
     */
    public static <T> void setColumn(ColumnListMutation<T> columns, T name, Object val, Integer ttl) {
        Class valClass = val.getClass();
        if (val == null) {
            columns.putEmptyColumn(name, ttl);
        } else if (valClass == byte[].class) {
            columns.putColumn(name, (byte[]) val, ttl);
        } else if (val.getClass() == String.class) {
            columns.putColumn(name, (String) val, ttl);
        } else if (val.getClass() == URI.class) {
            columns.putColumn(name, val.toString(), ttl);
        } else if (val.getClass() == Byte.class) {
            columns.putColumn(name, (Byte) val & 0xff, ttl);
        } else if (val.getClass() == Boolean.class) {
            columns.putColumn(name, (Boolean) val, ttl);
        } else if (val.getClass() == Short.class) {
            columns.putColumn(name, (Short) val, ttl);
        } else if (val.getClass() == Integer.class) {
            columns.putColumn(name, (Integer) val, ttl);
        } else if (val.getClass() == Long.class) {
            columns.putColumn(name, (Long) val, ttl);
        } else if (val.getClass() == Float.class) {
            columns.putColumn(name, (Float) val, ttl);
        } else if (val.getClass() == Double.class) {
            columns.putColumn(name, (Double) val, ttl);
        } else if (val.getClass() == Date.class) {
            columns.putColumn(name, (Date) val, ttl);
        } else if (val.getClass() == NamedURI.class) {
            columns.putColumn(name, val.toString(), ttl);
        } else if (val.getClass() == ScopedLabel.class) {
            columns.putColumn(name, val.toString(), ttl);
        } else if (val instanceof Calendar) {
            columns.putColumn(name, ((Calendar) val).getTimeInMillis(), ttl);
        } else if (val.getClass().isEnum()) {
            columns.putColumn(name, ((Enum<?>) val).name(), ttl);
        } else {
            throw DatabaseException.fatals.serializationFailedUnsupportedType(name);
        }
    }

    /**
     * Serialize field into db query
     * 
     * @param columns batch query
     * @param name composite column name
     * @param val column value
     * @throws DatabaseException
     */
    public static void setColumn(ColumnListMutation<CompositeColumnName> columns,
            CompositeColumnName name, Object val) {
        setColumn(columns, name, val, null);
    }
}
