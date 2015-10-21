/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Calendar;
import java.util.Date;

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

    /**
     * Serialize field into db query
     * 
     * @param columns batch query
     * @param name composite column name
     * @param val column value
     * @param ttl time to live in seconds for this column
     * @return the number of bytes added
     * @throws DatabaseException
     */
    public static <T> int setColumn(ColumnListMutation<T> columns, T name, Object val, Integer ttl) {
        int totalSizeInByte = 4; // size of ttl
        if (val == null) {
            columns.putEmptyColumn(name, ttl);
        } else if (val.getClass() == byte[].class) {
            byte[] value = (byte[])val;
            columns.putColumn(name, value, ttl);
            totalSizeInByte += value.length;
        } else if (val.getClass() == String.class) {
            String value = (String)val;
            columns.putColumn(name, value, ttl);
            totalSizeInByte += value.getBytes().length;
        } else if (val.getClass() == URI.class) {
            String value = val.toString();
            columns.putColumn(name, value, ttl);
            totalSizeInByte += value.getBytes().length;
        } else if (val.getClass() == Byte.class) {
            columns.putColumn(name, (Byte) val & 0xff, ttl);
            totalSizeInByte +=1;
        } else if (val.getClass() == Boolean.class) {
            columns.putColumn(name, (Boolean) val, ttl);
            totalSizeInByte +=1;
        } else if (val.getClass() == Short.class) {
            columns.putColumn(name, (Short) val, ttl);
            totalSizeInByte +=2;
        } else if (val.getClass() == Integer.class) {
            columns.putColumn(name, (Integer) val, ttl);
            totalSizeInByte +=4;
        } else if (val.getClass() == Long.class) {
            columns.putColumn(name, (Long) val, ttl);
            totalSizeInByte +=8;
        } else if (val.getClass() == Float.class) {
            columns.putColumn(name, (Float) val, ttl);
            totalSizeInByte +=4;
        } else if (val.getClass() == Double.class) {
            columns.putColumn(name, (Double) val, ttl);
            totalSizeInByte +=8;
        } else if (val.getClass() == Date.class) {
            columns.putColumn(name, (Date) val, ttl);
            totalSizeInByte +=8;
        } else if (val.getClass() == NamedURI.class) {
            String value = val.toString();
            columns.putColumn(name, value, ttl);
            totalSizeInByte +=value.getBytes(Charset.forName("UTF-8")).length;
        } else if (val.getClass() == ScopedLabel.class) {
            String value = val.toString();
            columns.putColumn(name, val.toString(), ttl);
            totalSizeInByte +=value.getBytes(Charset.forName("UTF-8")).length;
        } else if (val instanceof Calendar) {
            columns.putColumn(name, ((Calendar) val).getTimeInMillis(), ttl);
            totalSizeInByte +=8;
        } else if (val.getClass().isEnum()) {
            String value = ((Enum<?>)val).name();
            columns.putColumn(name, value, ttl);
            totalSizeInByte +=value.getBytes(Charset.forName("UTF-8")).length;
        } else {
            throw DatabaseException.fatals.serializationFailedUnsupportedType(name);
        }

        return totalSizeInByte;
    }

    /**
     * Serialize field into db query
     * 
     * @param columns batch query
     * @param name composite column name
     * @param val column value
     * @throws DatabaseException
     */
    public static int setColumn(ColumnListMutation<CompositeColumnName> columns,
            CompositeColumnName name, Object val) {
        return setColumn(columns, name, val, null);
    }
}
