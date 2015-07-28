/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common.diff;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.schema.AnnotationType;
import com.emc.storageos.db.common.schema.AnnotationValue;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.FieldInfo;

@XmlRootElement(name = "schema_changes")
public class DbSchemasDiff extends Diff {
    private static final Logger log = LoggerFactory.getLogger(DbSchemasDiff.class);

    private CollectionChangeTracker<DbSchema, DbSchemaDiff> schemaCT;

    private DbSchemasDiff() {
    }

    public DbSchemasDiff(DbSchemas src, DbSchemas tgt) {
        this(src, tgt, null);
    }

    public DbSchemasDiff(DbSchemas src, DbSchemas tgt, String[] ignoredPkgs) {
        List<DbSchema> srcSchemas = src.getSchemas();
        List<DbSchema> tgtSchemas = tgt.getSchemas();

        if (ignoredPkgs != null) {
            // remove the schemas that should not be checked
            removeIgnoredSchemas(srcSchemas, ignoredPkgs);
            removeIgnoredSchemas(tgtSchemas, ignoredPkgs);
        }

        schemaCT = CollectionChangeTracker.<DbSchema, DbSchemaDiff> newInstance(
                DbSchema.class, DbSchemaDiff.class, srcSchemas, tgtSchemas);
    }

    private void removeIgnoredSchemas(List<DbSchema> schemas, String[] ignoredPkgs) {
        Iterator<DbSchema> iterator = schemas.iterator();
        boolean found = false;
        while (iterator.hasNext()) {
            DbSchema schema = iterator.next();
            found = false;
            for (String pkg : ignoredPkgs) {
                if (schema.getType().startsWith(pkg)) {
                    found = true;
                    break;
                }
            }

            if (found)
             {
                iterator.remove(); // remove the schema that should not be checked
            }
        }
    }

    @XmlElement(name = "schema_changes")
    public CollectionChangeTracker<DbSchema, DbSchemaDiff> getSchemaCT() {
        return schemaCT;
    }

    public boolean isUpgradable() {
        return schemaCT == null || schemaCT.isUpgradable();
    }

    public boolean isChanged() {
        return schemaCT != null && schemaCT.isChanged();
    }

    /**
     * Return a list of new CF schemas from the target schemas
     */
    public List<DbSchema> getNewClasses() {
        if (schemaCT != null) {
            return schemaCT.getNewList();
        }

        return new ArrayList<DbSchema>();
    }

    /**
     * Return a list of new fields from the target schemas with parent CF information
     * 
     * Note that it only includes new fields of EXISITING CF schemas, not fields of
     * new CF schemas
     */
    public List<FieldInfo> getNewFields() {
        List<FieldInfo> fieldList = new ArrayList<FieldInfo>();

        if (schemaCT != null) {
            for (DbSchemaDiff schema : schemaCT.getDiff()) {
                fieldList.addAll(schema.getNewFields());
            }
        }

        return fieldList;
    }

    /**
     * Return a list of new class annotations from the target schemas with parent CF
     * information
     * 
     * Note that it only includes new annotations of EXISITING CF schemas, not annotations
     * of new CF schemas
     */
    public List<AnnotationType> getNewClassAnnotations() {
        List<AnnotationType> annoList = new ArrayList<AnnotationType>();

        if (schemaCT != null) {
            for (DbSchemaDiff schema : schemaCT.getDiff()) {
                annoList.addAll(schema.getNewClassAnnotations());
            }
        }

        return annoList;
    }

    /**
     * Return a list of new field annotations from the target schemas with parent CF/field
     * information
     * 
     * Note that it only includes new annotations of EXISITING fields, not annotations of
     * new fields
     */
    public List<AnnotationType> getNewFieldAnnotations() {
        List<AnnotationType> annoList = new ArrayList<AnnotationType>();

        if (schemaCT != null) {
            for (DbSchemaDiff schema : schemaCT.getDiff()) {
                annoList.addAll(schema.getNewFieldAnnotations());
            }
        }

        return annoList;
    }

    /**
     * Return a list of new annotation values from the target schemas with parent
     * CF/field/annotation information
     * 
     * Note that it only includes new annotation values of EXISITING annotations, not
     * annotation values of new annotations
     */
    public List<AnnotationValue> getNewAnnotationValues() {
        List<AnnotationValue> valueList = new ArrayList<AnnotationValue>();

        if (schemaCT != null) {
            for (DbSchemaDiff schema : schemaCT.getDiff()) {
                valueList.addAll(schema.getNewAnnotationValues());
            }
        }

        return valueList;
    }
}
