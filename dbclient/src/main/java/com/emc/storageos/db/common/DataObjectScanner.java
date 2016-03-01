/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.GlobalLockType;
import com.emc.storageos.db.client.impl.SchemaRecordType;
import com.emc.storageos.db.client.impl.TimeSeriesType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExcludeFromGarbageCollection;
import com.emc.storageos.db.client.model.GeoVisibleResource;
import com.emc.storageos.db.client.model.GlobalLock;
import com.emc.storageos.db.client.model.SchemaRecord;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.netflix.astyanax.model.ColumnFamily;

/**
 * Scanner for sweeping all DataObject types defined and creates:
 * - CF Map for building db schema
 * - DependencyTracker - with all the dependency information between the types
 */
public class DataObjectScanner extends PackageScanner {
    private static final Logger _log = LoggerFactory.getLogger(DataObjectScanner.class);
    private Map<String, ColumnFamily> _cfMap;
    private Map<String, ColumnFamily> _geocfMap;
    private DependencyTracker _dependencyTracker;
    private boolean _dualDbSvcMode;
    private Properties _dbCommonInfo;
    private Collection<Class<?>> modelClasses;

    /**
     * Get DependencyTracker
     * 
     * @return
     */
    public DependencyTracker getDependencyTracker() {
        return _dependencyTracker;
    }

    /**
     * Get ColumnFamily Map
     * 
     * @return
     */
    public Map<String, ColumnFamily> getCfMap() {
        return _cfMap;
    }

    /**
     * Get geo ColumnFamily Map
     * 
     * @return
     */
    public Map<String, ColumnFamily> getGeoCfMap() {
        return _geocfMap;
    }

    // DBSVC config parameters
    public void setDbCommonInfo(Properties dbCommonInfo) {
        _dbCommonInfo = dbCommonInfo;
    }

    /**
     * Scan model classes and load up CF information from them
     */
    @SuppressWarnings("unchecked")
    public void init() {
        _dependencyTracker = new DependencyTracker();
        _cfMap = new HashMap<String, ColumnFamily>();
        _geocfMap = new HashMap<String, ColumnFamily>();

        this.modelClasses = getModelClasses(Cf.class);
        scan(Cf.class);

        _dependencyTracker.buildDependencyLevels();
        _log.info("DependencyTracker - state: {}", _dependencyTracker.toString());
    }

