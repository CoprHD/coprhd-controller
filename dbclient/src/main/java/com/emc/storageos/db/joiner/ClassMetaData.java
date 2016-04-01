/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.PrefixConstraintImpl;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbIndex;
import com.emc.storageos.db.client.impl.PrefixDbIndex;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;

class ClassMetaData {
    private DataObjectType dot;

    /**
     * Constructor initializes the meta-data for the given class.
     * 
     * @param javaClass
     */
    ClassMetaData(Class<? extends DataObject> javaClass) {
        try {
            this.dot = TypeMap.getDoType(javaClass);
        } catch (Exception ex) {
            dot = null;
        }
    }

    /**
     * Return true if class is abstract.
     */
    boolean isAbstract() {
        return dot == null;
    }

    /**
     * Returns all the subClasses in the MetaData that have myClass as a
     * super class. Useful for doing joins on superclasses.
     * 
     * @param myClass -- Class of object specified in join.
     * @return -- Set<Class> that are subClasses of specified class
     */
    Set<Class<? extends DataObject>> getSubclasses(Class<? extends DataObject> myClass) {
        Set<Class<? extends DataObject>> subClasses = new HashSet<Class<? extends DataObject>>();
        for (DataObjectType doType : TypeMap.getAllDoTypes()) {
            Class supClass = doType.getDataObjectClass();
            while (supClass != null) {
                if (supClass.equals(myClass)) {
                    subClasses.add(doType.getDataObjectClass());
                }
                supClass = supClass.getSuperclass();
            }
        }
        return subClasses;
    }

    /**
     * Returns the DbClient DbIndex structure.
     * 
     * @param fieldName -- String name of field
     * @return DbIndex
     */
    DbIndex getIndex(String fieldName) {
        ColumnField field = dot.getColumnField(fieldName);
        return field.getIndex();
    }

    /**
     * Returns true if an alternate id index is defined.
     * 
     * @param fieldName
     * @return
     */
    boolean isAltIdIndex(String fieldName) {
        DbIndex index = getIndex(fieldName);
        return (index != null && index instanceof AltIdDbIndex);
    }

    /**
     * Returns true if a prefix index is defined.
     * 
     * @param fieldName
     * @return
     */
    boolean isPrefixIndex(String fieldName) {
        DbIndex index = getIndex(fieldName);
        return (index != null && index instanceof PrefixDbIndex);
    }

    /**
     * Returns true if a relation index is defined.
     * 
     * @param fieldName
     * @return
     */
    boolean isRelationIndex(String fieldName) {
        DbIndex index = getIndex(fieldName);
        return (index != null && index instanceof RelationDbIndex);
    }

    /**
     * Returns true if there is a usuable index
     * 
     * @param fieldName
     * @return
     */
    boolean isIndexed(String fieldName) {
        if (isAltIdIndex(fieldName)) {
            return true;
        }
        if (isPrefixIndex(fieldName)) {
            return true;
        }
        if (isRelationIndex(fieldName)) {
            return true;
        }
        return false;
    }

    /**
     * Return the appropriate Constraint for a given field.
     * 
     * @param fieldName
     * @param selectionValue
     * @return
     */
    Constraint buildConstraint(String fieldName, String selectionValue) {
        if (isAltIdIndex(fieldName)) {
            return new AlternateIdConstraintImpl(dot.getColumnField(fieldName), selectionValue);
        }
        if (isPrefixIndex(fieldName)) {
            return new PrefixConstraintImpl(selectionValue, dot.getColumnField(fieldName));
        }
        // No constraint available
        return null;
    }

    <T extends DataObject> Constraint buildConstraint(URI uri, Class<T> clazz, String joinField) {
        ColumnField field = dot.getColumnField(joinField);
        return new ContainmentConstraintImpl(uri, clazz, field);
    }

    Method getGettr(String fieldName) {
        ColumnField field = dot.getColumnField(fieldName);
        return field.getPropertyDescriptor().getReadMethod();
    }

    boolean isId(String fieldName) {
        return dot.getColumnField(fieldName).getType().equals(ColumnField.ColumnType.Id);
    }
}
