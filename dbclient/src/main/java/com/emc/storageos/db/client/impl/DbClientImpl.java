/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.datastax.driver.core.*;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.client.DbViewQuery;
import org.apache.cassandra.serializers.BooleanSerializer;
import org.apache.cassandra.utils.UUIDGen;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.ConstraintImpl;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.exceptions.FatalDatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;

/**
 * Default database client implementation
 */
public class DbClientImpl implements DbClient {
    private static final int COMPLETED_PROGRESS = 100;
    public static final String DB_STAT_OPTIMIZE_DISK_SPACE = "DB_STAT_OPTIMIZE_DISK_SPACE";
    public static final String DB_LOG_MINIMAL_TTL = "DB_LOG_MINIMAL_TTL";
    public static final String DB_CASSANDRA_OPTIMIZED_COMPACTION_STRATEGY = "DB_CASSANDRA_OPTIMIZED_COMPACTION_STRATEGY";
    public static final String DB_CASSANDRA_GC_GRACE_PERIOD = "DB_CASSANDRA_GC_GRACE_PERIOD";
    public static final String DB_CASSANDRA_INDEX_GC_GRACE_PERIOD = "DB_CASSANDRA_INDEX_GC_GRACE_PERIOD";
    public static final String DB_CASSANDRA_GC_GRACE_PERIOD_PREFIX = "DB_CASSANDRA_GC_GRACE_PERIOD_";

    private static final Logger _log = LoggerFactory.getLogger(DbClientImpl.class);
    private static final int DEFAULT_TS_PAGE_SIZE = 100;
    private static final int DEFAULT_BATCH_SIZE = 1000;
    protected static final int DEFAULT_PAGE_SIZE = 100;

    static private final List<Class<? extends DataObject>> excludeClasses = Arrays.asList(Token.class,
            StorageOSUserDAO.class, VirtualDataCenter.class, PropertyListDataObject.class, PasswordHistory.class,
            CustomConfig.class, VdcVersion.class, StorageSystemType.class);

    protected DbClientContext localContext;
    protected DbClientContext geoContext;

    private String keyspaceName;
    private String localDbSvcName = Constants.DBSVC_NAME;
    private String geoDbSvcName = Constants.GEODBSVC_NAME;
    private String clusterName;

    private DbVersionInfo _dbVersionInfo;
    private boolean _bypassMigrationLock;

    protected CoordinatorClient _coordinator;

    protected IndexCleaner _indexCleaner;

    protected EncryptionProvider _encryptionProvider;
    protected EncryptionProvider _geoEncryptionProvider;

    private boolean initDone = false;
    private String _geoVersion;
    private DrUtil drUtil;
    private int logInterval = 1800; //seconds
    private int logCount = 5;
    private KeyspaceTracerFactoryImpl tracer;

    public String getGeoVersion() {
        if (this._geoVersion == null) {
            this._geoVersion = VdcUtil.getMinimalVdcVersion();
        }
        return _geoVersion;
    }

    public void setLocalContext(DbClientContext localContext) {
        this.localContext = localContext;
    }

    public DbClientContext getLocalContext() {
        return localContext;
    }

    public void setGeoContext(DbClientContext geoContext) {
        this.geoContext = geoContext;
    }

    public DbClientContext getGeoContext() {
        return geoContext;
    }

    public int getLogInterval() {
        return logInterval;
    }

    public void setLogInterval(int logInterval) {
        this.logInterval = logInterval;
    }

    /**
     * customize the cluster name
     *
     * @param cn
     */
    public void setClusterName(String cn) {
        clusterName = cn;
    }

    /**
     * customize the keyspace name; keyspace name is the same for both local/default and global dbsvc's
     *
     * @param ks
     */
    public void setKeyspaceName(String ks) {
        keyspaceName = ks;
    }

    public void setDbVersionInfo(DbVersionInfo dbVersionInfo) {
        _dbVersionInfo = dbVersionInfo;
    }

    // only called once when Spring initialization, so it's safe to suppress
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    public void setCoordinatorClient(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    // only called once when Spring initialization, so it's safe to suppress
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    public CoordinatorClient getCoordinatorClient() {
        return _coordinator;
    }

    /**
     * Sets geo encryption provider
     *
     * @param encryptionProvider
     */

    // only called once when Spring initialization, so it's safe to suppress
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    public void setGeoEncryptionProvider(EncryptionProvider encryptionProvider) {
        _geoEncryptionProvider = encryptionProvider;
    }

    /**
     * Sets encryption provider
     *
     * @param encryptionProvider
     */
    // only called once when Spring initialization, so it's safe to suppress
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        _encryptionProvider = encryptionProvider;
    }

