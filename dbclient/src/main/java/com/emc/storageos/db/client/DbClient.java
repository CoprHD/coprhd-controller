/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.joda.time.DateTime;

import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * The main database client API
 */
public interface DbClient {

    /**
     * Queries for object with given URI, working out the class type from the URI. Deserializes into a data object of given
     * class.
     * 
     * @param id object id
     * @param <T> object type
     * @return deserialized object if record is located. null, if matching record does not
     *         exist
     * @throws DatabaseException TODO
     */
    DataObject queryObject(URI id);

    /**
     * Queries for object with given URI. Deserializes into a data object of given
     * class.
     * 
     * @param clazz object type
     * @param id object id
     * @param <T> object type
     * @return deserialized object if record is located. null, if matching record does not
     *         exist
     * @throws DatabaseException TODO
     */
    <T extends DataObject> T queryObject(Class<T> clazz, URI id);

    /**
     * Same as queryObject(Class, URI). Takes NamedURI instead.
     * 
     * @throws DatabaseException TODO
     */
    <T extends DataObject> T queryObject(Class<T> clazz, NamedURI id);

    /**
     * @deprecated use {@link DbClient#queryIterativeObjects(Class, Collection)} instead
     */
    @Deprecated
    <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids);

    /**
     * @deprecated use {@link DbClient#queryIterativeObjects(Class, Collection, boolean)} instead
     */
    @Deprecated
    <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids, boolean activeOnly);

    /**
     * @deprecated use {@link DbClient#queryIterativeObjects(Class, Collection)} instead
     */
    @Deprecated
    <T extends DataObject> List<T> queryObject(Class<T> clazz, URI... id);

    /**
     * Queries for objects with given URI's. Deserializes into a data object of given
     * class and returns them as an iterator. This method is different from
     * queryObject(Class<T> clazz, List<URI>) in a way that it won't load the entire
     * list of objects into memory at once.if you are dealing with a larger list of
     * objects, use this method instead of queryObject(Class<T> clazz, List<URI>)
     * 
     * @param clazz object type
     * @param id object id
     * @param <T> object type
     * @return deserialized object as an Interator. non matching records are not returned
     * @throws DatabaseException TODO
     */
    <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> id);

    /**
     * Same as {@link DbClient#queryIterativeObjects(Class, Collection)}. Filters on activeOnly record if specified.
     * 
     * @param clazz
     * @param ids
     * @param activeOnly
     * @return
     * @throws DatabaseException
     */
    <T extends DataObject> Iterator<T> queryIterativeObjects(final Class<T> clazz,
            Collection<URI> ids, final boolean activeOnly);

    /**
     * Queries for a particular field on objects with the given URIs
     * 
     * @param clazz object type
     * @param ids object uris
     * @param fieldName field name to get value of
     * @param <T> object type
     * @return deserialized field values for the objects if record is located,
     *         else null
     * @throws DatabaseException TODO
     */
    <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName, Collection<URI> ids);

    /**
     * Queries for a particular field on objects with the given URIs.
     * Deserializes into a data object of given class and returns them as an iterator.
     * This method is different from queryObjectField(Class<T> clazz, String fieldName, List<URI> ids)
     * in a way that it won't load the entire list of objects into memory at once.if you are dealing
     * with a larger list of objects, please use this method.
     * 
     * 
     * @param clazz object type
     * @param ids object uris
     * @param fieldName field name to get value of
     * @param <T> object type
     * @return deserialized object as an Interator. non matching records are not returned
     * @throws DatabaseException TODO
     */
    <T extends DataObject> Iterator<T> queryIterativeObjectField(Class<T> clazz, String fieldName, Collection<URI> ids);

    /**
     * Aggregate fields for the column by using the provided Aggregator.
     * This funciton is similar to queryObjectField but it supplies the field values into Aggregator
     * instead of deserializing objects of the clazz
     * 
     * 
     * @param clazz
     * @param ids
     * @param aggregator
     * @param <T>
     * @throws DatabaseException
     */
    <T extends DataObject> void aggregateObjectField(Class<T> clazz, Iterator<URI> ids, DbAggregatorItf aggregator);

    /**
     * Return references for objects with given type
     * 
     * @param clazz object type
     * @param activeOnly if true, gets only active object ids
     * @return
     * @throws DatabaseException TODO
     */
    <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly);

    /**
     * Return references for objects with given type
     * 
     * @param clazz object type
     * @param activeOnly if true, gets only active object ids
     * @param startId the resource ID (execlusive) where the query begins
     * @param count the max number of records returned
     * @return
     * @throws DatabaseException
     */
    <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly, URI startId, int count);

    /**
     * Query for object of given type that is marked as inactive before specified time.
     * 
     * @param clazz
     * @param timeBefore
     * @param result
     * @param <T>
     * @throws DatabaseException
     */
    <T extends DataObject> void queryInactiveObjects(Class<T> clazz, long timeBefore, QueryResultList<URI> result);

    /**
     * Queries for objects with given constraint. See constraint package for availabile
     * options.
     * 
     * This interface is deprecated. Use queryByConstraint(Constraint, Constraint.QueryResult).
     * 
     * @param constraint one of available constraints from constraint package
     * @return list of object URI's that match given constraint
     * @throws DatabaseException TODO
     */
    @Deprecated
    List<URI> queryByConstraint(Constraint constraint);

    /**
     * Queries for objects with given constraint. See constraint package for
     * availabile options.
     * 
     * @param constraint one of available constraints from constraint package
     * @param result out parameter where query results will be added to
     * @throws DatabaseException TODO
     */
    <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result);

    /**
     * Queries for objects with given constraint start from some resource. See constraint package for
     * availabile options.
     * 
     * @param constraint one of available constraints from constraint package
     * @param result out parameter where query results will be added to
     * @param startId where the query starts
     * @param maxCount the max number of resources returned by this query
     * 
     * @throws DatabaseException TODO
     */
    <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result, URI startId, int maxCount);

    /**
     * Returns the count of objects with the given type which have the given URI in the specified
     * columnField.
     * 
     * @param type
     * @param columnField
     * @param uri
     * @throws DatabaseException TODO
     */
    Integer countObjects(Class<? extends DataObject> type, String columnField, URI uri);

    /**
     * Persists given new object to DB. DataObject.id field must be filled in. This method
     * should only be used to persists new objects that doesn't exist in DB.
     * 
     * @param object object to persist
     * @param <T> data objects
     * @throws DatabaseException TODO
     */
    <T extends DataObject> void createObject(T object);

    /**
     * Persists given list of new objects to DB. DataObject.id field must be filled in.
     * This method should only be used to persists new objects that doesn't exist in DB.
     * 
     * @param object object to persist
     * @param <T> data objects
     * @throws DatabaseException TODO
     */
    <T extends DataObject> void createObject(Collection<T> objects);

    /**
     * See createObject(List<T>)
     * 
     * @throws DatabaseException TODO
     */
    <T extends DataObject> void createObject(T... object);

    /**
     * @deprecated use {@link DbClient#updateObject(T)} instead
     */
    @Deprecated
    <T extends DataObject> void persistObject(T object);

    /**
     * @deprecated use {@link DbClient#updateObject(Collection)} instead
     */
    @Deprecated
    <T extends DataObject> void persistObject(Collection<T> objects);

    /**
     * @deprecated use {@link DbClient#updateObject(T...)} instead
     */
    @Deprecated
    <T extends DataObject> void persistObject(T... object);

    /**
     * @deprecated use {@link DbClient#updateObject(T)} instead
     */
    @Deprecated
    <T extends DataObject> void updateAndReindexObject(T object);

    /**
     * @deprecated use {@link DbClient#updateObject(Collection)} instead
     */
    @Deprecated
    <T extends DataObject> void updateAndReindexObject(Collection<T> objects);

    /**
     * @deprecated use {@link DbClient#updateObject(T...)} instead
     */
    @Deprecated
    <T extends DataObject> void updateAndReindexObject(T... object);

    /**
     * Updates given existing object to DB, also synchronously updates the index fields if needed.
     * <p/>
     * DataObject.id field must be filled in. This method
     * only updates non null fields to DB (meaning partial write is possible). For example,
     * if FileShare.label field is not set but DB already has label field persisted, it won't
     * overwrite existing label field when this FileShare object is updated.
     * <p/>
     * For StringMap and StringSet, you can also incrementally add and remove entries by inserting
     * only new elements into them. In order to incrementally remove elements, see javadoc for
     * these two types.
     *
     * @param object object to update
     * @param <T> data objects
     * @throws DatabaseException TODO
     */
    <T extends DataObject> void updateObject(T object);

    /**
     * Updates given list of existing objects to DB. DataObject.id field must be filled in.
     * This method only updates non null fields to DB (meaning partial write is possible).
     *
     * @param objects objects to update
     * @param <T> data objects
     * @throws DatabaseException TODO
     */
    <T extends DataObject> void updateObject(Collection<T> objects);

    /**
     * @see DbClient#updateObject(Collection)
     */
    <T extends DataObject> void updateObject(T... object);

    /**
     * Convenience method for setting operation status to ready for given object
     * 
     * @param clazz
     * @param id
     * @param opId
     * @return TODO
     * @throws DatabaseException
     */
    Operation ready(Class<? extends DataObject> clazz, URI id, String opId);

    /**
     * Convenience method for setting operation status to ready for given object
     * 
     * @param clazz
     * @param id
     * @param opId
     * @param message
     * @return TODO
     * @throws DatabaseException
     */
    Operation ready(Class<? extends DataObject> clazz, URI id, String opId, String message);

    /**
     * Convenience method for setting operation status to pending for given
     * object
     * 
     * @param clazz
     * @param id
     * @param opId
     * @return TODO
     * @throws DatabaseException
     */
    Operation pending(Class<? extends DataObject> clazz, URI id, String opId, String message);

    /**
     * Convenience method for setting operation status to error for given object
     * 
     * @param clazz
     * @param id
     * @param serviceCode
     * @return TODO
     * @throws DatabaseException
     */
    Operation error(Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded);

    /**
     * Convenience method for setting operation status for given object
     * 
     * @param clazz
     * @param id
     * @param opId
     * @param status
     * @throws DatabaseException TODO
     */
    @Deprecated
    void setStatus(Class<? extends DataObject> clazz,
            URI id, String opId, String status);

    /**
     * Convenience method for setting operation status for given object
     * 
     * @param clazz
     * @param id
     * @param opId
     * @param status
     * @param message
     * @throws DatabaseException
     */
    @Deprecated
    void setStatus(Class<? extends DataObject> clazz,
            URI id, String opId, String status, String message);

    /**
     * Convenience method for creating and setting the operation status for
     * given object This method will set the starttime of the Operation to
     * current calendar time. If the passed in opertion object has start time
     * set, that will be overwritten by the current calendar time.
     * 
     * @param clazz
     * @param id - uuid of the resource
     * @param opId - task or operation id
     * @param newOperation - operation object
     * @throws DatabaseException
     */
    Operation createTaskOpStatus(Class<? extends DataObject> clazz,
            URI id, String opId, Operation newOperation);

    /**
     * Convenience method for creating and setting the operation status for
     * given object. This method will set the start time of the Operation to
     * current calendar time.
     * 
     * @param clazz
     * @param id - uuid of the resource
     * @param opId - task or operation id
     * @param type - type of operation
     * @throws DatabaseException
     */
    Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, ResourceOperationTypeEnum type);

    /**
     * Convenience method for creating and setting the operation status for
     * given object. This method will set the start time of the Operation to
     * current calendar time.
     * 
     * @param clazz
     * @param id - uuid of the resource
     * @param opId - task or operation id
     * @param type - type of operation
     * @param associatedResources - list of associated resources to the task
     * @throws DatabaseException
     */
    Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id,
            String opId, ResourceOperationTypeEnum type,
            String associatedResources);

    /**
     * Convenience method for updating the operation status for given object
     * 
     * @param clazz
     * @param id - uuid of the resource
     * @param opId - task or operation id
     * @param updateOperation - operation object that contains the new opStatus.
     * @throws DatabaseException TODO
     */
    @Deprecated
    // As public, should be changed to protected when no longer called from outside this class hierarchy.
            Operation updateTaskOpStatus(Class<? extends DataObject> clazz,
                    URI id, String opId, Operation updateOperation);

    /**
     * Marks an object for deletion. Note that DB does not remove an object immediately
     * after it's marked for deletion. This is done for a couple of reasons
     * 
     * 1. avoid lost update problems during concurrent delete / update
     * 2. to give clients a chance to verify object status prior to removal from db
     * 
     * DB svc will periodically purge objects older than preconfigured time period.
     * 
     * @param object object to mark for deletion
     * @throws DatabaseException TODO
     */
    void markForDeletion(DataObject object);

    /**
     * Marks a list of objects for deletion. Note that DB does not remove an object immediately
     * after it's marked for deletion. This is done for a couple of reasons
     * 
     * 1. avoid lost update problems during concurrent delete / update
     * 2. to give clients a chance to verify object status prior to removal from db
     * 
     * DB svc will periodically purge objects older than preconfigured time period.
     * 
     * @param object list of objects to mark for deletion
     * @throws DatabaseException TODO
     */
    void markForDeletion(Collection<? extends DataObject> objects);

    /**
     * See markForDeletion(List)
     * 
     * @throws DatabaseException TODO
     */
    <T extends DataObject> void markForDeletion(T... object);

    /**
     * Delete records for objects
     * 
     * @param object array of objects to delete
     * @throws DatabaseException TODO
     */
    void removeObject(DataObject... object);

    /**
     * Inserts time series data of given type. Time series implementation
     * implements TimeSeries<T> interface. EventTimeSeries is an example of such
     * implementation for event data. For other types of time series data, You
     * can follow EventTimeSeries template.
     * 
     * Note that shard and bucket granularity are defined using annotations in
     * your TimeSeries implementation.
     * 
     * Time series is organized (in the DB) in the following manner
     * 
     * If bucket granularity is specified as HOURLY and shard count is 10
     * 
     * 1. For every hour time bucket(UTC time), there will be 10 rows. 2.
     * Insertion will spread across those 10 rows for the same hour 3. Each time
     * series data point will have insertion time uuid as column id and
     * serialized content as value
     * 
     * Consistency level of one is used for insertion. This means that
     * read-after-write is not guaranteed in a cluster. Query clients should not
     * expect all results to be present when querying the current time bucket
     * 
     * @param tsType time series implementation class (such as EventTimeSeries,
     *            for example)
     * @param data time series data points to insert
     * @param <T>
     * @return bucket / row ID where this batch of time series data was stored
     * @throws DatabaseException TODO
     */
    <T extends TimeSeriesSerializer.DataPoint> String insertTimeSeries(
            Class<? extends TimeSeries> tsType, T... data);

    /**
     * Inserts a data point of time series with given timestamp.
     * 
     * This API is for testing convenience only. We should not use it in normal
     * code path.
     * 
     * @param tsType time series implementation class (such as EventTimeSeries,
     *            for example)
     * @param time timestamp
     * @param data time series data point to insert
     * @return bucket / row ID where this batch of time series data was stored
     * @throws DatabaseException TODO
     */
    <T extends TimeSeriesSerializer.DataPoint> String insertTimeSeries(
            Class<? extends TimeSeries> tsType, DateTime time, T data);

    /**
     * Queries time series data for given time bucket. For example, if time
     * series class specifies HOURLY bucket and timeBucket argument is 2012-4-16
     * 1:10:00 UTC time, this query will retrieves data from all shards for an
     * hour bucket for 2012-4-16 1:00:00 UTC time.
     * 
     * This method will call TimeSeriesQueryResult#data for every record found
     * in that time bucket. Note that callbacks will done from multiple threads
     * using workerThreads executor service.
     * 
     * Note that executor service should limit the number of threads used for
     * parallel execution (fixed thread pool, for example). For each shard in a
     * given time bucket, default implementation will launch a parallel queries.
     * 
     * @param tsType See insertTimeSeries for explanation of what time series
     *            class is
     * @param timeBucket time bucket
     * @param callback result callback
     * @param workerThreads executor service to use for running parallel queries
     * @param <T>
     * @throws DatabaseException TODO
     */
    <T extends TimeSeriesSerializer.DataPoint> void queryTimeSeries(
            Class<? extends TimeSeries> tsType, DateTime timeBucket,
            TimeSeriesQueryResult<T> callback, ExecutorService workerThreads);

    /**
     * Overload of queryTimeSeries(TimeSeries, DateTime, TImeSeriesQueryResult,
     * ExecutorService) that takes non default TimeBucket. Note that bucket must
     * be be same or finer granularity than what's specified in TimeSeries type.
     * For example, you may use MINUTE and HOUR buckets when time series type is
     * specified with HOUR bucket.
     * 
     * @throws DatabaseException TODO
     */
    <T extends TimeSeriesSerializer.DataPoint> void queryTimeSeries(
            Class<? extends TimeSeries> tsType, DateTime timeBucket,
            TimeSeriesMetadata.TimeBucket bucket, TimeSeriesQueryResult<T> callback,
            ExecutorService workerThreads);

    /**
     * Queries metadata for give time series data type
     * 
     * @param tsType time series class (like EventTimeSeries)
     * @return
     * @throws DatabaseException TODO
     */
    TimeSeriesMetadata queryTimeSeriesMetadata(Class<? extends TimeSeries> tsType);

    /**
     * Starts DB connection pool
     */
    void start();

    /**
     * Shuts down DB connection pool
     */
    void stop();

    <T extends DataObject> Collection<T>
            queryObjectFields(Class<T> clazz, Collection<String> fieldNames, Collection<URI> ids);

    <T extends DataObject> Iterator<T>
            queryIterativeObjectFields(Class<T> clazz, Collection<String> fieldNames, Collection<URI> ids);

    /**
     * Get the DB schema version
     */
    String getSchemaVersion();

    /**
     * Retrieve the short ID of the local VDC
     * Deprecated; use VdcUtils.getLocakVcdId() instead
     * 
     * @return the vdc id
     */
    @Deprecated
    String getLocalShortVdcId();

    /**
     * Get the full URN for the VDC referenced by the short vdcId
     * Deprecated; use VdcUtils.getVdcUrn() instead
     * 
     * @param shortVdcId the vdc short id
     * @return the VDC object's full URN or null if not found
     */
    @Deprecated
    URI getVdcUrn(String shortVdcId);

    /**
     * For a rebuild of the Vdc to Urn cache used for mapping short VDC ids
     * Deprecated; use VdcUtils.invalidateVdcUrnCache() instead
     */
    @Deprecated
    void invalidateVdcUrnCache();

    /**
     * Check if Geo db version is compatible or not in a federation.
     * 
     * @param expectVersion version of Geo db
     * @return true if Geo db version in all vdcs of the federation is equals or high then expectVersion which indicates
     *         the features for geoVersion can be enabled. Otherwise false;
     * */
    boolean checkGeoCompatible(String expectVersion);
    
    /**
     * Check whether there is active and useful data in database. Don't check the data whose type is in excludeClasses
     * @return true if there are useful data
     */
    boolean hasUsefulData();
}
