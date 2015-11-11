/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.BulkDataObjQueryResultIterator;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.ColumnValue;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.SMISProvider;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;

public class CustomQueryUtility {

    public static List<SMISProvider> getActiveSMISProvidersByProviderId(DbClient dbClient, String providerId) {
        return queryActiveResourcesByConstraint(dbClient,
                SMISProvider.class,
                AlternateIdConstraint.Factory.getSMISProviderByProviderIDConstraint(providerId));
    }

    public static List<StorageProvider> getActiveStorageProvidersByProviderId(DbClient dbClient, String providerId) {
        return queryActiveResourcesByConstraint(dbClient,
                StorageProvider.class,
                AlternateIdConstraint.Factory.getStorageProviderByProviderIDConstraint(providerId));
    }

    public static List<StorageProvider> getActiveStorageProvidersByInterfaceType(DbClient dbClient, String interfaceType) {
        return queryActiveResourcesByConstraint(dbClient,
                StorageProvider.class,
                AlternateIdConstraint.Factory.getProviderByInterfaceTypeConstraint(interfaceType));
    }

    public static List<StorageSystem> getActiveStorageSystemByMgmAccessId(DbClient dbClient, String mgmId) {
        return queryActiveResourcesByConstraint(dbClient,
                StorageSystem.class,
                AlternateIdConstraint.Factory.getStorageSystemByMgmtAccessPointConstraint(mgmId));
    }