    /**
     * Sets whether to bypass the migration lock checking or not
     *
     * @param bypassMigrationLock
     *            if false, wait until MIGRATION_DONE is set before proceed with start()
     *            if true, wait until INIT_DONE is set before proceed with start()
     */
    public void setBypassMigrationLock(boolean bypassMigrationLock) {
        _bypassMigrationLock = bypassMigrationLock;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    @Override
    public synchronized void start() {
        if (initDone) {
            return;
        }

        try {
            _coordinator.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        if (_encryptionProvider != null) {
            _encryptionProvider.start();
        }

        if (_geoEncryptionProvider != null) {
            _geoEncryptionProvider.start();
        }

        TypeMap.setEncryptionProviders(_encryptionProvider, _geoEncryptionProvider);

        setupContext();

        _indexCleaner = new IndexCleaner();

        tracer = new KeyspaceTracerFactoryImpl();

        initDone = true;
    }

    public boolean isInitDone() {
        return initDone;
    }

    protected void setupContext() {
        if (localContext != null) {
            setupContext(localContext, localDbSvcName);
        }
        if (geoContext != null) {
            setupContext(geoContext, geoDbSvcName);
        }
    }

    protected void setupContext(DbClientContext ctx, String dbSvcName) {
        if (keyspaceName != null) {
            ctx.setKeyspaceName(keyspaceName);
        }

        if (clusterName != null) {
            ctx.setClusterName(clusterName);
        }

        // wait for schema init
        DbServiceStatusChecker statusChecker = new DbServiceStatusChecker();
        statusChecker.setCoordinator(_coordinator);
        statusChecker.setVersion(_dbVersionInfo.getSchemaVersion());
        statusChecker.setServiceName(dbSvcName);

        DbVersionInfo versionInfo = new DbVersionInfo();
        versionInfo.setSchemaVersion(_dbVersionInfo.getSchemaVersion());
        statusChecker.setDbVersionInfo(versionInfo);
        _coordinator.setDbVersionInfo(versionInfo);

        if (_bypassMigrationLock) {
            statusChecker.waitForAnyNodeInitDone();
        } else {
            statusChecker.waitForMigrationDone();
        }

        HostSupplierImpl hostSupplier = new HostSupplierImpl();
        hostSupplier.setDbClientVersion(_dbVersionInfo.getSchemaVersion());
        hostSupplier.setCoordinatorClient(_coordinator);
        hostSupplier.setDbSvcName(dbSvcName);
        ctx.init(hostSupplier);
    }

    /**
     * returns either local or geo keyspace depending on class annotation or id of dataObj,
     * for query requests only
     *
     * @param dataObj
     * @return
     */
    protected Session getSession(DataObject dataObj) {
        Class<? extends DataObject> clazz = dataObj.getClass();
        return getSession(clazz);
    }
    
    /**
     * Get local or geo cassandra session keyspace depending on class annotation of clazz 
     * @param clazz class information of data object
     * @return cassandra session
     */
    protected <T extends DataObject> Session getSession(Class<T> clazz) {
        return getDbClientContext(clazz).getSession();
    }
    
    public <T extends DataObject> DbClientContext getDbClientContext(Class<T> clazz) {
        DbClientContext ctx = null;
        if (localContext == null && geoContext == null) {
            throw new IllegalStateException();
        } else if (localContext == null && geoContext != null) {
            ctx = geoContext;
        } else if (geoContext == null && localContext != null) {
            ctx = localContext;
        } else {
            ctx = KeyspaceUtil.isGlobal(clazz) ? geoContext : localContext;
        }
        return ctx;
    }

    protected <T extends DataObject> boolean shouldRetryFailedWriteWithLocalQuorum(Class<T> clazz) {
        return getDbClientContext(clazz).isRetryFailedWriteWithLocalQuorum();
    }

    @Override
    public synchronized void stop() {
        if (localContext != null) {
            localContext.stop();
            localContext = null;
        }

        if (geoContext != null) {
            geoContext.stop();
            geoContext = null;
        }

        _log.info("stop coordinator");
        _coordinator.stop();
        initDone = false;
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, NamedURI id) {
        tracer.newTracer("read");
        return queryObject(clazz, id.getURI());
    }

    @Override
    public DataObject queryObject(URI id) {
        tracer.newTracer("read");
        Class<? extends DataObject> clazz = URIUtil.getModelClass(id);

        return queryObject(clazz, id);
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI id) {
        tracer.newTracer("read");
        List<URI> ids = new ArrayList<>(1);
        ids.add(id);

        List<T> objs = queryObject(clazz, ids);

        if (objs.isEmpty()) {
            return null;
        }

        return objs.get(0);
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, URI... id) {
        tracer.newTracer("read");
        return queryObject(clazz, Arrays.asList(id));
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids) {
        tracer.newTracer("read");
        return queryObject(clazz, ids, false);
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids, boolean activeOnly) {
        tracer.newTracer("read");
        DataObjectType doType = TypeMap.getDoType(clazz);

        if (doType == null) {
            throw new IllegalArgumentException();
        }

        if (!ids.iterator().hasNext()) {
            // nothing to do, just an empty list
            return new ArrayList<T>();
        }

        DbClientContext context = getDbClientContext(clazz);
        Map<String, List<CompositeColumnName>> result = queryRowsWithAllColumns(getDbClientContext(clazz), ids, doType.getCF().getName());
        List<T> objects = new ArrayList<T>(result.size());
        IndexCleanupList cleanList = new IndexCleanupList();

        for (String rowKey : result.keySet()) {
            List<CompositeColumnName> rows = result.get(rowKey);
            if (rows == null || rows.size() == 0) {
                continue;
            }

            T object = doType.deserialize(clazz, rowKey, rows, cleanList, new LazyLoader(this));

            // filter base on activeOnly
            if (activeOnly) {
                if (!object.getInactive()) {
                    objects.add(object);
                }
            } else {
                objects.add(object);
            }
        } 
        
        if (!cleanList.isEmpty()) {
            boolean retryFailedWriteWithLocalQuorum = shouldRetryFailedWriteWithLocalQuorum(clazz);
            RowMutator mutator = new RowMutator(context, retryFailedWriteWithLocalQuorum);
            SoftReference<IndexCleanupList> indexCleanUpRef = new SoftReference<IndexCleanupList>(cleanList);
            _indexCleaner.cleanIndexAsync(mutator, doType, indexCleanUpRef);
        }
        return objects;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(final Class<T> clazz,
            Collection<URI> ids) {
        tracer.newTracer("read");
        return queryIterativeObjects(clazz, ids, false);
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(final Class<T> clazz,
            Collection<URI> ids, final boolean activeOnly) {
        tracer.newTracer("read");
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null || ids == null) {
            throw new IllegalArgumentException();
        }
        if (!(ids.iterator().hasNext())) {
            // nothing to do, just an empty list
            return new ArrayList<T>().iterator();
        }
        BulkDataObjQueryResultIterator<T> bulkQueryIterator = new BulkDataObjQueryResultIterator<T>(ids.iterator()) {

            @Override
            protected void run() {
                currentIt = null;
                getNextBatch();
                while (!nextBatch.isEmpty()) {
                    List<T> currBatchResults = queryObject(clazz, nextBatch, activeOnly);
                    if (!currBatchResults.isEmpty()) {
                        currentIt = currBatchResults.iterator();
                        break;
                    }

                    getNextBatch();
                }
            }
        };

        return bulkQueryIterator;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectField(final Class<T> clazz,
            final String fieldName, Collection<URI> ids) {
        tracer.newTracer("read");
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null || ids == null) {
            throw new IllegalArgumentException();
        }
        if (!(ids.iterator().hasNext())) {
            // nothing to do, just an empty list
            return new ArrayList<T>().iterator();
        }

        BulkDataObjQueryResultIterator<T> bulkQueryIterator = new BulkDataObjQueryResultIterator<T>(ids.iterator()) {
            @Override
            protected void run() {
                currentIt = null;
                getNextBatch();
                while (!nextBatch.isEmpty()) {
                    List<T> currBatchResults = queryObjectField(clazz, fieldName, nextBatch);
                    if (!currBatchResults.isEmpty()) {
                        currentIt = currBatchResults.iterator();
                        break;
                    }

                    getNextBatch();
                }
            }
        };

        return bulkQueryIterator;
    }

    @Override
    public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName, Collection<URI> ids) {
        tracer.newTracer("read");
        Set<String> fieldNames = new HashSet<>(1);
        fieldNames.add(fieldName);
        Iterator<T> iterator = queryObjectFields(clazz, fieldNames, ids).iterator();

        List<T> objects = new ArrayList<T>();

        while (iterator.hasNext()) {
            T obj = iterator.next();
            objects.add(obj);
        }

        return objects;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectFields(final Class<T> clazz,
            final Collection<String> fieldNames, Collection<URI> ids) {
        tracer.newTracer("read");
        BulkDataObjQueryResultIterator<T> bulkQueryIterator = new BulkDataObjQueryResultIterator<T>(ids.iterator()) {

            @Override
            protected void run() {
                currentIt = null;
                getNextBatch();
                while (!nextBatch.isEmpty()) {
                    currentIt = queryObjectFields(clazz, fieldNames, nextBatch).iterator();

                    if (currentIt.hasNext()) {
                        break;
                    }

                    getNextBatch();
                }
            }
        };

        return bulkQueryIterator;
    }

    @Override
    public <T extends DataObject> Collection<T> queryObjectFields(Class<T> clazz,
            Collection<String> fieldNames, Collection<URI> ids) {
        tracer.newTracer("read");
        DataObjectType doType = TypeMap.getDoType(clazz);

        if (doType == null || ids == null) {
            throw new IllegalArgumentException();
        }

        if (ids.isEmpty()) {
            // nothing to do, just an empty list
            return new ArrayList<T>();
        }

        Set<ColumnField> columnFields = new HashSet<ColumnField>(fieldNames.size());
        for (String fieldName : fieldNames) {
            ColumnField columnField = doType.getColumnField(fieldName);

            if (columnField == null) {
                throw new IllegalArgumentException();
            }

            columnFields.add(columnField);
        }

        Map<URI, T> objectMap = new HashMap<URI, T>();
        for (ColumnField columnField : columnFields) {
            Map<String, List<CompositeColumnName>> result = queryRowsWithAColumn(getDbClientContext(clazz), ids, doType.getCF().getName(), columnField);
            for (String rowKey : result.keySet()) {
                List<CompositeColumnName> rows = result.get(rowKey);
                if (rows == null || rows.size() == 0) {
                    continue;
                }
                try {
                    // Since the order of columns returned is not guaranteed to be the same as keys
                    // we have to create an object to track both id and label, for now, lets use the same type
                    
                    URI key = URI.create(rowKey);

                    T obj = objectMap.get(key);

                    if (obj == null) {
                        obj = (T) DataObject.createInstance(clazz, URI.create(rowKey));
                        objectMap.put(key, obj);
                    }

                    Iterator<CompositeColumnName> columnIterator = rows.iterator();
                    while (columnIterator.hasNext()) {
                        columnField.deserialize(columnIterator.next(), obj);
                    }
                } catch (final InstantiationException e) {
                    throw DatabaseException.fatals.queryFailed(e);
                } catch (final IllegalAccessException e) {
                    throw DatabaseException.fatals.queryFailed(e);
                }
            }
        }

        // Begin tracking changes
        for (T obj : objectMap.values()) {
            obj.trackChanges();
        }

        return objectMap.values();
    }

    @Override
    public <T extends DataObject> void aggregateObjectField(Class<T> clazz, Iterator<URI> ids,
            DbAggregatorItf aggregator) {
        tracer.newTracer("read");
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }

        String[] fields = aggregator.getAggregatedFields();
        Arrays.sort(fields);
        
        for (int ii = 0; ii < fields.length; ii++) {
            ColumnField columnField = doType.getColumnField(fields[ii]);
            if (columnField == null) {
                throw new IllegalArgumentException();
            }
            if (fields.length > 1) {
                // ***** multiple columns aggregation can be done only for non-indexed columns. ******
                if (columnField.getIndex() != null || columnField.getType() != ColumnField.ColumnType.Primitive) {
                    throw DatabaseException.fatals.queryFailed(new Exception("... "));
                }
            }
        }

        List<String> idList = new ArrayList<String>();
        while (ids.hasNext()) {
            idList.clear();
            for (int ii = 0; ii < DEFAULT_BATCH_SIZE && ids.hasNext(); ii++) {
                idList.add(ids.next().toString());
            }

            aggregateObjectFieldBatch(aggregator, clazz, idList, doType, fields);
        }
    }

    private <T extends DataObject> void aggregateObjectFieldBatch(
            DbAggregatorItf aggregator, Class<T> clazz, List<String> strIds, DataObjectType doType, String[] fields) {
        
        ResultSet resultSet;
        try {
            PreparedStatement preparedStatement = getDbClientContext(clazz).getPreparedStatement(
                    String.format("select * from \"%s\" where column1>=? and column1<=? and key in ? ALLOW FILTERING",
                            doType.getCF().getName()));
            resultSet = getDbClientContext(clazz).getSession()
                    .execute(preparedStatement.bind(fields[0], fields[fields.length - 1], strIds));
            
        } catch (DriverException e) {
            throw DatabaseException.retryables.operationFailed(e);
        }
        
        Map<String, List<CompositeColumnName>> queryResult = toColumnMap(resultSet);
        for (String rowKey : queryResult.keySet()) {
            List<CompositeColumnName> columns = queryResult.get(rowKey);
            aggregator.aggregate(columns);
        }
    }

    private static class MyIterator implements Iterator<Volume> {

        private ResultSet rs;
        public MyIterator(ResultSet rs) {
            this.rs = rs;
        }

        @Override
        public boolean hasNext() {
            return rs.iterator().hasNext();
        }

        @Override
        public Volume next() {
            Row row = rs.iterator().next();
            for (ColumnDefinitions.Definition colDef: row.getColumnDefinitions().asList()) {
                _log.info("====== Columns returned by vol view: {}", colDef.getName());
            }

            Volume vol = new Volume();
            vol.setType(row.getInt("type"));
            vol.setLabel(row.getString("label"));
            vol.setId(URI.create(row.getString("id")));
            return vol;
        }
    }

        /**
     * This class is used to filter unwanted rows while streaming from Cassandra.
     *
     * Sub classes should override shouldFilter() method to apply additional filtering logic.
     */
    private static class FilteredCfScanIterator implements Iterator<URI> {

        private final ResultSet queryResult;
        private URI cached;

        public FilteredCfScanIterator(ResultSet queryResult) {
            this.queryResult = queryResult;
        }

        protected boolean shouldFilter(com.datastax.driver.core.Row row) {
            return row.getColumnDefinitions().size() == 0;
        }

        private boolean tryAdvance() {
            for (com.datastax.driver.core.Row row : queryResult) {
                String rowKey = row.getString(0);
                if (shouldFilter(row)) {
                    continue;
                }
                this.cached = URI.create(rowKey);
                return true;
            }
            return false;
        }

        @Override
        public boolean hasNext() {
            if (this.cached != null) {
                return true;
            }
            return tryAdvance();
        }

        @Override
        public URI next() {
            if (this.cached == null && !tryAdvance()) {
                throw new NoSuchElementException();
            }

            URI tmp = this.cached;
            this.cached = null;
            return tmp;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private <T extends DataObject> ResultSet scanRowsByType(Class<T> clazz, URI startId, int count) {
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }
        try {
            StringBuilder queryString = new StringBuilder();
            List<Object> bindObjects = new ArrayList<Object>(); 
            
            queryString.append("select key, value, writetime(value) from \"").append(doType.getCF().getName()).append("\"");
            queryString.append(" where column1=? ");
            bindObjects.add("inactive");
            
            if (startId != null) {
                queryString.append(" and token(key)>token(?)");
                bindObjects.add(startId);
            }
            
            if (count != Integer.MAX_VALUE) {
                queryString.append(" limit ").append(count);
            }
            
            queryString.append(" ALLOW FILTERING");
            _log.info("Query String: {}", queryString);
            
            PreparedStatement preparedStatement = getDbClientContext(clazz).getPreparedStatement(queryString.toString());
            BoundStatement boundStatement = preparedStatement.bind(bindObjects.toArray());
            
            return getDbClientContext(clazz).getSession().execute(boundStatement);
        } catch (DriverException e) {
            throw DatabaseException.retryables.operationFailed(e);
        }
    }

    /**
     *
     * @param clazz
     * @param inactiveValue If null, don't care about the .inactive field and return all keys. Otherwise, return rows matching only
     *            specified value.
     * @param startId
     * @param count
     * @param <T>
     * @return
     * @throws DatabaseException
     */
    private <T extends DataObject> URIQueryResultList scanByType(Class<T> clazz, final Boolean inactiveValue, URI startId, int count) {
        final ResultSet resultSet = scanRowsByType(clazz, startId, count);

        URIQueryResultList result = new URIQueryResultList();

        result.setResult(new FilteredCfScanIterator(resultSet) {
            @Override
            protected boolean shouldFilter(com.datastax.driver.core.Row row) {
                if (super.shouldFilter(row)) {
                    return true;
                }

                if (inactiveValue != null && BooleanSerializer.instance.deserialize(row.getBytes(1)) != inactiveValue.booleanValue()) {
                    return true;
                }

                return false;
            }
        });

        return result;
    }

    @Override
    public <T extends DataObject> void queryInactiveObjects(Class<T> clazz, final long timeBefore, QueryResultList<URI> result) {
        tracer.newTracer("read");
        if (clazz.getAnnotation(NoInactiveIndex.class) != null) {
            final ResultSet queryResult = scanRowsByType(clazz, null, Integer.MAX_VALUE);

            result.setResult(new FilteredCfScanIterator(queryResult) {
                @Override
                protected boolean shouldFilter(com.datastax.driver.core.Row row) {
                    if (super.shouldFilter(row)) {
                        return true;
                    }
                    
                    if (!BooleanSerializer.instance.deserialize(row.getBytes(1))) {
                        return true;
                    }

                    if (timeBefore <= row.getTime(2)) {
                        return true;
                    }

                    return false;
                }
            });
        } else {
            queryByConstraint(
                    DecommissionedConstraint.Factory.getDecommissionedObjectsConstraint(
                            clazz, timeBefore),
                    result);
        }
    }

    /**
     *
     * @param clazz object type
     * @param activeOnly if true, gets only active object ids. NOTE: For classes marked with NoInactiveIndex, there could be 2 cases:
     *            a. The class does not use .inactive field at all, which means all object instances with .inactive == null
     *            b. The class does make use of .inactive field, just don't want to put it into Decommissioned index
     *            When querying type A classes, you can only specify activeOnly == false, otherwise you get nothing
     *            When querying type B classes, you can specify activeOnly freely as normal classes
     * @param <T>
     * @return
     * @throws DatabaseException
     */
    @Override
    public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly) {
        tracer.newTracer("read");
        if (clazz.getAnnotation(NoInactiveIndex.class) != null) {
            // A class not indexed by Decommissioned CF, we can only scan entire CF for it
            return scanByType(clazz, activeOnly ? false : null, null, Integer.MAX_VALUE);
        }

        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }
        URIQueryResultList result = new URIQueryResultList();
        DecommissionedConstraint constraint;
        if (activeOnly) {
            constraint = DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, false);
        } else {
            constraint = DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, null);
        }
        
