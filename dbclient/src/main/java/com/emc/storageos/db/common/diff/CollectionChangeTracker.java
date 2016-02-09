/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.diff;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.model.valid.EnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.UpgradeAllowed;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.common.schema.AnnotationType;
import com.emc.storageos.db.common.schema.AnnotationValue;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.FieldInfo;
import com.emc.storageos.db.common.schema.Key;
import com.emc.storageos.db.common.schema.SchemaObject;

public class CollectionChangeTracker<T extends SchemaObject, S extends Diff> extends Diff {
    private static final Logger log = LoggerFactory.getLogger(CollectionChangeTracker.class);
    private Class<T> clazz;

    private List<T> newList = new ArrayList<T>();
    private List<T> removedList = new ArrayList<T>();
    private List<T> duplicateList = new ArrayList<T>();
    private List<S> diffs = new ArrayList<S>();

    public static <T extends SchemaObject, S extends Diff> CollectionChangeTracker<T, S> newInstance(
            Class<T> schemaClass, Class<S> diffClass, List<T> srcList, List<T> tgtList) {
        if (srcList == null && tgtList == null) {
            return null;
        }

        if (srcList != null && srcList.equals(tgtList)) {
            return null;
        }

        CollectionChangeTracker<T, S> ret = new CollectionChangeTracker<T, S>(schemaClass,
                diffClass, srcList, tgtList);
        if (ret.isChanged()) {
            return ret;
        } else {
            return null;
        }
    }

    private CollectionChangeTracker() {
    }

    private CollectionChangeTracker(Class<T> schemaClass, Class<S> diffClass,
            List<T> srcList, List<T> tgtList) {
        clazz = schemaClass;

        if (srcList == null || srcList.isEmpty()) {
            newList = tgtList;
            return;
        }

        if (tgtList == null || tgtList.isEmpty()) {
            removedList = srcList;
            return;
        }

        // We want to catch whether there are duplicate keys in the tgt list.
        // This is primarily for CFs. Now let's assume that the src list is safe.
        Map<String, T> tgtMap = new HashMap<String, T>();
        for (T tgt : tgtList) {
            String tgtKey = getKey(tgt);
            if (tgtMap.containsKey(tgtKey)) {
                duplicateList.add(tgt);
            } else {
                tgtMap.put(tgtKey, tgt);
            }
        }
        tgtMap.clear();

        // initialize newList
        for (T tgt : tgtList) {
            String tgtKey = getKey(tgt);
            boolean found = false;
            for (T src : srcList) {
                String srcKey = getKey(src);
                if (tgtKey.equals(srcKey)) {
                    found = true;
                    if (!tgt.equals(src)) {
                        try {
                            S diff = diffClass.getConstructor(schemaClass,
                                    schemaClass).newInstance(src, tgt);
                            if (diff.isChanged()) {
                                diffs.add(diff);
                            }
                        } catch (NoSuchMethodException | InstantiationException |
                                IllegalAccessException | InvocationTargetException e) {
                            log.error("Failed to instantiate {}", diffClass, e);
                        }
                    }
                }
            }
            if (!found) {
                newList.add(tgt);
            }
        }

        // initialize removedList
        for (T src : srcList) {
            String srcKey = getKey(src);
            boolean found = false;
            for (T tgt : tgtList) {
                String tgtKey = getKey(tgt);
                if (tgtKey.equals(srcKey)) {
                    found = true;
                }
            }
            if (!found) {
                removedList.add(src);
            }
        }

    }

    @XmlElements({
            @XmlElement(name = "new_annotation", type = AnnotationType.class),
            @XmlElement(name = "new_annotation_value", type = AnnotationValue.class),
            @XmlElement(name = "new_schema", type = DbSchema.class),
            @XmlElement(name = "new_field", type = FieldInfo.class)
    })
    public List<T> getNewList() {
        return newList;
    }

    @XmlElements({
            @XmlElement(name = "removed_annotation", type = AnnotationType.class),
            @XmlElement(name = "removed_annotation_value", type = AnnotationValue.class),
            @XmlElement(name = "removed_schema", type = DbSchema.class),
            @XmlElement(name = "removed_field", type = FieldInfo.class)
    })
    public List<T> getRemovedList() {
        return removedList;
    }

