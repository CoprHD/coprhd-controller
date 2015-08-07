/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint;

import com.emc.storageos.db.client.constraint.impl.AggregatedConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public interface AggregatedConstraint extends Constraint {
    static class Factory {

        /**
         * query to get list of aggregated ids and corresponding values
         * 
         * @param clazz type of objects to query
         * @param groupByField the groupBy field for aggregation.
         * @param groupByValue the value of the groupBy field for aggregation.
         * @param aggregatedField - aggregated field of an object that is being aggregated.
         * @return
         */
        public static AggregatedConstraint getAggregationConstraint(
                Class<? extends DataObject> clazz,
                String groupByField,
                String groupByValue,
                String aggregatedField) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new AggregatedConstraintImpl(clazz,
                    doType.getColumnField(groupByField),
                    groupByValue,
                    doType.getColumnField(aggregatedField));
        }

        /**
         * query to get list of aggregated ids and corresponding values
         * the query obtained class level aggregation
         * 
         * @param clazz type of objects to query
         * @param aggregatedField - aggregated field of an object that is being aggregated.
         * @return
         */
        public static AggregatedConstraint getAggregationConstraint(
                Class<? extends DataObject> clazz,
                String aggregatedField) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new AggregatedConstraintImpl(clazz,
                    doType.getColumnField(aggregatedField));
        }
    }
}