    /**
     * Processes data object or time series class and extracts CF
     * requirements
     * 
     * @param clazz data object or time series class
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void processClass(Class clazz) {
        if (DataObject.class.isAssignableFrom(clazz)) {
            if (!isDualDbSvcMode()) {
                addToTypeMap(clazz, _cfMap);
            } else if (KeyspaceUtil.isLocal(clazz)) {
                addToTypeMap(clazz, _cfMap);
            } else if (KeyspaceUtil.isGlobal(clazz)) {
                addToTypeMap(clazz, _geocfMap);
            } else {
                addToTypeMap(clazz, _geocfMap);
                addToTypeMap(clazz, _cfMap);
            }
        } else if (TimeSeries.class.isAssignableFrom(clazz)) {
            TimeSeriesType tsType = TypeMap.getTimeSeriesType(clazz);
            ColumnFamily cf = tsType.getCf();
            _cfMap.put(cf.getName(), cf);
            if (tsType.getCompactOptimized() &&
                    _dbCommonInfo != null &&
                    Boolean.TRUE.toString().equalsIgnoreCase(
                            _dbCommonInfo.getProperty(DbClientImpl.DB_STAT_OPTIMIZE_DISK_SPACE, "false"))) {
                // modify TTL for Compaction Enable Series types
                int min_ttl = Integer.parseInt(_dbCommonInfo.getProperty(DbClientImpl.DB_LOG_MINIMAL_TTL,
                        "604800"));
                if (min_ttl < tsType.getTtl()) {
                    _log.info("Setting TTL for the CF {} equal to {}", cf.getName(), min_ttl);
                    tsType.setTtl(min_ttl);
                }
            }
        } else if (SchemaRecord.class.isAssignableFrom(clazz)) {
            SchemaRecordType srType = TypeMap.getSchemaRecordType();
            ColumnFamily cf = srType.getCf();
            _cfMap.put(cf.getName(), cf);
        } else if (GlobalLock.class.isAssignableFrom(clazz)) {
            GlobalLockType glType = TypeMap.getGlobalLockType();
            ColumnFamily cf = glType.getCf();
            _geocfMap.put(cf.getName(), cf);
        } else {
            throw new IllegalStateException("Failed to process Class " + clazz.getName());
        }
    }

    /**
     * Check to see if dependency is valid for the dataobject, geo-visible resource
     * or global lock. Make sure global and geo-visible objects are not referencing
     * local objects.
     * 
     * @param refType referenced dependency
     * @param clazz the dataobject type
     * @return true if validation passes
     */
    private boolean isDependencyValidated(Class refType, Class clazz) {
        _log.debug("Validating reference {} for Class {}", refType, clazz);
        // If this is global dataobject, reference should be global or geo-visible
        if (DataObject.class.isAssignableFrom(clazz) && KeyspaceUtil.isGlobal(clazz)) {
            if (GeoVisibleResource.class.isAssignableFrom(refType)) {
                return true;
            } else if (DataObject.class.isAssignableFrom(refType) && KeyspaceUtil.isGlobal(refType)) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    private void addToTypeMap(Class clazz, Map<String, ColumnFamily> useCfMap) {
    	DependencyInterceptor dependencyInterceptor = new DependencyInterceptor(this.modelClasses);
    	
        Map<String, List<String>> indexCfTypeMap = new HashMap<String, List<String>>();
        DataObjectType doType = TypeMap.getDoType(clazz);
        useCfMap.put(doType.getCF().getName(), doType.getCF());
        boolean include = (clazz.getAnnotation(ExcludeFromGarbageCollection.class) == null);
        Iterator<ColumnField> it = doType.getColumnFields().iterator();
        while (it.hasNext()) {
            ColumnField field = it.next();
            if (field.getIndex() == null) {
                continue;
            }
            useCfMap.put(field.getIndexCF().getName(), field.getIndexCF());
            // for dependency processing
            if (field.getIndexRefType() != null) {
                _log.info(" index: " + field.getIndex().getClass().getSimpleName() + " class: " + clazz.getSimpleName() + " field: "
                        + field.getName() + " indexCF: " + field.getIndexCF().getName() + " reference: " + field.getIndexRefType());
                if (isDuplicateIndexCf(field, indexCfTypeMap)) {
                    _log.error("Class: {} has muliple indexed columns of the same type configured to use the same index column family: {}",
                            clazz.getName(), field.getIndexCF().getName());
                    throw new IllegalStateException("Class: " + clazz.getName()
                            + " has muliple indexed columns of the same type configured to use the same index column family: "
                            + field.getIndexCF().getName());
                }
                // check first before adding the dependency
                if (!isDependencyValidated(field.getIndexRefType(), clazz)) {
                    _log.error("Class: {} is a global object but it has illegal reference to a non-global dependency: {}", clazz.getName(),
                            field.getIndexRefType());
                    throw new IllegalStateException("Class: " + clazz.getName()
                            + " is a global object but it has illegal reference to a non-global dependency: " + field.getIndexRefType());
                }
                
                if (dependencyInterceptor.isConcretModelClass(field.getIndexRefType())) {
                    _dependencyTracker.addDependency(field.getIndexRefType(), clazz, field);
                } else {
                	_log.info("reference type is abstract class, need special process");
                	dependencyInterceptor.handleDependency(_dependencyTracker, clazz, field);
                }
            }
        }
        if (include) {
            _dependencyTracker.includeClass(clazz);
        } else {
            _dependencyTracker.excludeClass(clazz);
        }
    }

    /**
     * @param field
     * @param clazz
     * @return
     */
    private boolean isDuplicateIndexCf(ColumnField field, Map<String, List<String>> indexCfTypeMap) {
        boolean isDuplicate = false;
        if (indexCfTypeMap.get(field.getIndexCF().getName()) == null) {
            List<String> typeList = new ArrayList<String>();
            typeList.add(field.getIndexRefType().getSimpleName());
            indexCfTypeMap.put(field.getIndexCF().getName(), typeList);
        } else if (indexCfTypeMap.get(field.getIndexCF().getName()).contains(field.getIndexRefType().getSimpleName())) {
            isDuplicate = true;
        } else {
            indexCfTypeMap.get(field.getIndexCF().getName()).add(field.getIndexRefType().getSimpleName());
        }
        return isDuplicate;
    }

    public boolean isDualDbSvcMode() {
        return _dualDbSvcMode;
    }

    public void setDualDbSvcMode(boolean dualDbSvcMode) {
        this._dualDbSvcMode = dualDbSvcMode;
    }
}