    @XmlElements({
            @XmlElement(name = "duplicate_annotation", type = AnnotationType.class),
            @XmlElement(name = "duplicate_annotation_value", type = AnnotationValue.class),
            @XmlElement(name = "duplicate_schema", type = DbSchema.class),
            @XmlElement(name = "duplicate_field", type = FieldInfo.class)
    })
    public List<T> getDuplicateList() {
        return duplicateList;
    }

    @XmlElements({
            @XmlElement(name = "changed_annotation", type = AnnotationTypeDiff.class),
            @XmlElement(name = "changed_annotation_value", type = AnnotationValueDiff.class),
            @XmlElement(name = "changed_schema", type = DbSchemaDiff.class),
            @XmlElement(name = "changed_field", type = FieldInfoDiff.class)
    })
    public List<S> getDiff() {
        return diffs;
    }

    @XmlTransient
    public Class<T> getSchemaClass() {
        return clazz;
    }

    private String getKey(T value) {
        Method[] methods = clazz.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isAnnotationPresent(Key.class)) {
                try {
                    return (String) methods[i].invoke(value);
                } catch (Exception e) {
                    log.error("Failed to get key", e);
                }
            }
        }
        return null;
    }

    public boolean isUpgradable() {
        boolean returnVal = true;
        if (!removedList.isEmpty()) {
            for (T schema : removedList) {
                // CustomMigrationCallback can be removed
                if (clazz.equals(AnnotationType.class)) {
                    AnnotationType at = (AnnotationType) schema;
                    // since it has been removed from the target schema, there's no runtime
                    // type associated with it. Do string comparison instead.
                    if (CustomMigrationCallback.class.getCanonicalName().equals(at.getType())) {
                        log.info("CustomMigrationCallback {} has been removed", at.describe());
                        continue;
                    } else if (EnumType.class.getCanonicalName().equals(at.getType())) {
                        log.info("EnumType {} has been removed", at.describe());
                        continue;
                    }
                }
                log.warn("An unsupported schema change has been made. {} has been removed.",
                        schema.describe());
                returnVal = false;
            }
        }

        if (!duplicateList.isEmpty()) {
            for (T schema : duplicateList) {
                log.warn("An unsupported schema change has been made. Duplicate {} has been added",
                        schema.describe());
            }
            returnVal = false;
        }

        for (S diff : diffs) {
            if (!diff.isUpgradable()) {
                returnVal = false;
            }
        }

        if (clazz.equals(AnnotationType.class)) {
            for (T element : newList) {
                AnnotationType at = (AnnotationType) element;
                Class cfClass = at.getCfClass();

                // refuse adding any annotation (including index) on existing field
                if (cfClass.isAnnotationPresent(DbKeyspace.class)) {
                    DbKeyspace keyspaceType = (DbKeyspace) cfClass.getAnnotation(DbKeyspace.class);
                    if (DbKeyspace.Keyspaces.GLOBAL.equals(keyspaceType.value())) {
                        log.warn("An unsupported geo schema change has been made. {} has been added",
                                at.describe());
                        returnVal = false;
                        break;
                    }
                }

                // check UpgradeAllowed annotation for new annotations
                if (!CustomMigrationCallback.class.isAssignableFrom(at.getAnnoClass()) &&
                        !at.getAnnoClass().isAnnotationPresent(UpgradeAllowed.class)) {
                    log.warn("An unsupported schema change has been made. {} has been added",
                            at.describe());
                    returnVal = false;
                    break;
                }
            }
        }

        return returnVal;
    }

    public boolean isChanged() {
        if (!newList.isEmpty()) {
            return true;
        }

        if (!removedList.isEmpty()) {
            return true;
        }

        if (!duplicateList.isEmpty()) {
            return true;
        }

        for (S diff : diffs) {
            if (diff.isChanged()) {
                return true;
            }
        }
        return false;
    }
}
