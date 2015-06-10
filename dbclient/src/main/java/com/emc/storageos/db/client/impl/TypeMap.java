/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer;
import com.emc.storageos.db.client.util.KeyspaceUtil;

/**
 * Global type map for all data object types
 */
public class TypeMap {
    private static ConcurrentMap<Class<? extends DataObject>, DataObjectType> _typeMap =
        new ConcurrentHashMap<Class<? extends DataObject>, DataObjectType>();
    private static ConcurrentHashMap<Class<? extends TimeSeries>, TimeSeriesType> _timeSeriesMap =
            new ConcurrentHashMap<Class<? extends TimeSeries>, TimeSeriesType>();
    private  static ConcurrentHashMap<String, TimeSeriesType> _tsTypeMap = new ConcurrentHashMap<>();
    private static final SchemaRecordType _srType = new SchemaRecordType();
    private static final GlobalLockType _glType = new GlobalLockType();
    private static volatile EncryptionProvider _encryptionProvider;
    private static volatile EncryptionProvider _geoEncryptionProvider;

    /**
     * Time series configuration overrides
     */
    public static class TimeSeriesConfiguration {
        private Class<? extends TimeSeries> _tsClass;
        private Integer _ttl;

        public void setTsClass(Class<? extends TimeSeries> clazz) {
            _tsClass = clazz;
        }

        public Class<? extends TimeSeries> getTsClass() {
            return _tsClass;
        }

        /**
         * Overrie TTL configuration
         *
         * @param ttl
         */
        public void setTtl(int ttl) {
            _ttl = ttl;
        }

        /**
         * TTL configuration
         *
         * @return
         */
        public Integer getTtl() {
            return _ttl;
        }
    }

    /**
     * Data object field configuration overrides
     */
    public static class DataObjectFieldConfiguration {
        private Class<? extends DataObject> _doClass;
        private String _fieldName;
        private Integer _ttl;

        /**
         * DataObject class
         *
         * @param clazz
         */
        public void setDoClass(Class<? extends DataObject> clazz) {
            _doClass = clazz;
        }

        public Class<? extends DataObject> getDoClass() {
            return _doClass;
        }

        /**
         * Target data object field
         *
         * @param fieldName
         */
        public void setFieldName(String fieldName) {
            _fieldName = fieldName;
        }

        public String getFieldName() {
            return _fieldName;
        }

        /**
         * Override default TTL configuration
         *
         * @param ttl
         */
        public void setTtl(int ttl) {
            _ttl = ttl;
        }

        /**
         * TTL configuration
         *
         * @return
         */
        public Integer getTtl() {
            return _ttl;
        }
    }

    /**
     * User may optionally override default data object configuration (like TTL)
     * by calling this method. Note that configuration must be loaded before
     * DbClient is used
     */
    public static void loadDataObjectConfiguration(List<DataObjectFieldConfiguration> configuration) {
        for (DataObjectFieldConfiguration fieldConfig : configuration) {
            DataObjectType doType = getDoType(fieldConfig.getDoClass());
            ColumnField field = doType.getColumnField(fieldConfig.getFieldName());
            if (field == null) {
                throw new IllegalArgumentException(String.format(
                        "Unknown field: %1$s", fieldConfig.getFieldName()));
            }
            field.setTtl(fieldConfig.getTtl());
        }
    }


    /**
     * User may optionally override default time series configuration (like TTL) by calling this method.
     * Note that configuration must be loaded before DbClient is used
     */
    public static void loadTimeSeriesConfiguration(List<TimeSeriesConfiguration> configuration) {
        for (TimeSeriesConfiguration tsConfig : configuration) {
            TimeSeriesType tsType = getTimeSeriesType(tsConfig.getTsClass());
            tsType.setTtl(tsConfig.getTtl());
        }
    }

    /**
     * Set encryption provider for all local data object types
     *
     * @param encryptionProvider
     */
    public static void setEncryptionProviders(EncryptionProvider encryptionProvider, 
            EncryptionProvider geoEncryptionProvider) {
        _encryptionProvider = encryptionProvider;
        _geoEncryptionProvider = geoEncryptionProvider;
        Iterator<DataObjectType> it = _typeMap.values().iterator();
        while (it.hasNext()) {
            setEncryptionProvider(it.next());
        }
    }
    
    private static void setEncryptionProvider(DataObjectType doType) {
        if (KeyspaceUtil.isLocal(doType.getDataObjectClass())) {
            doType.setEncryptionProvider(_encryptionProvider);
        } else {
            doType.setEncryptionProvider(_geoEncryptionProvider);
        }       
    }

    /**
     * Retrieve time series type
     *
     * @param clazz time series class
     * @param <T>
     * @return
     */
    @SuppressWarnings(value = "unchecked")
    public static <T extends TimeSeriesSerializer.DataPoint>
        TimeSeriesType<T> getTimeSeriesType(Class<? extends TimeSeries> clazz) {
        TimeSeriesType<T> ttype = _timeSeriesMap.get(clazz);
        if (ttype != null) {
            return ttype;
        }
        ttype = new TimeSeriesType<T>(clazz);
        _timeSeriesMap.putIfAbsent(clazz, ttype);
        _tsTypeMap.putIfAbsent(ttype.getCf().getName(),ttype);
        return _timeSeriesMap.get(clazz);
    }

    /**
     * Retrieve time series type
     *
     * @param cfName time series CF
     * @param <T>
     * @return
     */
    @SuppressWarnings(value = "unchecked")
    public static <T extends TimeSeriesSerializer.DataPoint>
    TimeSeriesType<T> getTimeSeriesType(String cfName) {
        return _tsTypeMap.get(cfName);
    }


    /**
     * Retrieve data object type
     * 
     * @param clazz data object class
     * @return
     */
	public static DataObjectType getDoType(Class<? extends DataObject> clazz) {
        DataObjectType doType = _typeMap.get(clazz);
        if (doType != null) {
            return doType;
        }        
        doType = new DataObjectType(clazz);
        setEncryptionProvider(doType);
        _typeMap.putIfAbsent(clazz, doType);
        if (doType.getInstrumentedType()!=null) {
            _typeMap.putIfAbsent(doType.getInstrumentedType(), doType);
        }
        return doType;
    }

    public static String getCFName(Class<? extends DataObject> clazz) {
        return getDoType(clazz).getCF().getName();
    }

    public static SchemaRecordType getSchemaRecordType() {
        return _srType;
    }

    public static GlobalLockType getGlobalLockType() {
        return _glType;
    }

    public static void clear() {
        _typeMap.clear();
    }

    /**
     * Check the integrity of DataObject classes
     * throws RuntimeException if error occurs.
     */
    public static void check() {
        Iterator<DataObjectType> doIterator = _typeMap.values().iterator();
        while (doIterator.hasNext()) {
            DataObjectType doType = doIterator.next();
            Iterator<ColumnField> columnIterator = doType.getColumnFields().iterator();

            while (columnIterator.hasNext()) {
                ColumnField field = columnIterator.next();
                field.check();
            }
        }
    }

    /**
     * @return a copy of all DoType objects
     */
    public static Collection<DataObjectType> getAllDoTypes() {
        return Collections.unmodifiableCollection(_typeMap.values());
    }
}