    public static List<StorageSystem> getActiveStorageSystemByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                StorageSystem.class,
                AlternateIdConstraint.Factory.getStorageSystemByNativeGuidConstraint(nativeGuid));
    }

    public static List<StoragePool> getActiveStoragePoolByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                StoragePool.class,
                AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(nativeGuid));
    }

    public static List<StoragePort> getActiveStoragePortByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                StoragePort.class,
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(nativeGuid));
    }

    public static List<ProtectionSet> getActiveProtectionSetByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                ProtectionSet.class,
                AlternateIdConstraint.Factory.getProtectionSetByNativeGuidConstraint(nativeGuid));
    }

    public static List<UnManagedProtectionSet> getUnManagedProtectionSetByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                UnManagedProtectionSet.class,
                AlternateIdConstraint.Factory.getUnManagedProtectionSetByNativeGuidConstraint(nativeGuid));
    }

    public static List<UnManagedProtectionSet> getUnManagedProtectionSetByUnManagedVolumeId(DbClient dbClient, String unManagedVolumeId) {
        return queryActiveResourcesByConstraint(dbClient,
                UnManagedProtectionSet.class,
                AlternateIdConstraint.Factory.getUnManagedProtectionSetByUnManagedVolumeConstraint(unManagedVolumeId));
    }

    public static List<StorageHADomain> getActiveStorageHADomainByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                StorageHADomain.class,
                AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(nativeGuid));
    }

    public static List<Volume> getActiveVolumeByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                Volume.class,
                AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(nativeGuid));
    }

    public static List<BlockSnapshot> getActiveBlockSnapshotByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                BlockSnapshot.class,
                AlternateIdConstraint.Factory.getBlockSnapshotsByNativeGuid(nativeGuid));
    }

    public static List<BlockMirror> getActiveBlockMirrorByNativeGuid(DbClient dbClient, String nativeGuid) {
        return queryActiveResourcesByConstraint(dbClient,
                BlockMirror.class,
                AlternateIdConstraint.Factory.getMirrorByNativeGuid(nativeGuid));
    }

    public static <T extends DataObject> List<T> queryActiveResourcesByAltId(DbClient dbClient,
            Class<T> clazz,
            String columnField,
            String altId) {
        return queryActiveResourcesByConstraint(dbClient,
                clazz,
                AlternateIdConstraint.Factory.getConstraint(clazz, columnField, altId));
    }

    public static <T extends DataObject> List<T> queryActiveResourcesByRelation(DbClient dbClient,
            URI parentID,
            Class<T> childType,
            String childField) {

        return queryActiveResourcesByConstraint(dbClient, childType,
                ContainmentConstraint.Factory.getContainedObjectsConstraint(parentID,
                        childType,
                        childField));

    }

    public static <T extends DataObject> List<T> queryActiveResourcesByConstraint(DbClient dbClient, Class<T> clazz, Constraint constraint) {
        URIQueryResultList list = new URIQueryResultList();
        dbClient.queryByConstraint(constraint, list);
        Iterator<T> resultsIt = dbClient.queryIterativeObjects(clazz, list);
        List<T> objects = new ArrayList<T>();
        while (resultsIt.hasNext()) {
            T obj = resultsIt.next();
            if (!obj.getInactive()) {
                objects.add(obj);
            }
        }
        return objects;
    }

    public static <T extends DataObject> SumPrimitiveFieldAggregator aggregateActiveObject(
            DbClient dbClient, Class<T> clazz,
            String[] fields, Iterator<URI> ids) {

        Iterator<URI> activeIds;
        if (ids != null) {
            activeIds = queryIterativeActiveObjects(dbClient, clazz, ids);
        }
        else {
            activeIds = dbClient.queryByType(clazz, true).iterator();
        }
        SumPrimitiveFieldAggregator aggregator = new SumPrimitiveFieldAggregator(clazz, fields);
        dbClient.aggregateObjectField(clazz, activeIds, aggregator);
        return aggregator;
    }

    public static <T extends DataObject> AggregatedValue aggregatedPrimitiveField(DbClient dbClient,
            Class<T> clazz,
            String groupField,
            String groupValue,
            String aggregatedField) {
        AggregationQueryResultList queryResults = new AggregationQueryResultList();
        dbClient.queryByConstraint(AggregatedConstraint.Factory.getAggregationConstraint(clazz, groupField, groupValue, aggregatedField),
                queryResults);
        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        return getAggregatedValue(it);
    }

    public static <T extends DataObject> AggregatedValue aggregatedPrimitiveField(DbClient dbClient,
            Class<T> clazz,
            String aggregatedField) {

        AggregationQueryResultList queryResults = new AggregationQueryResultList();
        dbClient.queryByConstraint(AggregatedConstraint.Factory.getAggregationConstraint(clazz, aggregatedField),
                queryResults);
        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        return getAggregatedValue(it);
    }

    public static <T extends DataObject> AggregatedValue getAggregatedValue(Iterator<AggregationQueryResultList.AggregatedEntry> it) {
        AggregatedValue agg = new AggregatedValue();
        while (it.hasNext()) {
            AggregationQueryResultList.AggregatedEntry entry = it.next();
            agg.value += agg.getDouble(entry.getValue());
            agg.count++;
        }
        return agg;
    }

    public static <T extends DataObject> SumPrimitiveFieldAggregator aggregateActiveObject(
            DbClient dbClient, Class<T> clazz, String[] fields) {
        return aggregateActiveObject(dbClient, clazz, fields, null);
    }

    private static <T extends DataObject> List<URI> queryActiveObjects(DbClient dbClient, Class<T> clazz, List<URI> ids)
            throws DatabaseException {

        class SelectActiveObjects implements DbAggregatorItf {
            private final List<URI> _activeObjects;
            private final DataObjectType _doType;
            private final String _field;
            private final ColumnField _columnField;

            public List<URI> getActive() {
                return _activeObjects;
            }

            @Override
            public String[] getAggregatedFields() {
                return new String[] { _field };
            }

            public SelectActiveObjects(Class<? extends DataObject> clazz) {
                _activeObjects = new ArrayList<URI>();
                _doType = TypeMap.getDoType(clazz);
                _field = "inactive";
                _columnField = _doType.getColumnField(_field);

            }

            @Override
            public void aggregate(Row<String, CompositeColumnName> row) {

                if (row.getColumns().size() == 0) {
                    return;
                }
                Column<CompositeColumnName> column = row.getColumns().iterator().next();
                Boolean value = (Boolean) ColumnValue.getPrimitiveColumnValue(column, _columnField.getPropertyDescriptor());
                if (!value.booleanValue()) {
                    _activeObjects.add(URI.create(row.getKey()));
                }
            }
        }

        SelectActiveObjects selector = new SelectActiveObjects(clazz);
        dbClient.aggregateObjectField(clazz, ids.iterator(), selector);
        return selector.getActive();
    }

    public static <T extends DataObject> Iterator<URI> queryIterativeActiveObjects(final DbClient dbClient, final Class<T> clazz,
            final Iterator<URI> ids)
            throws DatabaseException {

        if (!(ids.hasNext())) {
            // nothing to do, just an empty list
            return new ArrayList<URI>().iterator();
        }

        BulkDataObjQueryResultIterator<URI> bulkQueryIterator = new
                BulkDataObjQueryResultIterator<URI>(ids) {

                    @Override
                    protected void run() throws DatabaseException {
                        currentIt = null;
                        getNextBatch();
                        while (!nextBatch.isEmpty()) {
                            List<URI> currBatchResults = queryActiveObjects(dbClient, clazz, nextBatch);
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

    private static List<URI> filterDataObjects(DbClient dbClient, Class<? extends DataObject> clazz, String field, List<URI> ids,
            Set<String> values) {
        FieldInSetAggregator aggr = new FieldInSetAggregator(clazz, values, field);
        dbClient.aggregateObjectField(clazz, ids.iterator(), aggr);
        return aggr.getAggregate();
    }

    public static Iterator<URI> filterDataObjectsFieldValueInSet(final DbClient dbClient,
            final Class<? extends DataObject> clazz,
            final String field,
            final Iterator<URI> ids,
            final Set<String> values)
            throws DatabaseException {

        if (!(ids.hasNext())) {
            // nothing to do, just an empty list
            return new ArrayList<URI>().iterator();
        }

        BulkDataObjQueryResultIterator<URI> bulkQueryIterator = new
                BulkDataObjQueryResultIterator<URI>(ids) {

                    @Override
                    protected void run() throws DatabaseException {
                        currentIt = null;
                        getNextBatch();
                        while (!nextBatch.isEmpty()) {
                            List<URI> currBatchResults = filterDataObjects(dbClient, clazz, field, nextBatch, values);
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

    static public class AggregatedValue {
        double value = 0.0;
        long count = 0;

        public long getCount() {
            return count;
        }

        public double getValue() {
            return value;
        }

        double getDouble(Object value) {
            double val = 0;
            if (value instanceof Integer) {
                val = ((Integer) value).intValue();
            } else if (value instanceof Long) {
                val = ((Long) value).longValue();
            } else if (value instanceof Byte) {
                val = ((Byte) value).byteValue();
            } else if (value instanceof Short) {
                val = ((Short) value).shortValue();
            } else if (value instanceof Float) {
                val = ((Float) value).floatValue();
            } else if (value instanceof Double) {
                val = (Double) value;
            } else {
                throw new UnsupportedOperationException();
            }
            return val;
        }
    }

    /**
     * Returns a list from an iterator
     * SHOULD ONLY BE USED ON ITERATORS KNOWN TO HAVE FEW ITEMS ONLY!!!!!
     * 
     * @param itr the iterator
     * @return a list of the iterator items. Empty list if the iterator is empty.
     */
    public static <T extends Object> List<T> iteratorToList(Iterator<T> itr) {
        List<T> list = new ArrayList<T>();
        while (itr.hasNext()) {
            list.add(itr.next());
        }
        return list;
    }

}
