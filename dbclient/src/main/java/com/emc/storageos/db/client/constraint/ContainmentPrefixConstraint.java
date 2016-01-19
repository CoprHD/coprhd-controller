/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint;

import com.emc.storageos.db.client.constraint.impl.ContainmentLabelConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentPrefixConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.*;

import java.net.URI;

/**
 * ContainmentPrefix constraint. For example, find volumes with its name starting
 * with 'foo' under project X
 */
public interface ContainmentPrefixConstraint extends Constraint {
    /**
     * Factory for creating containment prefix constraint for various object types
     */
    class Factory {
        private static final String PROJECT = "project";

        public static ContainmentPrefixConstraint getProjectUnderTenantConstraint(
                URI tenant, String projectPrefix) {
            DataObjectType doType = TypeMap.getDoType(Project.class);
            ColumnField field = doType.getColumnField("tenantOrg");
            return new ContainmentPrefixConstraintImpl(tenant, projectPrefix, field);
        }

        public static ContainmentPrefixConstraint getFileshareUnderProjectConstraint(
                URI project, String fileSharePrefix) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, fileSharePrefix, field);
        }

        public static ContainmentPrefixConstraint getFileshareUnderTenantConstraint(
                URI tenant, String fileSharePrefix) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            ColumnField field = doType.getColumnField("tenant");
            return new ContainmentPrefixConstraintImpl(tenant, fileSharePrefix, field);
        }

        public static ContainmentPrefixConstraint getVolumeUnderProjectConstraint(
                URI project, String volumePrefix) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, volumePrefix, field);
        }

        public static ContainmentPrefixConstraint getVolumeUnderTenantConstraint(
                URI tenant, String volumePrefix) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("tenant");
            return new ContainmentPrefixConstraintImpl(tenant, volumePrefix, field);
        }

        public static ContainmentPrefixConstraint getExportGroupUnderProjectConstraint(
                URI project, String exportGroupPrefix) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, exportGroupPrefix, field);
        }

        public static ContainmentPrefixConstraint getSnapshotUnderProjectConstraint(
                URI project, String snapshotPrefix) {
            DataObjectType doType = TypeMap.getDoType(Snapshot.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, snapshotPrefix, field);
        }

        public static ContainmentPrefixConstraint getBlockSnapshotUnderProjectConstraint(
                URI project, String blockSnapshotPrefix) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, blockSnapshotPrefix, field);
        }
        
        public static ContainmentPrefixConstraint getBlockSnapshotSessionUnderProjectConstraint(
                URI project, String blockSnapshotSessionPrefix) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, blockSnapshotSessionPrefix, field);
        }

        public static ContainmentPrefixConstraint getExportGroupUnderTenantConstraint(
                URI tenant, String exportGroupPrefix) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            ColumnField field = doType.getColumnField("tenant");
            return new ContainmentPrefixConstraintImpl(tenant, exportGroupPrefix, field);
        }

        public static ContainmentPrefixConstraint getConsistencyGroupUnderProjectConstraint(
                URI project, String consistencyGroupPrefix) {
            DataObjectType doType = TypeMap.getDoType(BlockConsistencyGroup.class);
            ColumnField field = doType.getColumnField(PROJECT);
            return new ContainmentPrefixConstraintImpl(project, consistencyGroupPrefix, field);
        }

        public static ContainmentPrefixConstraint getConstraint(
                Class<? extends DataObject> type,
                String columeField,
                URI resourceUri,
                String prefix) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columeField);
            return new ContainmentPrefixConstraintImpl(resourceUri, prefix, field);
        }

        public static ContainmentPrefixConstraint getFullMatchConstraint(
                Class<? extends DataObject> type,
                String columeField,
                URI resourceUri,
                String prefix) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columeField);
            return new ContainmentLabelConstraintImpl(resourceUri, prefix, field);
        }

    }
}
