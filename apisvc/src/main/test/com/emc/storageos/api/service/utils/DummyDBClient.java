/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.AuditLog;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer.DataPoint;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.netflix.astyanax.clock.MicrosecondsClock;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Implemation of StatRetriever to retrieve stats locally instead of getting
 * from Cassandra database.
 * 
 * @author rvobugar
 * 
 */
public class DummyDBClient implements DbClient {

    HashMap<URI, DataObject> _idToObjectMap;
    final private Logger _logger = LoggerFactory.getLogger(DummyDBClient.class);

    @Override
    public DataObject queryObject(URI id) throws DatabaseException {
        Class<? extends DataObject> clazz = URIUtil.getModelClass(id);
        return queryObject(clazz, id);
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI id) throws DatabaseException {
        checkStarted();
        return (T) _idToObjectMap.get(id);
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, NamedURI id)
            throws DatabaseException {
        return queryObject(clazz, id.getURI());
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> id)
            throws DatabaseException {
        checkStarted();
        List<T> objectList = new ArrayList<T>();
        for (URI idEntry : id) {
            Object entry = _idToObjectMap.get(idEntry);
            if (null != entry) {
                objectList.add((T) entry);
            }
        }
        return objectList;
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, URI... id)
            throws DatabaseException {
        checkStarted();
        List<T> objectList = new ArrayList<T>();
        for (URI idEntry : id) {
            Object entry = _idToObjectMap.get(idEntry);
            if (null != entry) {
                objectList.add((T) entry);
            }
        }
        return objectList;
    }

