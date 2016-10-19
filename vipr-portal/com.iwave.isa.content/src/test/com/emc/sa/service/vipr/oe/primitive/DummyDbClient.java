/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.sa.service.vipr.oe.primitive;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer.DataPoint;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * .
 * 
 * 
 */
public class DummyDbClient implements DbClient {

    private HashMap<URI, DataObject> _idToObjectMap;
    final private Logger _logger = LoggerFactory.getLogger(DummyDbClient.class);

    @Override
    public DataObject queryObject(final URI id) throws DatabaseException {
        final Class<? extends DataObject> clazz = URIUtil.getModelClass(id);
        return queryObject(clazz, id);
    }

    @Override
    public <T extends DataObject> T queryObject(final Class<T> clazz,
            final URI id) throws DatabaseException {
        checkStarted();
        return (T) _idToObjectMap.get(id);
    }

    @Override
    public <T extends DataObject> T queryObject(final Class<T> clazz,
            final NamedURI id) throws DatabaseException {
        return queryObject(clazz, id.getURI());
    }

    @Override
    public <T extends DataObject> List<T> queryObject(final Class<T> clazz,
            final Collection<URI> id) throws DatabaseException {
        checkStarted();
        final List<T> objectList = new ArrayList<T>();
        for (final URI idEntry : id) {
            final Object entry = _idToObjectMap.get(idEntry);
            if (null != entry) {
                objectList.add((T) entry);
            }
        }
        return objectList;
    }

    @Override
    public <T extends DataObject> List<T> queryObject(final Class<T> clazz,
            final URI... id) throws DatabaseException {
        checkStarted();
        final List<T> objectList = new ArrayList<T>();
        for (final URI idEntry : id) {
            final Object entry = _idToObjectMap.get(idEntry);
            if (null != entry) {
                objectList.add((T) entry);
            }
        }
        return objectList;
    }

