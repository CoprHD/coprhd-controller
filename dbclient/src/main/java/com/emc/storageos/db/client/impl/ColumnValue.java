/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;

import org.apache.cassandra.serializers.BooleanSerializer;
import org.apache.cassandra.serializers.DoubleSerializer;
import org.apache.cassandra.serializers.FloatSerializer;
import org.apache.cassandra.serializers.Int32Serializer;
import org.apache.cassandra.serializers.LongSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;

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
    
    public static void setEncryptedStringField(CompositeColumnName compositeColumnName,
            PropertyDescriptor pd, Object obj, EncryptionProvider provider) {
        
        // TODO DATASTAX need to verify
        byte[] encrypted = compositeColumnName.getValue().array();
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
    
    public static void setField(CompositeColumnName compositeColumnName, PropertyDescriptor pd,
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
                String entryValue = UTF8Serializer.instance.deserialize(compositeColumnName.getValue());
                if (entryValue == null || entryValue.isEmpty()) {
                    trackingMap.removeNoTrack(compositeColumnName.getTwo(), compositeColumnName.getThree());
                } else {
                    trackingMap.putNoTrack(compositeColumnName.getTwo(), entryValue);
                }
            } else if (AbstractChangeTrackingMap.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingMap<?> trackingMap = (AbstractChangeTrackingMap<?>) objValue;
                byte[] entryValue = compositeColumnName.getValue().array();
                if (entryValue == null || entryValue.length == 0) {
                    trackingMap.removeNoTrack(compositeColumnName.getTwo());
                } else {
                    trackingMap.putNoTrack(compositeColumnName.getTwo(), compositeColumnName.getValue().array());
                }
            } else if (AbstractChangeTrackingSet.class.isAssignableFrom(type)) {
                objValue = pd.getReadMethod().invoke(obj);
                if (objValue == null) {
                    objValue = type.newInstance();
                }
                AbstractChangeTrackingSet trackingSet = (AbstractChangeTrackingSet) objValue;
                String entryValue = UTF8Serializer.instance.deserialize(compositeColumnName.getValue());
                if (entryValue == null || entryValue.isEmpty()) {
                    trackingSet.removeNoTrack(compositeColumnName.getTwo());
                } else {
                    trackingSet.addNoTrack(entryValue);
                }
            } else {
                objValue = getPrimitiveColumnValue(compositeColumnName.getValue(), pd);
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
    
    public static <T> Object getPrimitiveColumnValue(ByteBuffer binaryValue, PropertyDescriptor pd) {
        Object objValue = null;
        try {
            Class type = pd.getPropertyType();
            if (AbstractSerializableNestedObject.class.isAssignableFrom(type)) {
                objValue = type.newInstance();
                AbstractSerializableNestedObject value = (AbstractSerializableNestedObject) objValue;
                value.loadBytes(binaryValue.array());
            } else if (type == String.class) {
                objValue = UTF8Serializer.instance.deserialize(binaryValue);
            } else if (type == URI.class) {
                objValue = URI.create(UTF8Serializer.instance.deserialize(binaryValue));
            } else if (type == Byte.class) {
                objValue = (byte) (Int32Serializer.instance.deserialize(binaryValue) & 0xff);
            } else if (type == Boolean.class) {
                objValue = BooleanSerializer.instance.deserialize(binaryValue);
            } else if (type == Short.class) {
                objValue = (short) (binaryValue.getShort());
            } else if (type == Integer.class) {
                objValue = Int32Serializer.instance.deserialize(binaryValue);
            } else if (type == Long.class) {
                objValue = LongSerializer.instance.deserialize(binaryValue);
            } else if (type == Float.class) {
                objValue = FloatSerializer.instance.deserialize(binaryValue);
            } else if (type == Double.class) {
                objValue = DoubleSerializer.instance.deserialize(binaryValue);
            } else if (type == Date.class) {
                objValue = new Date(LongSerializer.instance.deserialize(binaryValue));
            } else if (type == NamedURI.class) {
                objValue = NamedURI.fromString(UTF8Serializer.instance.deserialize(binaryValue));
            } else if (type == byte[].class) {
                objValue = binaryValue.array();
            } else if (type == ScopedLabel.class) {
                objValue = ScopedLabel.fromString(UTF8Serializer.instance.deserialize(binaryValue));
            } else if (type.isEnum()) {
                objValue = Enum.valueOf(type, UTF8Serializer.instance.deserialize(binaryValue));
            } else if (type == Calendar.class) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(LongSerializer.instance.deserialize(binaryValue));
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
}
