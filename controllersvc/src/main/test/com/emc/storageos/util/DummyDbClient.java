/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.joda.time.DateTime;

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
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.collect.Lists;

/**
 * This is used in test programs to fix URIUtil which insists looking things up in the database.
 * This is not convenient for test programs.
 * This is the solution the infrastructure team (i.e. Ben Perkins) proposed.
 */
public class DummyDbClient implements DbClient {

    private List<VirtualDataCenter> vdcs;

    public DummyDbClient() {
        VirtualDataCenter vdc = new VirtualDataCenter();
        vdc.setId(URIUtil.createVirtualDataCenterId("1"));
        vdc.setShortId("vdc1");
        vdc.setApiEndpoint("https://localhost");
        vdc.setLocal(true);
        vdcs = Lists.newArrayList(vdc);
    }

    @Override
    public DataObject queryObject(URI id) throws DatabaseException {
        return null;  // To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI id)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, NamedURI id)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> id)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids,
            boolean activeOnly) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, URI... id)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> id)
            throws DatabaseException {
        if (clazz.equals(VirtualDataCenter.class)) {
            return (Iterator<T>) vdcs.iterator();
        } else {
            return null;
        }
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz,
            Collection<URI> ids, boolean activeOnly) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName,
            Collection<URI> ids) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectField(Class<T> clazz,
            String fieldName, Collection<URI> ids) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void aggregateObjectField(Class<T> clazz, Iterator<URI> ids,
            DbAggregatorItf aggregator) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly) throws DatabaseException {
        if (clazz.equals(VirtualDataCenter.class)) {
            return Lists.newArrayList(vdcs.get(0).getId());
        } else {
            return null;
        }
    }

    @Override
    public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly,
            URI startId, int count) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void queryInactiveObjects(Class<T> clazz, final long timeBefore, QueryResultList<URI> result)
            throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<URI> queryByConstraint(Constraint constraint) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result,
            URI startId, int maxCount) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public Integer countObjects(Class<? extends DataObject> type, String columnField, URI uri)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends DataObject> void createObject(T object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void createObject(Collection<T> object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void createObject(T... object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void persistObject(T object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void persistObject(Collection<T> object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void persistObject(T... object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void updateAndReindexObject(T object)
            throws DatabaseException {
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

    @Override
    public Operation ready(Class<? extends DataObject> clazz, URI id, String opId)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation ready(Class<? extends DataObject> clazz, URI id, String opId,
            String message) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation pending(Class<? extends DataObject> clazz, URI id, String opId,
            String message) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Operation error(Class<? extends DataObject> clazz, URI id, String opId,
            ServiceCoded serviceCoded) throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setStatus(Class<? extends DataObject> clazz, URI id, String opId, String status)
            throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public void setStatus(Class<? extends DataObject> clazz, URI id, String opId,
            String status, String message) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId,
            Operation newOperation) throws DatabaseException {
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
    public void markForDeletion(DataObject object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public void markForDeletion(Collection<? extends DataObject> object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> void markForDeletion(T... object) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeObject(DataObject... object) throws DatabaseException {
        // TODO Auto-generated method stub

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
            DateTime timeBucket, TimeSeriesQueryResult<T> callback,
            ExecutorService workerThreads) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType,
            DateTime timeBucket, TimeBucket bucket, TimeSeriesQueryResult<T> callback,
            ExecutorService workerThreads) throws DatabaseException {
        // TODO Auto-generated method stub

    }

    @Override
    public TimeSeriesMetadata queryTimeSeriesMetadata(Class<? extends TimeSeries> tsType)
            throws DatabaseException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void start() {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> Collection<T> queryObjectFields(Class<T> clazz,
            Collection<String> fieldNames, Collection<URI> ids) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getSchemaVersion() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLocalShortVdcId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public URI getVdcUrn(String shortVdcId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void invalidateVdcUrnCache() {
        // TODO Auto-generated method stub

    }

    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjectFields(
            Class<T> clazz, Collection<String> fieldNames, Collection<URI> ids) {
        // TODO Auto-generated method stub
        return null;
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