    @Override
    public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName,
            Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectField(Class<T> clazz, String fieldName,
            Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<T> queryObjectFields(Class<T> clazz, Collection<String> fieldNames,
            Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectFields(Class<T> clazz, Collection<String> fieldNames,
            Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void aggregateObjectField(Class<T> clazz, Iterator<URI> ids, DbAggregatorItf aggregator) {
        // do nothing
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> id)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly) throws DatabaseException {
        checkStarted();
        List<URI> idList = new ArrayList<URI>();
        for (Entry<URI, DataObject> entry : _idToObjectMap.entrySet()) {
            if (clazz.isInstance(entry.getValue()) &&
                    (!activeOnly || !entry.getValue().getInactive())) {
                idList.add(entry.getKey());
            }
        }
        return idList;
    }

    @Override
    public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly, URI startID, int count)
            throws DatabaseException {
        return null;
    }

    @Override
    public <T extends DataObject> void queryInactiveObjects(Class<T> clazz, final long timeBefore, QueryResultList<URI> result)
            throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    public List<URI> queryByConstraint(Constraint constraint)
            throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void createObject(T object) throws DatabaseException {
        persistObject(object);
    }

    @Override
    public <T extends DataObject> void createObject(Collection<T> object) throws DatabaseException {
        persistObject(object);
    }

    @Override
    public <T extends DataObject> void createObject(T... object) throws DatabaseException {
        persistObject(object);
    }

    @Override
    public <T extends DataObject> void persistObject(T object) throws DatabaseException {
        checkStarted();
        _idToObjectMap.put(object.getId(), object);

    }

    @Override
    public <T extends DataObject> void persistObject(Collection<T> object) throws DatabaseException {
        checkStarted();
        for (DataObject entry : object) {
            persistObject(entry);
        }
    }

    @Override
    public <T extends DataObject> void persistObject(T... object) throws DatabaseException {
        checkStarted();
        for (DataObject entry : object) {
            persistObject(entry);
        }
    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(T object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(Collection<T> object)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(T... object)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateObject(T object) {
        // TODO Auto-generated method stub
    }

    @Override
    public <T extends DataObject> void updateObject(Collection<T> objects) {
        // TODO Auto-generated method stub
    }

    @Override
    public <T extends DataObject> void updateObject(T... object) {
        // TODO Auto-generated method stub
    }

    @Deprecated
    public void setStatus(Class<? extends DataObject> clazz,
            URI id, String opId, String status)
            throws DatabaseException {
        checkStarted();
    }

    @Deprecated
    public void setStatus(Class<? extends DataObject> clazz,
            URI id, String opId, String status, String message)
            throws DatabaseException {
        checkStarted();
    }

    @Override
    public void markForDeletion(DataObject object) throws DatabaseException {
        checkStarted();
        object.setInactive(true);
    }

    @Override
    public void markForDeletion(Collection<? extends DataObject> object) throws DatabaseException {
        checkStarted();
        for (DataObject o : object) {
            o.setInactive(true);
        }
    }

    @Override
    public <T extends DataObject> void markForDeletion(T... object) throws DatabaseException {
        checkStarted();
        for (DataObject o : object) {
            o.setInactive(true);
        }
    }

    @Override
    public void removeObject(DataObject... object) throws DatabaseException {
        checkStarted();
        for (DataObject oneObject : object) {
            _idToObjectMap.remove(oneObject.getId());
        }
    }

    @Override
    public <T extends DataPoint> String insertTimeSeries(Class<? extends TimeSeries> tsType,
            T... data) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataPoint> String insertTimeSeries(Class<? extends TimeSeries> tsType,
            DateTime time, T data) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType,
            DateTime timeBucket, TimeSeriesQueryResult<T> callback, ExecutorService workerThreads)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType,
            DateTime timeBucket, TimeBucket bucket, TimeSeriesQueryResult<T> callback,
            ExecutorService workerThreads) throws DatabaseException {
        if (timeBucket != null) {

            MicrosecondsClock clock = new MicrosecondsClock();
            UUID uuid = TimeUUIDUtils.getTimeUUID(clock);

            // For timeBucket 2012-01-01T00:00 we retirn 10 stats
            // For timeBucket 2012-01-02T00:00 we return I/O exception
            // For timeBucket 2012-01-03T00:00 we set value data.error
            // For timeBucket 2012-01-04T00:00 we return 10 events
            if (timeBucket.toString().contains("2012-01-01T00:00")) {

                try {
                    // TODO Auto-generated method stub

                    for (int i = 0; i < 10; i++) {
                        Stat st = new Stat();
                        st.setProject(new URI("http://project" + i));
                        st.setTenant(new URI("http://t." + i));
                        st.setUser(new URI("http://u." + i));
                        st.setVirtualPool(new URI("http://vpool.gold" + i));
                        callback.data((T) st,
                                TimeUUIDUtils.getTimeFromUUID(uuid));
                    }
                } catch (URISyntaxException e) {
                    _logger.error(e.getMessage(), e);
                }

                callback.done();

            } else if (timeBucket.toString().contains("2012-01-02T00:00")) {
                throw DatabaseException.retryables.dummyClientFailed();
            } else if (timeBucket.toString().contains("2012-01-03T00:00")) {
                callback.error(null);
            } else if (timeBucket.toString().contains("2012-01-04T00:00")) {
                try {
                    // TODO Auto-generated method stub
                    for (int i = 0; i < 10; i++) {
                        Event evt = new Event();
                        evt.setProjectId(new URI("http://project" + i));
                        evt.setEventId(String.valueOf(i));
                        evt.setTenantId(new URI("http://t." + i));
                        evt.setUserId(new URI("http://u." + i));
                        evt.setVirtualPool(new URI("http://vpool.gold" + i));
                        callback.data((T) evt,
                                TimeUUIDUtils.getTimeFromUUID(uuid));
                    }
                } catch (URISyntaxException e) {
                    _logger.error(e.getMessage(), e);
                }

                callback.done();
            } else if (timeBucket.toString().contains("2012-01-05T00:00")) {
                try {
                    throw new MarshallingExcetion("marshalling Exception", null);
                } catch (MarshallingExcetion e) {
                    _logger.error(e.getMessage(), e);
                }
            } else if (timeBucket.toString().contains("2012-01-06T00:00")) {
                callback.error(null);
            } else if (timeBucket.toString().contains("2012-01-07T00:00")) {
                try {
                    // TODO Auto-generated method stub
                    for (int i = 0; i < 10; i++) {
                        AuditLog log = new AuditLog();
                        log.setProductId("productId." + i);
                        log.setTenantId(new URI("http://tenant." + i));
                        log.setUserId(new URI("http://user." + i));
                        log.setServiceType("serviceType" + i);
                        log.setAuditType("auditType" + i);
                        log.setDescription("description" + i);
                        callback.data((T) log,
                                TimeUUIDUtils.getTimeFromUUID(uuid));
                    }
                } catch (URISyntaxException e) {
                    _logger.error(e.getMessage(), e);
                }

                callback.done();
            }
            else if (timeBucket.toString().contains("2012-01-08T00:00")) {
                try {
                    throw new MarshallingExcetion("marshalling Exception", null);
                } catch (MarshallingExcetion e) {
                    _logger.error(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public TimeSeriesMetadata queryTimeSeriesMetadata(Class<? extends TimeSeries> tsType) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void start() {
        _idToObjectMap = new HashMap<URI, DataObject>();

    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

    }

    @Override
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result)
            throws DatabaseException {
        checkStarted();
    }

    @Override
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result, URI startId, int count)
            throws DatabaseException {
    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId,
            Operation newOperation) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, ResourceOperationTypeEnum type)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz,
            URI id, String opId, ResourceOperationTypeEnum type,
            String associatedResources) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation updateTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId,
            Operation updateOperation) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation ready(Class<? extends DataObject> clazz, URI id, String opId) throws DatabaseException {
        checkStarted();
        return null;
    }

    @Override
    public Operation ready(Class<? extends DataObject> clazz, URI id, String opId, String message) throws DatabaseException {
        return ready(clazz, id, opId);
    }

    @Override
    public Operation error(Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded) throws DatabaseException {
        checkStarted();
        return null;
    }

    @Override
    public Operation pending(Class<? extends DataObject> clazz, URI id, String opId, String message) throws DatabaseException {
        checkStarted();
        return null;
    }

    @Override
    public Integer countObjects(Class<? extends DataObject> type, String columnField, URI uri)
            throws DatabaseException {
        return 0;
    }

    private void checkStarted() {
        if (null == _idToObjectMap) {
            throw DatabaseException.retryables.dummyClientNotStarted();
        }
    }

    @Override
    public String getSchemaVersion() {
        return "1";
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz,
            Collection<URI> ids, boolean activeOnly) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(
            Class<T> clazz, Collection<URI> ids, boolean activeOnly)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    public String getLocalShortVdcId() {
        return "vdc1";
    }

    @Override
    public URI getVdcUrn(String shortVdcId) {
        return null;
    }

    @Override
    public void invalidateVdcUrnCache() {
    }

    @Override
    public boolean checkGeoCompatible(String expectVersion) {
        return true;
    }

    @Override
    public boolean hasUsefulData() {
        // TODO Auto-generated method stub
        return false;
    }
}
