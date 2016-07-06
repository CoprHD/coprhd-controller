/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint;

import com.emc.storageos.db.client.constraint.impl.DecommissionedConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.TimeConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;

import java.util.Date;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public interface DecommissionedConstraint extends Constraint {
    class Factory {
        /**
         * query to get list of decommissioned object URIs of a given type
         * 
         * @param clazz type of objects to query
         * @param timeStartMarker if non-zero, used for filtering the decommissioned objects
         *            marked inactive before the time given in microseconds
         * @return
         */
        public static DecommissionedConstraint getDecommissionedObjectsConstraint(
                Class<? extends DataObject> clazz, long timeStartMarker) {
            return getDecommissionedObjectsConstraint(clazz, "inactive", timeStartMarker);
        }

        /**
         * query to get list of DecommissionedIndex'd object URIs of a given type
         * 
         * @param clazz type of objects to query
         * @param fieldName name of the field indexed
         * @param timeStartMarker if non-zero, used for filtering the decommissioned objects
         *            marked inactive before the time given in microseconds
         * @return
         */
        public static DecommissionedConstraint getDecommissionedObjectsConstraint(
                Class<? extends DataObject> clazz, String fieldName, long timeStartMarker) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new DecommissionedConstraintImpl(clazz,
                    doType.getColumnField(fieldName), timeStartMarker);
        }

        /**
         * query to get list of object URIs of a given type, with given value for the inactive field
         * 
         * @param clazz type of objects to query
         * @param value true - list inactive objects, false - list active objects, null - full list
         * @return
         */
        public static DecommissionedConstraint getAllObjectsConstraint(
                Class<? extends DataObject> clazz, Boolean value) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new DecommissionedConstraintImpl(clazz,
                    doType.getColumnField("inactive"), value);
        }

        /**
         * Query objects on updated time.
         * 
         * @param clazz type of objects to query
         * @param columnName Name of the indexed column to query on
         * @param startTime Start time Date or null for no filtering on start time
         * @param endTime End time Date or null for no filtering on end time
         * @return
         */
        public static DecommissionedConstraint getTimeConstraint(
                Class<? extends DataObject> clazz, String columnName, Date startTime, Date endTime) {
            return getTimeConstraint(clazz, Boolean.TRUE, columnName, startTime, endTime);
        }

        /**
         * Query objects on updated time.
         * 
         * @param clazz type of objects to query
         * @param value Value to query on
         * @param columnName Name of the indexed column to query on
         * @param startTime Start time Date or null for no filtering on start time
         * @param endTime End time Date or null for no filtering on end time
         * @return
         */
        public static DecommissionedConstraint getTimeConstraint(
                Class<? extends DataObject> clazz, Boolean value, String columnName, Date startTime, Date endTime) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new TimeConstraintImpl(clazz, value,
                    doType.getColumnField(columnName).getIndexCF().getName(), startTime, endTime);
        }
    }
}
