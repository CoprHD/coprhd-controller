/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint;

import com.emc.storageos.db.client.constraint.impl.ContainmentPermissionsConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.db.client.model.DiscoveredComputeSystemWithAcls;
import com.emc.storageos.db.client.model.TenantOrg;

/**
 * ContainmentPermissions constraint. For example:
 * - find all permissions on a tenant
 * - find all tenants a user has permissions on
 */
public interface ContainmentPermissionsConstraint extends Constraint {
    /**
     * Factory for creating containment prefix constraint for various object types
     */
    class Factory {
        public static ContainmentPermissionsConstraint getTenantsWithPermissionsConstraint(String key) {
            DataObjectType doType = TypeMap.getDoType(TenantOrg.class);
            ColumnField field = doType.getColumnField("role-assignment");
            return new ContainmentPermissionsConstraintImpl(key, field, TenantOrg.class);
        }

        public static ContainmentPermissionsConstraint
                getObjsWithPermissionsConstraint(String key, Class<? extends DataObjectWithACLs> clazz) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            ColumnField field = doType.getColumnField("acls");
            return new ContainmentPermissionsConstraintImpl(key, field, clazz);
        }

        public static ContainmentPermissionsConstraint getUserMappingsWithDomain(String domain) {
            DataObjectType doType = TypeMap.getDoType(TenantOrg.class);
            ColumnField field = doType.getColumnField("userMappings");
            return new ContainmentPermissionsConstraintImpl(domain.toLowerCase(), field, TenantOrg.class);
        }

        public static Constraint getConstraint(Class<? extends DataObject> type,
                String columeField,
                String key) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columeField);
            return new ContainmentPermissionsConstraintImpl(key, field, type);
        }

        public static ContainmentPermissionsConstraint
                getDiscoveredObjsWithPermissionsConstraint(String key, Class<? extends DiscoveredComputeSystemWithAcls> clazz) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            ColumnField field = doType.getColumnField("acls");
            return new ContainmentPermissionsConstraintImpl(key, field, clazz);
        }
    }
}
