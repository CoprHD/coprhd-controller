/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer.DataPoint;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.exceptions.FatalClientControllerException;
import com.emc.storageos.exceptions.RetryableClientControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;

public class FileControllerImplTest {

    private static final URI StorageSystemId = URIUtil.createId(StorageSystem.class);
    private static final String TaskId = UUID.randomUUID().toString();
    private static final URI FileShareId = URIUtil.createId(FileShare.class);
    private static final URI StoragePoolId = URIUtil.createId(StoragePool.class);
    private static volatile FileControllerImpl controller;
    private static volatile StorageSystem storageSystem;

    private static int runcount = 0;

    protected static class StubDispatcher extends Dispatcher {
        @Override
        public void queue(final QueueName queueName, final URI deviceURI, final String deviceType,
                boolean lockDevice, Object target, String method, Object... args) throws ControllerException {
            runcount++;
            if (runcount == 1) {
                throw ClientControllerException.retryables.queueToBusy();
            } else {
                throw ClientControllerException.fatals.unableToQueueJob(deviceURI, new Exception());
            }
        }
    }

    protected static class StubDbClientImpl implements DbClient {

        @Override
        public DataObject queryObject(URI id) throws DatabaseException {
            Class<? extends DataObject> clazz = URIUtil.getModelClass(id);

            return queryObject(clazz, id);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends DataObject> T queryObject(Class<T> clazz, URI id)
                throws DatabaseException {
            if (StorageSystem.class.equals(clazz)) {
                return (T) storageSystem;
            }
            return null;
        }

        @Override
        public <T extends DataObject> T queryObject(Class<T> clazz, NamedURI id)
                throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> id)
                throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> id, boolean acitveOnly)
                throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, URI... id)
                throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> id)
                throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> id, boolean activeOnly)
                throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName,
                Collection<URI> ids) throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjectField(Class<T> clazz, String fieldName,
                Collection<URI> ids) throws DatabaseException {
            return null;
        }

        @Override
        public <T extends DataObject> void aggregateObjectField(Class<T> clazz, Iterator<URI> ids, DbAggregatorItf aggregator)
                throws DatabaseException {
        }

        @Override
        public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly)
                throws DatabaseException {
            return null;
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

        @Override
        public List<URI> queryByConstraint(Constraint constraint) throws DatabaseException {
            return null;
        }

        @Override
        public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result)
                throws DatabaseException {
        }

        @Override
        public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result, URI startId, int count)
                throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void createObject(T object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void createObject(Collection<T> object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void createObject(T... object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void persistObject(T object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void persistObject(Collection<T> object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void persistObject(T... object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(T object)
                throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(Collection<T> object)
                throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(T... object)
                throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void updateObject(T object) {
        }

        @Override
        public <T extends DataObject> void updateObject(Collection<T> objects) {
        }

        @Override
        public <T extends DataObject> void updateObject(T... object) {
        }

        @Override
        public Operation ready(Class<? extends DataObject> clazz, URI id, String opId)
                throws DatabaseException {
            return null;
        }

        @Override
        public Operation ready(Class<? extends DataObject> clazz, URI id, String opId, String message)
                throws DatabaseException {
            return null;
        }

        @Override
        public Operation pending(Class<? extends DataObject> clazz, URI id, String opId, String message)
                throws DatabaseException {
            return null;
        }

        @Override
        public Operation error(Class<? extends DataObject> clazz, URI id, String opId,
                ServiceCoded serviceCoded) throws DatabaseException {
            return null;
        }

        @Deprecated
        public void setStatus(Class<? extends DataObject> clazz,
                URI id, String opId, String status)
                throws DatabaseException {
        }

        @Deprecated
        public void setStatus(Class<? extends DataObject> clazz,
                URI id, String opId, String status, String message)
                throws DatabaseException {
        }

        @Override
        public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId,
                Operation newOperation) throws DatabaseException {
            return null;
        }

        @Override
        public Operation updateTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId,
                Operation updateOperation) throws DatabaseException {
            return null;
        }

        @Override
        public void markForDeletion(DataObject object) throws DatabaseException {
        }

        @Override
        public void markForDeletion(Collection<? extends DataObject> object) throws DatabaseException {
        }

        @Override
        public <T extends DataObject> void markForDeletion(T... object) throws DatabaseException {
        }

        @Override
        public void removeObject(DataObject... object) throws DatabaseException {
        }

        @SuppressWarnings("rawtypes")
        @Override
        public <T extends DataPoint> String insertTimeSeries(Class<? extends TimeSeries> tsType,
                T... data) throws DatabaseException {
            return null;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public <T extends DataPoint> String insertTimeSeries(Class<? extends TimeSeries> tsType,
                DateTime time, T data) throws DatabaseException {
            return null;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public <T extends DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType,
                DateTime timeBucket, TimeSeriesQueryResult<T> callback,
                ExecutorService workerThreads) throws DatabaseException {
        }

        @SuppressWarnings("rawtypes")
        @Override
        public <T extends DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType,
                DateTime timeBucket, TimeBucket bucket, TimeSeriesQueryResult<T> callback,
                ExecutorService workerThreads) throws DatabaseException {
        }

        @SuppressWarnings("rawtypes")
        @Override
        public TimeSeriesMetadata queryTimeSeriesMetadata(Class<? extends TimeSeries> tsType)
                throws DatabaseException {
            return null;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Integer countObjects(Class<? extends DataObject> type, String columnField, URI uri)
                throws DatabaseException {
            return 0;
        }

        @Override
        public <T extends DataObject> Collection<T> queryObjectFields(Class<T> clazz,
                Collection<String> fieldNames, Collection<URI> ids) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjectFields(Class<T> clazz,
                Collection<String> fieldNames, Collection<URI> ids) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getSchemaVersion() {
            return "1";
        }

        @Override
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
        public Operation createTaskOpStatus(Class<? extends DataObject> clazz,
                URI id, String opId, ResourceOperationTypeEnum type)
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
        public boolean checkGeoCompatible(String expectVersion) {
            return true;
        }

        @Override
        public boolean hasUsefulData() {
            // TODO Auto-generated method stub
            return false;
        }
    }

    @BeforeClass
    public static void setupController() throws Exception {
        final Dispatcher dispatcher = new StubDispatcher();
        final Set<FileController> fileControllers = new HashSet<FileController>();
        final FileDeviceController fileDeviceController = new FileDeviceController();

        fileControllers.add(fileDeviceController);

        controller = new FileControllerImpl();
        controller.setDbClient(new StubDbClientImpl());
        controller.setDeviceImpl(fileControllers);
        controller.setDispatcher(dispatcher);

        storageSystem = new StorageSystem();
        storageSystem.setId(StorageSystemId);
    }

    @Test
    public void queueTooBusy() {
        try {
            controller.createFS(storageSystem.getId(), StoragePoolId, FileShareId, null, TaskId);
            Assert.fail("The exception should have been caught");
        } catch (ControllerException e) {
            Assert.assertTrue(e instanceof RetryableClientControllerException);
            Assert.assertEquals(ServiceCode.COORDINATOR_QUEUE_TOO_BUSY, e.getServiceCode());
        }
    }

    @Test
    public void unableToQueueJob() {
        try {
            controller.createFS(storageSystem.getId(), StoragePoolId, FileShareId, null, TaskId);
            Assert.fail("The exception should have been caught");
        } catch (ControllerException e) {
            Assert.assertTrue(e instanceof FatalClientControllerException);
            Assert.assertEquals(ServiceCode.COORDINATOR_UNABLE_TO_QUEUE_JOB, e.getServiceCode());
        }
    }
}