    @Override
    public <T extends DataObject> List<T> queryObjectField(
            final Class<T> clazz, final String fieldName,
            final Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectField(
            final Class<T> clazz, final String fieldName,
            final Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<T> queryObjectFields(
            final Class<T> clazz, final Collection<String> fieldNames,
            final Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectFields(
            final Class<T> clazz, final Collection<String> fieldNames,
            final Collection<URI> ids) throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void aggregateObjectField(
            final Class<T> clazz, final Iterator<URI> ids,
            final DbAggregatorItf aggregator) {
        // do nothing
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(
            final Class<T> clazz, final Collection<URI> id)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<URI> queryByType(final Class<T> clazz,
            final boolean activeOnly) throws DatabaseException {
        checkStarted();
        final List<URI> idList = new ArrayList<URI>();
        for (final Entry<URI, DataObject> entry : _idToObjectMap.entrySet()) {
            if (clazz.isInstance(entry.getValue())
                    && (!activeOnly || !entry.getValue().getInactive())) {
                idList.add(entry.getKey());
            }
        }
        return idList;
    }

    @Override
    public <T extends DataObject> List<URI> queryByType(final Class<T> clazz,
            final boolean activeOnly, final URI startID, final int count)
            throws DatabaseException {
        return null;
    }

    @Override
    public <T extends DataObject> void queryInactiveObjects(
            final Class<T> clazz, final long timeBefore,
            final QueryResultList<URI> result) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<URI> queryByConstraint(final Constraint constraint)
            throws DatabaseException {
        checkStarted();
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void createObject(final T object)
            throws DatabaseException {
        persistObject(object);
    }

    @Override
    public <T extends DataObject> void createObject(final Collection<T> object)
            throws DatabaseException {
        persistObject(object);
    }

    @Override
    public <T extends DataObject> void createObject(final T... object)
            throws DatabaseException {
        persistObject(object);
    }

    @Override
    public <T extends DataObject> void persistObject(final T object)
            throws DatabaseException {
        checkStarted();
        _idToObjectMap.put(object.getId(), object);

    }

    @Override
    public <T extends DataObject> void persistObject(final Collection<T> object)
            throws DatabaseException {
        checkStarted();
        for (final DataObject entry : object) {
            persistObject(entry);
        }
    }

    @Override
    public <T extends DataObject> void persistObject(final T... object)
            throws DatabaseException {
        checkStarted();
        for (final DataObject entry : object) {
            persistObject(entry);
        }
    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(final T object)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(
            final Collection<T> object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(final T... object)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateObject(final T object) {
        // TODO Auto-generated method stub
    }

    @Override
    public <T extends DataObject> void updateObject(final Collection<T> objects) {
        // TODO Auto-generated method stub
    }

    @Override
    public <T extends DataObject> void updateObject(final T... object) {
        // TODO Auto-generated method stub
    }

    @Override
    @Deprecated
    public void setStatus(final Class<? extends DataObject> clazz,
            final URI id, final String opId, final String status)
            throws DatabaseException {
        checkStarted();
    }

    @Override
    @Deprecated
    public void setStatus(final Class<? extends DataObject> clazz,
            final URI id, final String opId, final String status,
            final String message) throws DatabaseException {
        checkStarted();
    }

    @Override
    public void markForDeletion(final DataObject object)
            throws DatabaseException {
        checkStarted();
        object.setInactive(true);
    }

    @Override
    public void markForDeletion(final Collection<? extends DataObject> object)
            throws DatabaseException {
        checkStarted();
        for (final DataObject o : object) {
            o.setInactive(true);
        }
    }

    @Override
    public <T extends DataObject> void markForDeletion(final T... object)
            throws DatabaseException {
        checkStarted();
        for (final DataObject o : object) {
            o.setInactive(true);
        }
    }

    @Override
    public void removeObject(final DataObject... object)
            throws DatabaseException {
        checkStarted();
        for (final DataObject oneObject : object) {
            _idToObjectMap.remove(oneObject.getId());
        }
    }

    @Override
    public <T extends DataPoint> String insertTimeSeries(
            final Class<? extends TimeSeries> tsType, final T... data)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataPoint> String insertTimeSeries(
            final Class<? extends TimeSeries> tsType, final DateTime time,
            final T data) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataPoint> void queryTimeSeries(
            final Class<? extends TimeSeries> tsType,
            final DateTime timeBucket, final TimeSeriesQueryResult<T> callback,
            final ExecutorService workerThreads) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataPoint> void queryTimeSeries(
            final Class<? extends TimeSeries> tsType,
            final DateTime timeBucket, final TimeBucket bucket,
            final TimeSeriesQueryResult<T> callback,
            final ExecutorService workerThreads) throws DatabaseException {
    }

    @Override
    public TimeSeriesMetadata queryTimeSeriesMetadata(
            final Class<? extends TimeSeries> tsType) throws DatabaseException {
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
    public <T> void queryByConstraint(final Constraint constraint,
            final QueryResultList<T> result) throws DatabaseException {
        checkStarted();
    }

    @Override
    public <T> void queryByConstraint(final Constraint constraint,
            final QueryResultList<T> result, final URI startId, final int count)
            throws DatabaseException {
    }

    @Override
    public Operation createTaskOpStatus(
            final Class<? extends DataObject> clazz, final URI id,
            final String opId, final Operation newOperation)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation createTaskOpStatus(
            final Class<? extends DataObject> clazz, final URI id,
            final String opId, final ResourceOperationTypeEnum type)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation createTaskOpStatus(
            final Class<? extends DataObject> clazz, final URI id,
            final String opId, final ResourceOperationTypeEnum type,
            final String associatedResources) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation updateTaskOpStatus(
            final Class<? extends DataObject> clazz, final URI id,
            final String opId, final Operation updateOperation)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation ready(final Class<? extends DataObject> clazz,
            final URI id, final String opId) throws DatabaseException {
        checkStarted();
        return null;
    }

    @Override
    public Operation ready(final Class<? extends DataObject> clazz,
            final URI id, final String opId, final String message)
            throws DatabaseException {
        return ready(clazz, id, opId);
    }

    @Override
    public Operation error(final Class<? extends DataObject> clazz,
            final URI id, final String opId, final ServiceCoded serviceCoded)
            throws DatabaseException {
        checkStarted();
        return null;
    }

    @Override
    public Operation pending(final Class<? extends DataObject> clazz,
            final URI id, final String opId, final String message)
            throws DatabaseException {
        checkStarted();
        return null;
    }

    @Override
    public Integer countObjects(final Class<? extends DataObject> type,
            final String columnField, final URI uri) throws DatabaseException {
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
    public <T extends DataObject> List<T> queryObject(final Class<T> clazz,
            final Collection<URI> ids, final boolean activeOnly)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(
            final Class<T> clazz, final Collection<URI> ids,
            final boolean activeOnly) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation suspended_no_error(
            final Class<? extends DataObject> clazz, final URI id,
            final String opId, final String message) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation suspended_no_error(
            final Class<? extends DataObject> clazz, final URI id,
            final String opId) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLocalShortVdcId() {
        return "vdc1";
    }

    @Override
    public URI getVdcUrn(final String shortVdcId) {
        return null;
    }

    @Override
    public void invalidateVdcUrnCache() {
    }

    @Override
    public boolean checkGeoCompatible(final String expectVersion) {
        return true;
    }

    @Override
    public boolean hasUsefulData() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Operation suspended_error(final Class<? extends DataObject> clazz,
            final URI id, final String opId, final ServiceCoded serviceCoded)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

}
