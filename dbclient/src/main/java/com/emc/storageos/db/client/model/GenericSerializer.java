/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Serializer for object to byte array
 */
public class GenericSerializer {
    private static final int MAX_PROPERTIES = 256;
    private static final Charset ENCODING = Charset.forName("UTF-8");

    private class PropertiesMap {
        private PropertyDescriptor[] _array;
        private ArrayList<Integer> _indices;

        public PropertiesMap() {
            _array = new PropertyDescriptor[MAX_PROPERTIES];
            Arrays.fill(_array, null);
            _indices = new ArrayList<Integer>();
        }

        /**
         * Adds PropertyDescriptor at the given index
         *
         * @param index Serialization index for the property
         * @param pd PropertyDescriptor
         */
        public void add(int index, PropertyDescriptor pd) {
            if (index >= MAX_PROPERTIES) {
                throw DatabaseException.fatals.serializationFailedIndexGreaterThanMax(pd.getName(), index, MAX_PROPERTIES);
            }
            if (_array[index] != null) {
                throw DatabaseException.fatals.serializationFailedIndexReused(pd.getName(), index);
            }
            _array[index] = pd;
            _indices.add(index);
        }

        public ArrayList<Integer> getIndices() {
            return _indices;
        }

        public PropertyDescriptor get(int index) {
            return _array[index];
        }
    }

    private ConcurrentMap<Class<?>, PropertiesMap> _typeCache;

    /**
     * default constructor
     */
    public GenericSerializer() {
        _typeCache = new ConcurrentHashMap<Class<?>, PropertiesMap>();
    }

    /**
     * Initialize the property descriptor map for the given class type
     *
     * @param clazz
     */
    private void initForType(Class<?> clazz) {
        if (_typeCache.containsKey(clazz)) {
            return;
        }
        BeanInfo bInfo;
        try {
            bInfo = Introspector.getBeanInfo(clazz);
        } catch (final IntrospectionException ex) {
            throw DatabaseException.fatals.serializationFailedInitializingBeanInfo(clazz, ex);
        }
        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();
        PropertiesMap properties = new PropertiesMap();
        for (int i = 0; i < pds.length; i++) {
            PropertyDescriptor pd = pds[i];
            if (pd.getName().equals("class")) {
                continue;
            }
            byte index = 0;
            Annotation[] annotations = pd.getReadMethod().getAnnotations();
            for (int j = 0; j < annotations.length; j++) {
                Annotation a = annotations[j];
                if (a instanceof SerializationIndex) {
                    index = ((SerializationIndex) a).value();
                }
            }
            properties.add(index, pd);
        }
        _typeCache.putIfAbsent(clazz, properties);
    }

    public PropertiesMap getProperties(Class<?> clazz) {
        if (!_typeCache.containsKey(clazz)) {
            // init the map for this type
            initForType(clazz);
        }
        return _typeCache.get(clazz);
    }

