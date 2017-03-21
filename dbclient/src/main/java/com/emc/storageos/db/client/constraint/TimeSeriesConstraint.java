/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint;

import java.net.URI;

import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.impl.ClassNameTimeSeriesConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.TimeSeriesConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.uimodels.Order;

public interface TimeSeriesConstraint extends Constraint {
    long count() throws DriverException;

    class Factory {
        private Factory() {
        }

        public static TimeSeriesConstraint getOrdersByUser(String user, long startTimeInMS, long endTimeInMS) {
            DataObjectType doType = TypeMap.getDoType(Order.class);
            ColumnField field = doType.getColumnField(Order.SUBMITTED_BY_USER_ID);
            return new ClassNameTimeSeriesConstraintImpl(field, user, startTimeInMS, endTimeInMS);
        }

        public static TimeSeriesConstraint getOrders(URI tid, long startTimeInMS, long endTimeInMS) {
            DataObjectType doType = TypeMap.getDoType(Order.class);
            ColumnField field = doType.getColumnField(Order.SUBMITTED);
            return new TimeSeriesConstraintImpl(tid.toString(), field, startTimeInMS, endTimeInMS);
        }
    }
}
