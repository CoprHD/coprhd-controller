/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint;

import java.net.URI;

import com.emc.storageos.db.client.constraint.impl.LabelConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.PrefixConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Query constraint for type & prefix matching. For example, give me FileShare
 * with label "foobar*"
 */
public interface PrefixConstraint extends Constraint {
    /**
     * Factory for creating prefix constraint
     */
    class Factory {
        // tags - prefix search
        public static PrefixConstraint getTagsPrefixConstraint(Class<? extends DataObject> clazz, String prefix, URI tenant) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new PrefixConstraintImpl(tenant, prefix, doType.getColumnField("tags"));
        }

        // label - prefix search
        public static PrefixConstraint getLabelPrefixConstraint(Class<? extends DataObject> clazz, String prefix) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new PrefixConstraintImpl(prefix, doType.getColumnField("label"));
        }

        // tags - prefix search
        // This method allow system admin to get prefix constraint for system objects!!!
        public static PrefixConstraint getTagsPrefixConstraint(Class<? extends DataObject> clazz, String prefix) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            return new PrefixConstraintImpl(prefix, doType.getColumnField("tags"));
        }

        // prefix indexed field - prefix search
        public static Constraint getConstraint(Class<? extends DataObject> type,
                String columeField,
                String prefix) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columeField);
            return new PrefixConstraintImpl(prefix, field);
        }

        // prefix indexed field - prefix search, scoped to resource uri
        public static Constraint getConstraint(Class<? extends DataObject> type,
                String columeField,
                String prefix,
                URI resourceUri) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columeField);
            return new PrefixConstraintImpl(resourceUri, prefix, field);
        }

        // prefix indexed field - full string match
        public static PrefixConstraint getFullMatchConstraint(Class<? extends DataObject> type,
                String columeField,
                String prefix) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columeField);
            return new LabelConstraintImpl(prefix, field);
        }
    }
}
