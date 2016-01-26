/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.Encrypt;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Shards;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.common.diff.DbSchemasDiff;
import com.emc.storageos.db.common.schema.DataObjectSchema;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class BaseDbSchemaCheckerTest {

    protected DbSchema srcSchema;
    protected DbSchema tgtSchema;
    protected DbSchemas srcSchemas;
    protected DbSchemas tgtSchemas;

    protected DbSchemasDiff diff;

    @Before
    public void initialize() {
        srcSchema = new DataObjectSchema(ClassUT.class);
        srcSchemas = new DbSchemas();
        srcSchemas.addSchema(srcSchema);
        tgtSchemas = new DbSchemas();
    }

    @Test
    public void testMoveFieldToBaseClass() {
        srcSchemas.setSchemas(new ArrayList<DbSchema>());
        tgtSchemas.setSchemas(new ArrayList<DbSchema>());

        srcSchema = new DataObjectSchema(ChildClassUTBefore.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        tgtSchema = new DataObjectSchema(ChildClassUTAfter1.class);
        tgtSchema.setType(tgtSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        tgtSchema = new DataObjectSchema(ChildClassUTAfter2.class);
        tgtSchema.setType(tgtSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testCustomMigrationExistingField() {

        tgtSchema = new DataObjectSchema(CustomMigrationExistingField.class);
        tgtSchema.setType(tgtSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertTrue(diff.isUpgradable());
        Assert.assertFalse(diff.isChanged());
    }

    @Test
    public void testCustomMigrationPreUpgrade() {
        srcSchemas.setSchemas(new ArrayList<DbSchema>());
        tgtSchemas.setSchemas(new ArrayList<DbSchema>());

        srcSchema = new DataObjectSchema(CustomMigrationExistingField.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        tgtSchema = new DataObjectSchema(CustomMigrationExistingAndNewField.class);
        tgtSchema.setType(tgtSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    /**
     * a DataObject derived class under test
     */
    @Cf("schema_ut")
    protected static class ClassUT extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    @Shards(10)
    protected static class NewClassAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    protected static class RemovedClassAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class WithIndexAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @AlternateId("altIdName")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class WithRemovedIndexAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("class_new_annotation_value")
    protected static class NewClassAnnotationValue extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class RemovedField extends DataObject {
    }

    @Cf("schema_ut")
    protected static class NewField extends DataObject {
        private String fieldUT;
        private String newFieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }

        @Name("new_field_ut")
        public String getNewFieldUT() {
            return newFieldUT;
        }

        public void setNewFieldUT(String newFieldUT) {
            this.newFieldUT = newFieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class NewFieldType extends DataObject {
        private Boolean fieldUT;

        @Name("field_ut")
        public Boolean getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(Boolean fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class NewFieldAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @AlternateId("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class RemovedFieldAnnotation extends DataObject {
        private String fieldUT;

        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class WithChangedAnnotationValue extends DataObject {
        private String fieldUT;

        @Name("new_field_name_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class NewPermittedFieldAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @AlternateId("PermittedNewIndex")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class NewNotPermittedFieldAnnotation extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @Encrypt
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut2")
    protected static class classUT2 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class DuplicateCF1 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class DuplicateCF2 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut_2")
    protected static class ClassUT7 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut_2")
    protected static class ClassUT3 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @Encrypt
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut_3")
    protected static class ClassUT4 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut_4")
    protected static class ClassUT5 extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut_4")
    protected static class ClassUT6 extends DataObject {
        private String fieldUT;

        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class WithNamedRelationIndex extends DataObject {
        private String fieldUT;

        @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    protected static class BaseClassUTBefore extends DataObject {
    }

    @Cf("schema_ut")
    protected static class ChildClassUTBefore extends BaseClassUTBefore {
        private String fieldUT;
        private String unchangedField;

        @Name("field_ut")
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }

        @Name("unchnaged_field")
        public String getUnchangedField() {
            return unchangedField;
        }

        public void setUnchangedField(String unchangedField) {
            this.unchangedField = unchangedField;
        }
    }

    protected static class BaseClassUTAfter extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @CustomMigrationCallback(callback = MyTestMigrationCallback.class)
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class ChildClassUTAfter1 extends BaseClassUTAfter {
        private String unchangedField;

        @Name("unchnaged_field")
        @AlternateId("AltIdName")
        public String getUnchangedField() {
            return unchangedField;
        }

        public void setUnchangedField(String unchangedField) {
            this.unchangedField = unchangedField;
        }

    }

    @Cf("schema_ut2")
    protected static class ChildClassUTAfter2 extends BaseClassUTAfter {
    }

    protected static class MyTestMigrationCallback extends BaseCustomMigrationCallback {

        private static final Logger log = LoggerFactory.getLogger(MyTestMigrationCallback.class);

        public MyTestMigrationCallback() {

        }

        @Override
        public void process() throws MigrationCallbackException {
            log.info("in custom migration process()");

        }
    }

    protected static class MyTestMigrationCallback2 extends BaseCustomMigrationCallback {

        private static final Logger log = LoggerFactory.getLogger(MyTestMigrationCallback2.class);

        public MyTestMigrationCallback2() {

        }

        @Override
        public void process() throws MigrationCallbackException {
            log.info("in custom migration 2 process()");

        }
    }

    @Cf("schema_ut")
    protected static class CustomMigrationExistingField extends DataObject {
        private String fieldUT;

        @Name("field_ut")
        @CustomMigrationCallback(callback = MyTestMigrationCallback2.class)
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }
    }

    @Cf("schema_ut")
    protected static class CustomMigrationExistingAndNewField extends DataObject {
        private String fieldUT;
        private String newField;

        @Name("field_ut")
        @CustomMigrationCallback(callback = MyTestMigrationCallback2.class)
        public String getFieldUT() {
            return fieldUT;
        }

        public void setFieldUT(String fieldUT) {
            this.fieldUT = fieldUT;
        }

        @Name("new_field")
        @CustomMigrationCallback(callback = MyTestMigrationCallback.class)
        public String getNewField() {
            return newField;
        }

        public void setNewField(String newField) {
            this.newField = newField;
        }
    }

    @Cf("geoSchema_ut")
    @DbKeyspace(Keyspaces.GLOBAL)
    protected static class GeoClassUT extends DataObject {
        private String geoFieldUT;

        @Name("geoField_ut")
        public String getGeoFieldUT() {
            return geoFieldUT;
        }

        public void setGeoFieldUT(String geoFieldUT) {
            this.geoFieldUT = geoFieldUT;
        }
    }

    @Cf("geoSchema_ut")
    @DbKeyspace(Keyspaces.GLOBAL)
    protected static class GeoNewAnnotationOnExistingField extends DataObject {
        private String geoFieldUT;

        @Name("geoField_ut")
        @AlternateId("geoField_ut")
        public String getGeoFieldUT() {
            return geoFieldUT;
        }

        public void setGeoFieldUT(String geoFieldUT) {
            this.geoFieldUT = geoFieldUT;
        }
    }

    @Cf("geoSchema_ut")
    @DbKeyspace(Keyspaces.GLOBAL)
    protected static class GeoNewField extends DataObject {
        private String geoFieldUT;
        private String newField;

        @Name("geoField_ut")
        public String getGeoFieldUT() {
            return geoFieldUT;
        }

        public void setGeoFieldUT(String geoFieldUT) {
            this.geoFieldUT = geoFieldUT;
        }

        @Name("new_field")
        @AlternateId("geoField_ut")
        public String getNewField() {
            return newField;
        }

        public void setNewField(String newField) {
            this.newField = newField;
        }
    }

    @Cf("geoSchema_ut_new")
    @DbKeyspace(Keyspaces.GLOBAL)
    protected static class GeoNewCF extends DataObject {
        private String geoFieldUT;

        @Name("geoField_ut")
        @AlternateId("geoField_ut")
        public String getGeoFieldUT() {
            return geoFieldUT;
        }

        public void setGeoFieldUT(String geoFieldUT) {
            this.geoFieldUT = geoFieldUT;
        }
    }
}