    /**
     * Decode a long number from variable length encoded byte array
     * 1. decode 7 bits from each bit, starting with LSB
     * 2. undo the sign-unsigned conversion we did, to restore the sign bit
     * 
     * @param read byte array
     * @return long
     */
    public long decodeVariantLong(byte[] read) {
        int shift = 0;
        long result = 0;
        ByteArrayInputStream in = new ByteArrayInputStream(read);
        while (shift < 64) {
            final byte b = (byte) in.read();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return ((result >>> 1) ^ -(result & 1));
    }

    /**
     * Encode a long number as variable length encoded byte array
     * 1. get equivalent unsigned long
     * 2. encode 7 bits at a time starting from LSB, set the highest bit if there is a next byte needed
     * 
     * @param read long value to encode
     * @return byte array
     */
    public byte[] encodeVariantLong(long read) {
        long value = (read << 1) ^ (read >> 63);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            if ((value & ~0x7FL) == 0) {
                bytes.write((int) value);
                break;
            } else {
                bytes.write(((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Get byte[] representation of this object
     * each field is encoded as 3 values, { index [byte], len [2 bytes], value [len bytes] }
     * 
     * @param clazz
     * @param obj
     * @param <T>
     * @return
     */
    public <T> byte[] toByteArray(Class<T> clazz, T obj)
            throws DatabaseException {
        PropertiesMap propertiesMap = getProperties(clazz);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int index : propertiesMap.getIndices()) {
                PropertyDescriptor pd = propertiesMap.get(index);
                if (pd == null) {
                    // this should not happen
                    throw DatabaseException.fatals.serializationFailedInconsistentPropertyMap(clazz);
                }
                Class<?> type = pd.getPropertyType();
                byte[] value;
                if (type == String.class) {
                    String str = (String) pd.getReadMethod().invoke(obj);
                    if (str == null) {
                        continue;
                    }
                    value = str.getBytes(ENCODING);
                } else if (type == URI.class) {
                    URI uri = (URI) pd.getReadMethod().invoke(obj);
                    if (uri == null) {
                        continue;
                    }
                    value = uri.toString().getBytes(ENCODING);
                } else if (type == long.class) {
                    long lvalue = (Long) pd.getReadMethod().invoke(obj);
                    value = encodeVariantLong(lvalue);
                } else if (type == boolean.class) {
                    boolean lvalue = (Boolean) pd.getReadMethod().invoke(obj);
                    value = new byte[1];
                    value[0] = lvalue ? (byte) 1 : (byte) 0;
                } else if (type == byte[].class) {
                    byte[] lvalue = (byte[]) pd.getReadMethod().invoke(obj);
                    value = lvalue;
                } else {
                    // throw -- implement value for this type
                    throw DatabaseException.fatals.serializationFailedNotImplementedForType(clazz, pd.getName(), type);
                }
                // now encode this field
                if ((value.length & 0x0000) > 0) {
                    throw DatabaseException.fatals.serializationFailedFieldLengthTooLong(clazz, pd.getName(), value.length);
                }
                // write index
                out.write(index);
                int len = value.length;
                // length, encoded as 2 bytes
                out.write((len >> 8));
                out.write((len & 0xff));
                // actual value
                out.write(value);
            }
        } catch (final IOException ex) {
            throw DatabaseException.fatals.serializationFailedClass(clazz, ex);
        } catch (final IllegalAccessException ex) {
            throw DatabaseException.fatals.serializationFailedClass(clazz, ex);
        } catch (final InvocationTargetException ex) {
            throw DatabaseException.fatals.serializationFailedClass(clazz, ex);
        }
        return out.toByteArray();
    }

    /**
     * Create object of specified type, from given byte[]
     * encoding is expected as 3-tuple per field { index [byte], len [2 bytes], value [len bytes] }
     * 
     * @param clazz
     * @param <T>
     * @return
     */
    public <T>
            T fromByteArray(Class<T> clazz, byte[] bytes)
                    throws DatabaseException {

        PropertiesMap propertiesMap = getProperties(clazz);
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        try {
            T retObj = clazz.newInstance();
            // at least, 3 bytes expected
            while (in.available() > 3) {
                // read len
                int index = in.read();
                int high = in.read();
                int low = in.read();
                int len = (high << 8 | low);
                byte[] value = new byte[len];
                in.read(value, 0, len);
                if (index >= MAX_PROPERTIES) {
                    throw DatabaseException.fatals.deserializationFailedUnexpectedIndex(clazz, index, MAX_PROPERTIES);
                }
                // now, set value to the respective field
                PropertyDescriptor pd = propertiesMap.get(index);
                if (pd == null) {
                    // old field we don't have anymore, ignore
                    continue;
                }
                Class<?> type = pd.getPropertyType();
                if (type == String.class) {
                    pd.getWriteMethod().invoke(retObj, new String(value));
                } else if (type == URI.class) {
                    URI uri = URI.create(new String(value));
                    pd.getWriteMethod().invoke(retObj, uri);
                } else if (type == long.class) {
                    pd.getWriteMethod().invoke(retObj, decodeVariantLong(value));
                } else if (type == boolean.class) {
                    pd.getWriteMethod().invoke(retObj, value[0] == (byte) 1 ? true : false);
                } else if (type == byte[].class) {
                    pd.getWriteMethod().invoke(retObj, value);
                } else {
                    // throw -- implement value for this type
                    throw DatabaseException.fatals.deserializationFailedUnsupportedType(clazz, pd.getName(), type);
                }
            }
            return retObj;
        } catch (InstantiationException e) {
            throw DatabaseException.fatals.deserializationFailed(clazz, e);
        } catch (IllegalAccessException e) {
            throw DatabaseException.fatals.deserializationFailed(clazz, e);
        } catch (IllegalArgumentException e) {
            throw DatabaseException.fatals.deserializationFailed(clazz, e);
        } catch (InvocationTargetException e) {
            throw DatabaseException.fatals.deserializationFailed(clazz, e);
        } finally {
            try {
                in.close();
            } catch (final IOException e) {
                // ignore
            }
        }
    }
}