        constraint.setDbClientContext(getDbClientContext(clazz));
        constraint.execute(result);

        return result;
    }

    @Override
    public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly, URI startId, int count) {
        tracer.newTracer("read");
        URIQueryResultList result;

        if (clazz.getAnnotation(NoInactiveIndex.class) != null) {
            // A class not indexed by Decommissioned CF, we can only scan entire CF for it
            result = scanByType(clazz, activeOnly ? false : null, startId, count);
        } else {
            DataObjectType doType = TypeMap.getDoType(clazz);
            if (doType == null) {
                throw new IllegalArgumentException();
            }
            result = new URIQueryResultList();

            ConstraintImpl constraint;

            if (activeOnly) {
                constraint = (ConstraintImpl) DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, !activeOnly);
            } else {
                constraint = (ConstraintImpl) DecommissionedConstraint.Factory.getAllObjectsConstraint(clazz, null);
            }

            constraint.setStartId(startId);
            constraint.setPageCount(count);

            constraint.setDbClientContext(getDbClientContext(clazz));
            constraint.execute(result);
        }

        List<URI> ids = new ArrayList<>();

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            ids.add(it.next());
        }

        return ids;
    }

    @Override
    public List<URI> queryByConstraint(Constraint constraint) {
        tracer.newTracer("read");
        /* TODO: This API will be removed with Grace's patch */
        URIQueryResultList result = new URIQueryResultList();
        queryByConstraint(constraint, result);

        List<URI> out = new ArrayList<>();
        while (result.iterator().hasNext()) {
            out.add(result.iterator().next());
        }

        return out;
    }

    @Override
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result) {
    	tracer.newTracer("read");
    	ConstraintImpl constraintImpl = (ConstraintImpl) constraint;
    	if (!constraintImpl.isValid()) {
    		throw new IllegalArgumentException("invalid constraint: the key can't be null or empty");
    	}

        constraint.setDbClientContext(this.getDbClientContext(constraint.getDataObjectType()));
        constraint.execute(result);
    }

    @Override
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result, URI startId, int maxCount) {
        tracer.newTracer("read");
        ConstraintImpl constraintImpl = (ConstraintImpl) constraint;

        if (!constraintImpl.isValid()) {
            throw new IllegalArgumentException("invalid constraint: the key can't be null or empty");
        }

        constraintImpl.setStartId(startId);

        constraintImpl.setPageCount(maxCount);

        constraint.setDbClientContext(this.getDbClientContext(constraint.getDataObjectType()));
        constraint.execute(result);
    }

    @Override
    public DbViewQuery getDbViewQuery() {
        return new DbViewQueryImpl(this);
    }

    @Override
    public void listVolumesByProject(URI project, int type, QueryResultList<Volume> volumes) {
        DbClientContext dbCtx = getDbClientContext(Volume.class);
        String cql = "select * from vol_view where project = ? and type = ?";
        PreparedStatement queryStatement = dbCtx.getPreparedStatement(cql);

        BoundStatement bindStmt = queryStatement.bind(project.toString(), type);
        bindStmt.setFetchSize(DEFAULT_TS_PAGE_SIZE);

        ResultSet rs = getDbClientContext(Volume.class).getSession().execute(bindStmt);
        volumes.setResult(new MyIterator(rs));
    }

    // This is used to count the number of volumes or fileshares in a storagepool,
    // and the number of volumes or fileshares in a storage system
    @Override
    public Integer countObjects(Class<? extends DataObject> clazz, String columnField, URI uri) {
        tracer.newTracer("read");
        DataObjectType doType = TypeMap.getDoType(clazz);
        if (doType == null) {
            throw new IllegalArgumentException();
        }
        ColumnField field = doType.getColumnField(columnField);
        if (field == null) {
            throw new IllegalArgumentException();
        }
        try {
            String queryString = String.format("select count(*) from \"%s\" where column1='%s' and key='%s' ALLOW FILTERING;",
                    field.getIndexCF().getName(), clazz.getSimpleName(), uri.toString());
            ResultSet resultSet = this.getDbClientContext(clazz).getSession().execute(queryString);
            return (int)resultSet.one().getLong(0);
        } catch (DriverException e) {
            throw DatabaseException.retryables.operationFailed(e);
        }
    }

    @Override
    public <T extends DataObject> void createObject(T object) {
        tracer.newTracer("write");
        createObject(new DataObject[] { object });
    }

    /**
     * @deprecated use {@link DbClient#updateObject(T)} instead
     */
    @Deprecated
    @Override
    public <T extends DataObject> void persistObject(T object) {
        tracer.newTracer("write");
        internalPersistObject(object, true);
    }

    /**
     * @deprecated use {@link DbClient#updateObject(T)} instead
     */
    @Deprecated
    @Override
    public <T extends DataObject> void updateAndReindexObject(T object) {
        tracer.newTracer("write");
        internalPersistObject(object, true);
    }

    @Override
    public <T extends DataObject> void updateObject(T object) {
        tracer.newTracer("write");
        internalPersistObject(object, true);
    }

    private <T extends DataObject> void internalPersistObject(T object, boolean updateIndex) {
        DataObjectType doType = TypeMap.getDoType(object.getClass());
        if (doType == null || object.getId() == null) {
            throw new IllegalArgumentException();
        }
        internalPersistObject(Arrays.asList(new DataObject[] { object }), updateIndex);
    }

    @Override
    public <T extends DataObject> void createObject(Collection<T> dataobjects) {
        tracer.newTracer("write");
        for (T object : dataobjects) {
            object.setCreationTime(Calendar.getInstance());
            if (!object.getInactive()) {
                object.setInactive(false);
            }
        }
        internalIterativePersistObject(dataobjects, false);
    }

    /**
     * @deprecated use {@link DbClient#updateObject(Collection)} instead
     */
    @Deprecated
    @Override
    public <T extends DataObject> void persistObject(Collection<T> dataobjects) {
        tracer.newTracer("write");
        internalIterativePersistObject(dataobjects, true);
    }

    /**
     * @deprecated use {@link DbClient#updateObject(Collection)} instead
     */
    @Deprecated
    @Override
    public <T extends DataObject> void updateAndReindexObject(Collection<T> dataobjects) {
        tracer.newTracer("write");
        internalIterativePersistObject(dataobjects, true);
    }

    @Override
    public <T extends DataObject> void updateObject(Collection<T> objects) {
        tracer.newTracer("write");
        internalIterativePersistObject(objects, true);
    }

    /**
     * Print a stack trace to show the originator of the persist request.
     * Used to detect anti-patterns. Calling this method is only for logging purposes.
     *
     * @param obj
     *            object to print stack
     */
    private <T> void printPersistedObject(T obj) {
        final String filterClasses[] = { "Workflow", "WorkflowStep", "WorkflowStepData", "Task" };
        ArrayList<String> filterList = new ArrayList<>(Arrays.asList(filterClasses));
        if (obj instanceof DataObject && !filterList.contains(obj.getClass().getSimpleName())) {
            DataObject dobj = (DataObject) obj;

            StackTraceElement[] elements = Thread.currentThread().getStackTrace();
            StringBuffer sb = new StringBuffer("Persisting obj: " + dobj.getId() + "\n");
            sb.append("InActive: " + ((DataObject) obj).getInactive() + "\n");
            sb.append("====================================================================================================\n");
            for (int i = 0; i < elements.length; i++) {
                if (elements[i].getClassName().contains("storageos")) {
                    sb.append("\t\t" + elements[i] + "\n");
                }
            }
            sb.append("====================================================================================================");
            _log.info(sb.toString());
        }
    }

    private <T extends DataObject>
            void internalPersistObject(Collection<T> dataobjects,
                    boolean updateIndex) {
        if (dataobjects == null || dataobjects.isEmpty()) {
            return;
        }
        Map<Class<? extends T>, List<T>> typeObjMap = new HashMap<Class<? extends T>, List<T>>();
        for (T obj : dataobjects) {
            List<T> objTypeList = typeObjMap.get(obj.getClass());
            if (objTypeList == null) {
                objTypeList = new ArrayList<T>();
                typeObjMap.put((Class<? extends T>) obj.getClass(), objTypeList);
            }
            objTypeList.add(obj);

            // Instrumentation for the benefit of finding anti-patterns. This can be removed or
            // encapsulated around a system property
            // printPersistedObject(obj);
        }

        for (Entry<Class<? extends T>, List<T>> entry : typeObjMap.entrySet()) {
            internalPersistObject(entry.getKey(), entry.getValue(), updateIndex);
        }
    }

    protected <T extends DataObject> void internalPersistObject(Class<? extends T> clazz, Collection<T> dataobjects, boolean updateIndex) {
        if (dataobjects == null || dataobjects.isEmpty()) {
            return;
        }

        DbClientContext context = getDbClientContext(clazz);

        List<URI> objectsToCleanup = insertNewColumns(context, dataobjects); // now the list has objects whose indexed column get changed.
        if (updateIndex && !objectsToCleanup.isEmpty()) {
            Map<String, List<CompositeColumnName>> rows = fetchNewest(clazz, getDbClientContext(clazz), objectsToCleanup);
            Map<String, List<DbViewMetaRecord>> viewMetadatas = fetchAllRelatedViews(clazz, getDbClientContext(clazz), objectsToCleanup);
            cleanupOldColumns(clazz, rows, viewMetadatas);
        }
    }

    private <T extends DataObject> Map<String, List<DbViewMetaRecord>> fetchAllRelatedViews(Class<T> clazz, DbClientContext dbCtx, List<URI> objectsToCleanup) {
        String viewName;

        DataObjectType dataType = TypeMap.getDoType(clazz);
        DbViewDefinition volViewDef = dataType.getViewDefMap().get("vol_view");

        Map<String, List<DbViewMetaRecord>> viewMetadataMap = new HashMap<>();

        String cql = "select * from vol_view_meta where id in ?";

        PreparedStatement queryAllColumnsPreparedStatement = dbCtx.getPreparedStatement(cql);
        ResultSet rs = dbCtx.getSession()
                .execute(queryAllColumnsPreparedStatement.bind(uriList2StringList(objectsToCleanup)));

        // Todo: should get columns by view definition
        for (Row row : rs) {
            DbViewMetaRecord viewMeta = new DbViewMetaRecord(volViewDef);
            String id = row.get(0, String.class);
            viewMeta.setKeyValue(id);
            viewMeta.setTimeUUID(row.getUUID(1));
            viewMeta.addColumn(new ViewColumn("project", row.get(2, String.class), String.class));
            viewMeta.addColumn(new ViewColumn("type", row.get(3, Integer.class), Integer.class));

            List<DbViewMetaRecord> viewMetaRecords = viewMetadataMap.putIfAbsent(id, new ArrayList());
            if (viewMetaRecords == null) viewMetaRecords = viewMetadataMap.get(id);
            viewMetaRecords.add(viewMeta);
        }

        _log.info("========== viewMetadataMap size is {}, keys: {}", viewMetadataMap.size(), viewMetadataMap.keySet());
        return viewMetadataMap;
    }

    protected <T extends DataObject> List<URI> insertNewColumns(DbClientContext context, Collection<T> dataobjects) {
        List<URI> objectsToCleanup = new ArrayList<>();

        boolean retryFailedWriteWithLocalQuorum = true;
        Iterator<T> dataObjectIterator = dataobjects.iterator();
        if (dataObjectIterator.hasNext()) {
            T dataObject = dataObjectIterator.next();
            retryFailedWriteWithLocalQuorum = shouldRetryFailedWriteWithLocalQuorum(dataObject.getClass());
        }
        
        RowMutator mutator = new RowMutator(context, retryFailedWriteWithLocalQuorum);
        for (T object : dataobjects) {
            checkGeoVersionForMutation(object);
            DataObjectType doType = TypeMap.getDoType(object.getClass());

            if (object.getId() == null || doType == null) {
                throw new IllegalArgumentException();
            }
            if (doType.needPreprocessing()) {
                preprocessTypeIndexes(context, doType, object);
            }
            if (doType.serialize(mutator, object, new LazyLoader(this))) {
                objectsToCleanup.add(object.getId());
            }

            if (!(object instanceof Task)) {
                serializeTasks(object, mutator, objectsToCleanup);
            }
        }
        mutator.execute();

        return objectsToCleanup;
    }

    protected <T extends DataObject> Map<String, List<CompositeColumnName>> fetchNewest(Class<? extends T> clazz, DbClientContext context,
            List<URI> objectsToCleanup) {
        DataObjectType doType = TypeMap.getDoType(clazz);
        return queryRowsWithAllColumns(context, objectsToCleanup, doType.getCF().getName());
    }

    protected <T extends DataObject> void cleanupOldColumns(Class<? extends T> clazz, Map<String, List<CompositeColumnName>> rows, Map<String, List<DbViewMetaRecord>> viewMetadatas) {
        // cleanup old entries for indexed columns
        // CHECK - persist is called only with same object types for now
        // not sure, if this is an assumption we can make
        DataObjectType doType = TypeMap.getDoType(clazz);
        IndexCleanupList cleanList = new IndexCleanupList();
        for (String rowKey : rows.keySet()) {
            List<CompositeColumnName> columns = rows.get(rowKey);
            if (columns.isEmpty()) {
                continue;
            }
            doType.deserialize(clazz, rowKey, columns, cleanList, new LazyLoader(this));
        }

        addViewToCleanList(viewMetadatas, cleanList);

        if (!cleanList.isEmpty()) {
            boolean retryFailedWriteWithLocalQuorum = shouldRetryFailedWriteWithLocalQuorum(clazz);
            RowMutator cleanupMutator = new RowMutator(getDbClientContext(clazz), retryFailedWriteWithLocalQuorum);
            SoftReference<IndexCleanupList> indexCleanUpRef = new SoftReference<IndexCleanupList>(cleanList);
            _indexCleaner.cleanIndex(cleanupMutator, doType, indexCleanUpRef);
        }
    }

    private void addViewToCleanList(Map<String, List<DbViewMetaRecord>> viewMetadatas, IndexCleanupList cleanList) {
        List<DbViewRecord> viewRecords = new ArrayList<>();
        List<DbViewMetaRecord> viewMetaRecords = new ArrayList<>();

        for (List<DbViewMetaRecord> viewsPerObj : viewMetadatas.values()) {
            _log.info("======= addViewToCleanList: checking view meta perobject = {}", viewsPerObj);
            if (viewsPerObj.size() <= 1) { // only one means no need to clean
                _log.info("======= addViewToCleanList: nothing to clean for {}", viewsPerObj);
                continue;
            }
            viewsPerObj.remove(viewsPerObj.size()-1);
            viewMetaRecords.addAll(viewsPerObj);
            _log.info("======= addViewToCleanList: view meta to clean: {}", viewsPerObj);

            // generate db view records from view metadata.
            for (DbViewMetaRecord meta: viewsPerObj) {
                DbViewRecord viewRecord = new DbViewRecord(meta.getViewDef());
                viewRecord.setKeyValue((String) meta.getColumns().get(0).getValue());
                for (int i = 1; i < meta.getColumns().size(); i++) {
                    ViewColumn col = meta.getColumns().get(i);
                    viewRecord.addClusteringColumn(col.getName(), col.getValue());
                }
                viewRecord.addClusteringColumn(meta.getKeyName(), meta.getKeyValue());
                viewRecord.setTimeUUID(meta.getTimeUUID());

                viewRecords.add(viewRecord);
            }
        }

        _log.info("======= addViewToCleanList: viewRecords = {}", viewRecords);
        _log.info("======= addViewToCleanList: viewMetaRecords = {}", viewMetaRecords);
        cleanList.setDbViewRecords(viewRecords);
        cleanList.setDbViewMetaRecords(viewMetaRecords);
    }

    private <T extends DataObject> void preprocessTypeIndexes(DbClientContext context, DataObjectType doType, T object) {
        //todo replace Row/Keyspace in this methods
        boolean queried = false;//todo seems 'queried' is useless?
        Map<String, List<CompositeColumnName>> rows = null;

        // Before serializing an object, we might need to set referenced fields.
        List<ColumnField> refColumns = doType.getRefUnsetColumns(object);
        if (!refColumns.isEmpty()) {
            if (!queried) {
                rows = queryRowWithAllColumns(context, object.getId(), doType.getCF().getName());
                queried = true;
            }
            if (rows != null && rows.size() != 0) {
                String rowKey = object.getId().toString();
                doType.deserializeColumns(object, rows.get(rowKey), refColumns, true);
            }
        }

        rows = null;
        // We also might need to update dependent fields before serializing an object
        List<ColumnField> depColumns = doType.getDependentForModifiedColumns(object);
        if (!depColumns.isEmpty()) {
            if (!queried) {
                rows = queryRowWithAllColumns(context, object.getId(), doType.getCF().getName());
                queried = true;
            }
            // get object with value for dependent fields.
            if (rows != null && rows.size() != 0) {
                String rowKey = object.getId().toString();
                doType.deserializeColumns(object, rows.get(rowKey), depColumns, false);
            }
        }
    }

    private <T extends DataObject>
            void internalIterativePersistObject(Collection<T> dataobjects,
                    final boolean updateIndex) {
        if (dataobjects == null || dataobjects.isEmpty()) {
            return;
        }

        BulkDataObjPersistIterator<T> bulkPersistIterator = new BulkDataObjPersistIterator<T>(dataobjects.iterator()) {

            @Override
            protected void run() {
                internalPersistObject(nextBatch, updateIndex);
            }
        };

        while (bulkPersistIterator.hasNext()) {
            List<T> ids = bulkPersistIterator.next();
            internalPersistObject(ids, updateIndex);
        }
    }

    @Override
    public <T extends DataObject> void createObject(T... object) {
        tracer.newTracer("write");
        createObject(Arrays.asList(object));
    }

    /**
     * @deprecated use {@link DbClient#updateObject(T...)} instead
     */
    @Deprecated
    @Override
    public <T extends DataObject> void persistObject(T... object) {
        tracer.newTracer("write");
        internalPersistObject(Arrays.asList(object), true);
    }

    /**
     * @deprecated use {@link DbClient#updateObject(T...)} instead
     */
    @Deprecated
    @Override
    public <T extends DataObject> void updateAndReindexObject(T... object) {
        tracer.newTracer("write");
        internalPersistObject(Arrays.asList(object), true);
    }

    @Override
    public <T extends DataObject> void updateObject(T... object) {
        tracer.newTracer("write");
        internalPersistObject(Arrays.asList(object), true);
    }

    @Override
    @Deprecated
    public void setStatus(Class<? extends DataObject> clazz, URI id,
            String opId, String status) {
        tracer.newTracer("write");
        setStatus(clazz, id, opId, status, null);
    }

    @Override
    @Deprecated
    public void setStatus(Class<? extends DataObject> clazz, URI id, String opId, String status,
            String message) {
        tracer.newTracer("write");
        try {
            DataObject doobj = clazz.newInstance();
            doobj.setId(id);
            doobj.setOpStatus(new OpStatusMap());
            Operation op = new Operation();
            op.setStatus(status);
            if (message != null) {
                op.setMessage(message);
            }
            doobj.getOpStatus().put(opId, op);
            persistObject(doobj);
        } catch (InstantiationException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void markForDeletion(DataObject object) {
        tracer.newTracer("write");
        markForDeletion(Arrays.asList(new DataObject[] { object }));
    }

    @Override
    public void markForDeletion(Collection<? extends DataObject> objects) {
        tracer.newTracer("write");
        Iterator<? extends DataObject> it = objects.iterator();
        while (it.hasNext()) {
            it.next().setInactive(true);
        }
        updateObject(objects);
    }

    @Override
    public <T extends DataObject> void markForDeletion(T... object) {
        tracer.newTracer("write");
        markForDeletion(Arrays.asList(object));
    }

    @Override
    public void removeObject(DataObject... object) {
        tracer.newTracer("write");
        Map<Class<? extends DataObject>, List<DataObject>> typeObjMap = new HashMap<Class<? extends DataObject>, List<DataObject>>();
        for (DataObject obj : object) {
            List<DataObject> objTypeList = typeObjMap.get(obj.getClass());
            if (objTypeList == null) {
                objTypeList = new ArrayList<>();
                typeObjMap.put(obj.getClass(), objTypeList);
            }
            objTypeList.add(obj);
        }
        for (Entry<Class<? extends DataObject>, List<DataObject>> entry : typeObjMap.entrySet()) {
            if (entry.getKey().getAnnotation(NoInactiveIndex.class) == null) {
                _log.debug("Model class {} has no NoInactiveIndex. Call markForDeletion() to delete", entry.getKey());
                markForDeletion(entry.getValue());
            } else {
                List<DataObject> dbObjList = entry.getValue();
                removeObject(entry.getKey(), dbObjList.toArray(new DataObject[dbObjList.size()]));
            }
        }
    }

    public void removeObject(Class<? extends DataObject> clazz, DataObject... object) {
        tracer.newTracer("write");
        List<DataObject> allObjects = Arrays.asList(object);
        DbClientContext context = getDbClientContext(clazz);

        DataObjectType doType = null;
        RemovedColumnsList removedList = new RemovedColumnsList();
        for (DataObject dataObject : allObjects) {
            _log.debug("Try to remove data object {}", dataObject.getId());
            checkGeoVersionForMutation(dataObject);
            doType = TypeMap.getDoType(dataObject.getClass());
            // delete all the index columns for this object first
            if (doType == null) {
                throw new IllegalArgumentException();
            }
            Map<String, List<CompositeColumnName>> result = queryRowWithAllColumns(context, dataObject.getId(), doType.getCF().getName());
            for (String rowKey : result.keySet()) {
                for (CompositeColumnName row : result.get(rowKey)) {
                    removedList.add(rowKey, row);
                }
            }       
        }
        if (!removedList.isEmpty()) {
            boolean retryFailedWriteWithLocalQuorum = shouldRetryFailedWriteWithLocalQuorum(clazz);
            RowMutator mutator = new RowMutator(context, retryFailedWriteWithLocalQuorum);
            _indexCleaner.removeColumnAndIndex(mutator, doType, removedList);
        }
    }

    @Override
    public <T extends TimeSeriesSerializer.DataPoint> String insertTimeSeries(
            Class<? extends TimeSeries> tsType, T... data) {
    	tracer.newTracer("write");
        // time series are always in the local keyspace
        RowMutator mutator = new RowMutator(getLocalContext(), false);
        // todo check batch.lockCurrentTimestamp();
        // quorum is not required since there should be no duplicates
        // for reads, clients should expect read-after-write is
        // not guaranteed for time series data.
        TimeSeriesType<T> type = TypeMap.getTimeSeriesType(tsType);
        String rowId = type.getRowId();
        mutator.setWriteCL(ConsistencyLevel.ONE);
        for (T entry : data) {
            mutator.insertTimeSeriesColumn(type.getCf().getName(), rowId, UUIDs.timeBased(),
                    type.getSerializer().serialize(entry), type.getTtl());
        }
        mutator.execute();
        return rowId;
    }

    @Override
    public <T extends TimeSeriesSerializer.DataPoint> String insertTimeSeries(
            Class<? extends TimeSeries> tsType, DateTime time, T data) {
        tracer.newTracer("write");
        if (time == null || (time.getZone() != DateTimeZone.UTC)) {
            throw new IllegalArgumentException("Invalid timezone");
        }

        TimeSeriesType<T> type = TypeMap.getTimeSeriesType(tsType);
        String rowId = type.getRowId(time);
        UUID timeUUID = UUIDGen.getTimeUUID(time.getMillis());
        // time series are always in the local keyspace
        RowMutator mutator = new RowMutator(getLocalContext(), false);

        // quorum is not required since there should be no duplicates
        // for reads, clients should expect read-after-write is
        // not guaranteed for time series data.
        mutator.setWriteCL(ConsistencyLevel.ONE);
        mutator.insertTimeSeriesColumn(type.getCf().getName(), rowId, timeUUID, type.getSerializer().serialize(data), type.getTtl());
        mutator.execute();
        return rowId;
    }

    @Override
    public <T extends TimeSeriesSerializer.DataPoint>
            void queryTimeSeries(Class<? extends TimeSeries> tsType,
                    DateTime timeBucket,
                    final TimeSeriesQueryResult<T> result,
                    ExecutorService workerThreads) {
        tracer.newTracer("read");
        queryTimeSeries(tsType, timeBucket, null, result, workerThreads);
    }

    @Override
    public <T extends TimeSeriesSerializer.DataPoint>
            void queryTimeSeries(final Class<? extends TimeSeries> tsType, final DateTime timeBucket,
                    TimeSeriesMetadata.TimeBucket bucket, final TimeSeriesQueryResult<T> result,
                    ExecutorService workerThreads) {
        tracer.newTracer("read");
        final TimeSeriesType<T> type = TypeMap.getTimeSeriesType(tsType);
        final TimeSeriesMetadata.TimeBucket granularity = (bucket == null ? type.getBucketConfig() : bucket);
        final List<String> rows = type.getRows(timeBucket);
        final List<Future<Object>> queries = new ArrayList<Future<Object>>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            final String rowKey = rows.get(index);
            queries.add(workerThreads.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    // time series are always in the local keyspace
                    DbClientContext context = getLocalContext();
                    
                    StringBuilder queryString = new StringBuilder();
                    queryString.append("select key, unixTimestampOf(column1), value from \"").append(type.getCf().getName()).append("\" where key=?");
                    
                    DateTime[] timeRangeValues = type.getColumnRange(timeBucket, granularity);
                    if (timeRangeValues.length > 0) {
                        queryString.append(" and column1>=minTimeuuid(?) and column1<=maxTimeuuid(?)");
                    }
                    
                    _log.info("Query String: {}", queryString);
                    
                    PreparedStatement queryStatement = context.getPreparedStatement(queryString.toString());
                    queryStatement.setConsistencyLevel(ConsistencyLevel.ONE);
                    
                    BoundStatement bindStatement = queryStatement.bind(rowKey);
                    bindStatement.setFetchSize(DEFAULT_TS_PAGE_SIZE);
                    
                    if (timeRangeValues.length > 0) {
                        bindStatement.setTimestamp(1, timeRangeValues[0].toDate());
                        bindStatement.setTimestamp(2, timeRangeValues[1].toDate());
                    }
                    
                    ResultSet results = context.getSession().execute(bindStatement);
                    for (com.datastax.driver.core.Row row : results) {
                        result.data(type.getSerializer().deserialize(row.getBytes(2).array()),
                                row.getLong(1));
                    }
                    
                    return null;
                }
            }));
        }
        for (int i = 0; i < queries.size(); i++) {
            Future<Object> objectFuture = queries.get(i);
            try {
                objectFuture.get();
            } catch (Exception t) {
                result.error(t);
            }
        }
        result.done();
    }

    @Override
    public TimeSeriesMetadata queryTimeSeriesMetadata(Class<? extends TimeSeries> tsType) {
        tracer.newTracer("read");
        return TypeMap.getTimeSeriesType(tsType);
    }

    /**
     * Convenience helper that queries for multiple rows for collection of row
     * keys for a single column
     * 
     * @param context dbClientContext
     * @param ids row keys.
     * @param column column field for the column to query
     * @return matching rows
     * @throws DatabaseException
     */
    protected Map<String, List<CompositeColumnName>> queryRowsWithAColumn(DbClientContext context, Collection<URI> ids, String tableName, ColumnField column) {
        PreparedStatement queryAllColumnsPreparedStatement = context.getPreparedStatement( 
                String.format("Select * from \"%s\" where column1=? and key in ? ALLOW FILTERING", tableName));
        
        ResultSet resultSet = context.getSession()
                .execute(queryAllColumnsPreparedStatement.bind(column.getName(), uriList2StringList(ids)));
        
        return toColumnMap(resultSet);
    }

    /**
     * Convernts from List<URI> to List<String>.
     *
     * todo: could optimize this by wrapping and converting URI to String on the fly
     *
     * @param uriList
     * @return
     */
    private Collection<String> convertUriCollection(Collection<URI> uriList) {
        List<String> idList = new ArrayList<String>();
        Iterator<URI> it = uriList.iterator();
        while (it.hasNext()) {
            idList.add(it.next().toString());
        }
        if (idList.size() > DEFAULT_PAGE_SIZE) {
            int MAX_STACK_SIZE = 10; // Maximum stack we'll search in our thread.
            int MAX_STACK_PRINT = 2; // Maximum number of frames we'll print.  (really the first frame is the most important)
            
            _log.warn("Unbounded database query, request size is over allowed limit({}), " +
                    "please use corresponding iterative API.", DEFAULT_PAGE_SIZE);
            StackTraceElement[] elements = new Throwable().getStackTrace();
            int i=0, j=0;
            while (i < MAX_STACK_SIZE && j < MAX_STACK_PRINT) {
                // Print the stacktrace of this inefficiency.  Avoid printing anything in DbClientImpl since that's a given.
                if (i < elements.length && elements[i] != null && elements[i].getMethodName() != null && !elements[i].getClassName().contains("DbClientImpl")) {
                    _log.warn(String.format("Stack position %d: %s.%s(), %s:%s", i, 
                            elements[i].getClassName().substring(elements[i].getClassName().lastIndexOf(".") + 1), 
                            elements[i].getMethodName(), 
                            elements[i].getFileName(), 
                            elements[i].getLineNumber()));
                    j++;
                }
                i++;
            }
        }

        return idList;
    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, ResourceOperationTypeEnum type) {
        tracer.newTracer("write");
        Operation op = new Operation();
        op.setResourceType(type);
        return createTaskOpStatus(clazz, id, opId, op);
    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, ResourceOperationTypeEnum type, String associatedResources) {
        tracer.newTracer("write");
        Operation op = new Operation();
        op.setResourceType(type);
        op.setAssociatedResourcesField(associatedResources);
        return createTaskOpStatus(clazz, id, opId, op);
    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, Operation newOperation) {
        tracer.newTracer("write");
        if (newOperation == null) {
            throw new IllegalArgumentException("missing required parameter: Operation");
        }
        try {
            if (newOperation.getDescription() == null) {
                throw new IllegalArgumentException("missing required parameter: description");
            }
            DataObject doobj = clazz.newInstance();
            doobj.setId(id);
            doobj.setOpStatus(new OpStatusMap());
            Operation op = new Operation();
            op.setStartTime(Calendar.getInstance());
            String message = newOperation.getMessage();
            String description = newOperation.getDescription();
            String name = newOperation.getName();
            if (newOperation.getStatus().equals(Operation.Status.ready.name())) {
                op.ready();
                op.setEndTime(Calendar.getInstance());
            }
            if (message != null) {
                op.setMessage(message);
            }
            if (description != null) {
                op.setDescription(description);
            }
            if (name != null) {
                op.setName(name);
            }
            List<String> associatedResources = newOperation.getAssociatedResourcesField();
            if (associatedResources != null) {
                String associatedResourcesStr = Joiner.on(',').join(associatedResources);
                op.setAssociatedResourcesField(associatedResourcesStr);
            }
            doobj.getOpStatus().put(opId, op);
            persistObject(doobj);
            newOperation.addTask(id, op.getTask(id));
            return op;
        } catch (InstantiationException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Deprecated
    public Operation updateTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, Operation updateOperation) {
        tracer.newTracer("write");
        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    private Operation updateTaskStatus(Class<? extends DataObject> clazz, URI id,
            String opId, Operation updateOperation) {
        return updateTaskStatus(clazz, id, opId, updateOperation, false);
    }

    private Operation updateTaskStatus(Class<? extends DataObject> clazz, URI id,
            String opId, Operation updateOperation, boolean resetStartTime) {
        List<URI> ids = new ArrayList<URI>(Arrays.asList(id));
        List<? extends DataObject> objs = queryObjectField(clazz, "status", ids);
        if (objs == null || objs.isEmpty()) {
            // When "status" map is null (empty) we do not get object when query by the map field name in CF.
            // Try to get object by id.
            objs = queryObject(clazz, ids);
            if (objs == null || objs.isEmpty()) {
                _log.error("Cannot find object {} in {}", id, clazz.getSimpleName());
                return null;
            }
            _log.info("Object {} has empty status map", id);
        }

        DataObject doobj = objs.get(0);
        _log.info(String.format("Updating operation %s for object %s with status %s", opId, doobj.getId(), updateOperation.getStatus()));
        Operation op = doobj.getOpStatus().updateTaskStatus(opId, updateOperation, resetStartTime);
        if (op == null) {
            // OpStatusMap does not have entry for a given opId. The entry already expired based on ttl.
            // Recreate the entry for this opId from the task object and proceed with update
            _log.info("Operation map for object {} does not have entry for operation id {}", doobj.getId(), opId);
            Task task = TaskUtils.findTaskForRequestId(this, doobj.getId(), opId);
            if (task != null) {
                _log.info(String.format("Creating operation %s for object %s from task instance %s", opId, doobj.getId(), task.getId()));
                // Create operation instance for the task
                Operation operation = TaskUtils.createOperation(task);
                doobj.getOpStatus().createTaskStatus(opId, operation);
                op = doobj.getOpStatus().updateTaskStatus(opId, updateOperation, false);
                if (op == null) {
                    _log.error(String.format("Failed to update operation %s for object %s ", opId, doobj.getId()));
                    return null;
                }
            } else {
                _log.warn(String.format("Task for operation %s and object %s does not exist.", opId, doobj.getId()));
                return null;
            }
        }
        persistObject(doobj);
        return op;
    }

    @Override
    public Operation ready(Class<? extends DataObject> clazz, URI id, String opId) {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.ready();
        updateOperation.setProgress(COMPLETED_PROGRESS);
        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    @Override
    public Operation ready(Class<? extends DataObject> clazz, URI id, String opId, String message) {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.ready(message);
        updateOperation.setProgress(COMPLETED_PROGRESS);
        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    @Override
    public Operation suspended_no_error(Class<? extends DataObject> clazz, URI id,
            String opId, String message) throws DatabaseException {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.suspendedNoError(message);
        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    @Override
    public Operation suspended_no_error(Class<? extends DataObject> clazz, URI id,
            String opId) throws DatabaseException {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.suspendedNoError();
        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    @Override
    public Operation suspended_error(Class<? extends DataObject> clazz, URI id,
            String opId, ServiceCoded serviceCoded) throws DatabaseException {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.suspendedError(serviceCoded);
        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    @Override
    public Operation pending(Class<? extends DataObject> clazz, URI id, String opId, String message) throws DatabaseException {
        tracer.newTracer("write");
        return pending(clazz, id, opId, message, false);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.DbClient#pending(java.lang.Class, java.net.URI, java.lang.String, java.lang.String, boolean)
     */
    @Override
    public Operation pending(Class<? extends DataObject> clazz, URI id, String opId, String message, boolean resetStartTime) {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.setMessage(message);
        return updateTaskStatus(clazz, id, opId, updateOperation, true);
    }

    /**
     * Convenience method for setting operation status to error for given object
     *
     * @param clazz
     * @param id
     * @param opId
     * @throws DatabaseException TODO
     */
    @Override
    public Operation error(Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded) {
        tracer.newTracer("write");
        Operation updateOperation = new Operation();
        updateOperation.error(serviceCoded);

        return updateTaskStatus(clazz, id, opId, updateOperation);
    }

    /**
     * Get DB schema version
     */
    @Override
    public String getSchemaVersion() {
        return _dbVersionInfo.getSchemaVersion();
    }

    @Deprecated
    @Override
    public String getLocalShortVdcId() {
        return VdcUtil.getLocalShortVdcId();
    }

    @Deprecated
    @Override
    public URI getVdcUrn(String shortVdcId) {
        return VdcUtil.getVdcUrn(shortVdcId);
    }

    @Deprecated
    @Override
    public void invalidateVdcUrnCache() {
        VdcUtil.invalidateVdcUrnCache();
    }

    @Override
    public boolean hasUsefulData() {
        Collection<DataObjectType> doTypes = TypeMap.getAllDoTypes();

        for (DataObjectType doType : doTypes) {
            Class clazz = doType.getDataObjectClass();

            if (hasDataInCF(clazz)) {
                return true;
            }
        }

        return false;
    }

    private <T extends DataObject> boolean hasDataInCF(Class<T> clazz) {
        if (excludeClasses.contains(clazz)) {
            return false; // ignore the data in those CFs
        }

        // true: query only active object ids, for below reason:
        // add site should succeed just when remove the data in site2.
        List<URI> ids = queryByType(clazz, true, null, 2);

        if (clazz.equals(TenantOrg.class)) {
            if (ids.size() > 1) {
                // at least one non-root tenant exist
                return true;
            }

            return false;
        }

        if (!ids.isEmpty()) {
            _log.info("The class {} has data e.g. id={}", clazz.getSimpleName(), ids.get(0));
            return true;
        }

        return false;
    }

    @Override
    public boolean checkGeoCompatible(String expectVersion) {
        _geoVersion = VdcUtil.getMinimalVdcVersion();
        return VdcUtil.VdcVersionComparator.compare(_geoVersion, expectVersion) >= 0;
    }

    private void serializeTasks(DataObject dataObject, RowMutator mutator, List<URI> objectsToCleanup) {
        OpStatusMap statusMap = dataObject.getOpStatus();
        if (statusMap == null || statusMap.getChangedKeySet() == null || statusMap.getChangedKeySet().isEmpty()) {
            return;
        }

        Set<String> addedSet = statusMap.getChangedKeySet();
        if (addedSet != null) {
            DataObjectType taskDoType = TypeMap.getDoType(Task.class);

            Iterator<String> it = statusMap.getChangedKeySet().iterator();
            while (it.hasNext()) {
                String requestId = it.next();
                Operation operation = statusMap.get(requestId);

                Task task = TaskUtils.findTaskForRequestId(this, dataObject.getId(), requestId);

                if (task == null) {
                    // Task doesn't currently exist for this id, so create it
                    task = new Task();

                    task.setId(URIUtil.createId(Task.class));
                    task.setRequestId(requestId);
                    task.setInactive(false);
                    task.setServiceCode(operation.getServiceCode());
                    task.setLabel(operation.getName());
                    task.setStatus(operation.getStatus());
                    task.setDescription(operation.getDescription());

                    Integer progress = operation.getProgress();
                    task.setProgress(progress != null ? progress : 0);

                    task.setMessage(operation.getMessage());
                    task.setAssociatedResources(operation.rawAssociatedResources());
                    task.setCreationTime(Calendar.getInstance());
                    task.setInactive(false);
                    task.setStartTime(operation.getStartTime());
                    task.setEndTime(getEndTime(operation));

                    // Often dummy objects are used that just contain an ID, for some things we need access to the entire object
                    DataObject loadedObject = dataObject;
                    if (StringUtils.isBlank(dataObject.getLabel())) {
                        loadedObject = this.queryObject(URIUtil.getModelClass(dataObject.getId()), dataObject.getId());
                    }

                    if (loadedObject == null) {
                        throw new RuntimeException("Task created on a resource which doesn't exist " + dataObject.getId());
                    }

                    task.setResource(new NamedURI(loadedObject.getId(), loadedObject.getLabel()));

                    URI tenantId = getTenantURI(loadedObject);
                    if (tenantId == null) {
                        task.setTenant(TenantOrg.SYSTEM_TENANT);
                    } else {
                        task.setTenant(tenantId);
                    }

                    _log.info("Created task {}, {}", task.getId() + " (" + task.getRequestId() + ")", task.getLabel());
                } else {
                    // Task exists so update it
                    task.setServiceCode(operation.getServiceCode());
                    task.setStatus(operation.getStatus());
                    task.setMessage(operation.getMessage());

                    // Some code isn't updating progress to 100 when completed, so fix this here
                    if (Objects.equal(task.getStatus(), "pending") || Objects.equal(task.getStatus(), "suspended_no_error") ||
                            Objects.equal(task.getStatus(), "suspended_error")) {
                        task.setProgress(operation.getProgress());
                    } else {
                        task.setProgress(COMPLETED_PROGRESS);
                    }
                    task.setStartTime(operation.getStartTime());
                    task.setEndTime(getEndTime(operation));
                    task.setAssociatedResources(operation.rawAssociatedResources());

                    if (!Objects.equal(task.getStatus(), "pending")) {
                        _log.info("Completed task {}, {}", task.getId() + " (" + task.getRequestId() + ")", task.getStatus());
                    }
                }

                if (taskDoType.serialize(mutator, task)) {
                    objectsToCleanup.add(task.getId());
                }

                operation.addTask(dataObject.getId(), task);
            }
        }
    }

    /**
     * Even if we have NTP, there could probably be time difference among nodes in cluster,
     * make sure endTime is not earlier than startTime.
     */
    private static Calendar getEndTime(Operation operation) {
        if (operation.getStartTime() == null || operation.getEndTime() == null) {
            return operation.getEndTime();
        }
        return operation.getEndTime().before(operation.getStartTime()) ? (Calendar) operation.getStartTime().clone() : operation
                .getEndTime();
    }

    private URI getTenantURI(DataObject dataObject) {
        if (dataObject instanceof ProjectResource) {
            return ((ProjectResource) dataObject).getTenant().getURI();
        } else if (dataObject instanceof ProjectResourceSnapshot) {
            NamedURI projectURI = ((ProjectResourceSnapshot) dataObject).getProject();
            Project project = queryObject(Project.class, projectURI);
            return project.getTenantOrg().getURI();
        } else if (dataObject instanceof TenantResource) {
            return ((TenantResource) dataObject).getTenant();
        } else if (dataObject instanceof HostInterface) {
            URI hostURI = ((HostInterface) dataObject).getHost();
            Host host = queryObject(Host.class, hostURI);
            return host == null ? null : host.getTenant();
        }

        return null;
    }

    private <T extends DataObject> void checkGeoVersionForMutation(final T object) {
        DataObjectType doType = TypeMap.getDoType(object.getClass());

        if (!KeyspaceUtil.isGlobal(object.getClass())) {
            return;
        }
        for (ColumnField columnField : doType.getColumnFields()) {
            if (object.isChanged(columnField.getName())
                    && !isChangeAllowedOnField(object.getClass(), columnField.getPropertyDescriptor())) {
                String clazzName = object.getClass().getName();
                String fieldName = columnField.getPropertyDescriptor().getName();
                String geoVersion = this.getGeoVersion();
                String expectVersion = this.getMaxGeoAllowedVersion(object.getClass(), columnField.getPropertyDescriptor());
                _log.warn("Error while persisting {0}: {1}, Geo version {2} is not compatible with expect version {3}", new String[] {
                        clazzName, fieldName, geoVersion, expectVersion });
                throw FatalDatabaseException.fatals.disallowedGeoUpdate(clazzName, fieldName, geoVersion, expectVersion);
            }
        }
    }

    private boolean isChangeAllowedOnField(final Class<? extends DataObject> clazz, final PropertyDescriptor property) {
        if (!hasGeoVersionAnnotation(clazz, property)) {
            return true;
        }
        String maxVersion = this.getMaxGeoAllowedVersion(clazz, property);
        String geoVersion = this.getGeoVersion();
        return VdcUtil.VdcVersionComparator.compare(geoVersion, maxVersion) >= 0;
    }

    private boolean hasGeoVersionAnnotation(Class<? extends DataObject> clazz, PropertyDescriptor property) {
        return clazz.getAnnotation(AllowedGeoVersion.class) != null
                || property.getReadMethod().getAnnotation(AllowedGeoVersion.class) != null;
    }

    private String getMaxGeoAllowedVersion(final Class<? extends DataObject> clazz, final PropertyDescriptor property) {
        if (clazz.getAnnotation(AllowedGeoVersion.class) == null) {
            return property.getReadMethod().getAnnotation(AllowedGeoVersion.class).version();
        }
        if (property.getReadMethod().getAnnotation(AllowedGeoVersion.class) == null) {
            return clazz.getAnnotation(AllowedGeoVersion.class).version();
        }

        String clazzVersion = clazz.getAnnotation(AllowedGeoVersion.class).version();
        String fieldVersion = property.getReadMethod().getAnnotation(AllowedGeoVersion.class).version();
        return VdcUtil.VdcVersionComparator.compare(fieldVersion, clazzVersion) > 0 ? fieldVersion : clazzVersion;
    }
    
    /**
     * Convenience helper that queries for multiple rows for collection of row
     * keys
     * 
     * @param context local or geo dbclientContext to query rows against
     * @param ids row keys
     * @param tableName column family/table name
     * @return matching rows
     * @throws DatabaseException
     */
    public Map<String, List<CompositeColumnName>> queryRowsWithAllColumns(DbClientContext context, Collection<URI> ids, String tableName) {
        PreparedStatement queryAllColumnsPreparedStatement = context.getPreparedStatement(String.format("Select * from \"%s\" where key in ?", tableName));
        
        ResultSet resultSet = context.getSession()
                .execute(queryAllColumnsPreparedStatement.bind(uriList2StringList(ids)));
        
        return toColumnMap(resultSet);
    }

    public Map<String, List<CompositeColumnName>> toColumnMap(ResultSet resultSet) {
        Map<String, List<CompositeColumnName>> result = new HashMap<String, List<CompositeColumnName>>();
        List<CompositeColumnName> rows = null;
        String lastKey = null;
        
        for (com.datastax.driver.core.Row row : resultSet) {
            String currentKey = row.getString(0);
            
            if (lastKey == null || (!lastKey.equals(currentKey))) {
                rows = new ArrayList<CompositeColumnName>();
                result.put(currentKey, rows);
                lastKey = currentKey;
            }
                
            result.get(currentKey).add(new CompositeColumnName(
                            currentKey,
                            row.getString(1),
                            row.getString(2),
                            row.getString(3),
                            row.getUUID(4),
                            row.getBytes(5)));
        }
        
        if (lastKey != null) {
            result.put(lastKey, rows);
        }
        return result;
    }
    
    private Map<String, List<CompositeColumnName>> queryRowWithAllColumns(DbClientContext context, URI id, String tableName) {
        Map<String, List<CompositeColumnName>> result = queryRowsWithAllColumns(context, Arrays.asList(id), tableName);
        return result;
    }
    
    private List<String> uriList2StringList(Collection<URI> ids) {
        Set<String> result = new HashSet<String>();
        for (URI uri : ids) {
            result.add(uri.toString());
        }
        return new ArrayList<String>(result);
    }
    
    public void internalRemoveObjects(DataObject... object) {
        Map<Class<? extends DataObject>, List<DataObject>> typeObjMap = new HashMap<Class<? extends DataObject>, List<DataObject>>();
        for (DataObject obj : object) {
            List<DataObject> objTypeList = typeObjMap.get(obj.getClass());
            if (objTypeList == null) {
                objTypeList = new ArrayList<>();
                typeObjMap.put(obj.getClass(), objTypeList);
            }
            objTypeList.add(obj);
        }
        for (Entry<Class<? extends DataObject>, List<DataObject>> entry : typeObjMap.entrySet()) {
            List<DataObject> dbObjList = entry.getValue();
            removeObject(entry.getKey(), dbObjList.toArray(new DataObject[dbObjList.size()]));
        }
    }

    class KeyspaceTracerFactoryImpl {
        private ConcurrentHashMap<String, AtomicLong> counterMap = new ConcurrentHashMap<String, AtomicLong>();
        private Map<String, AtomicLong> sortedMap;
        private ScheduledExecutorService executor = new NamedScheduledThreadPoolExecutor("DbClientPerformance", 1);
        private AtomicLong total = new AtomicLong(0);
        public KeyspaceTracerFactoryImpl() {
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    _log.info("Total dbclient calls: {} for last {} seconds. Top {} caller are: ", total, logInterval, logCount);
                    sortedMap = desendSortByValue(counterMap);
                    int i=1;
                    for (Entry<String, AtomicLong> entry : sortedMap.entrySet()) {
                        _log.info("{} -> {}", entry.getKey(), entry.getValue().get());
                        if (i >= logCount) break;
                        i++;
                    }
                    counterMap.clear();
                    total.set(0);
                }
            }, logInterval, logInterval, TimeUnit.SECONDS);
        }

        private Map<String, AtomicLong> desendSortByValue(Map<String, AtomicLong> map) {
            List<Map.Entry<String, AtomicLong>> entryList = new LinkedList<Entry<String, AtomicLong>>(map.entrySet());
            Collections.sort(entryList, new Comparator<Map.Entry<String, AtomicLong>>() {
                @Override
                public int compare(Map.Entry<String, AtomicLong> o1, Map.Entry<String, AtomicLong> o2) {
                    return (int) ((o2.getValue().get()) - (o1.getValue().get()));
                }
            });
            Map<String, AtomicLong> sortMap = new LinkedHashMap<>();
            for (Map.Entry<String, AtomicLong> entry : entryList) {
                sortMap.put(entry.getKey(), entry.getValue());
            }
            return sortMap;
        }

        public void newTracer(String type) {
            StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[3];
            if (stackTraceElement.getClassName().startsWith("com.emc.storageos.db.client")) {
                return;
            }
            total.getAndIncrement();
            String key = String.format("%s.%s:%s:%s", stackTraceElement.getClassName(), stackTraceElement.getMethodName(), stackTraceElement.getLineNumber(),type);
            counterMap.putIfAbsent(key, new AtomicLong(0));
            counterMap.get(key).getAndIncrement();
        }
    }
}
