/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.diff.DbSchemasDiff;
import com.emc.storageos.db.common.schema.DataObjectSchema;
import com.emc.storageos.db.common.schema.DataPointSchema;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.TimeSeriesSchema;
import com.emc.storageos.services.util.LoggingUtils;

public class DbSchemaCheckerTest extends BaseDbSchemaCheckerTest {
    static {
        LoggingUtils.configureIfNecessary("dbchecker-log4j.properties");
    }
    private static final Logger log = LoggerFactory.getLogger(DbSchemaCheckerTest.class);

    @XmlRootElement(name = "dbschemas")
    private static class SchemaUT {
        @XmlElements({
                @XmlElement(name = "data_object_schema", type = DataObjectSchema.class),
                @XmlElement(name = "time_series_schema", type = TimeSeriesSchema.class),
                @XmlElement(name = "data_point_schema", type = DataPointSchema.class)
        })
        public DbSchema schema;

        public SchemaUT() {
        }

        public SchemaUT(DbSchema schema) {
            this.schema = schema;
        }
    }

    @Test
    public void testSchemaConsistent() {
        StringWriter sw1 = new StringWriter();
        StringWriter sw2 = new StringWriter();

        SchemaUT schemaUT = new SchemaUT(srcSchema);
        try {
            JAXBContext jc = JAXBContext.newInstance(SchemaUT.class);
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));

            m.marshal(schemaUT, sw1);
            m.marshal(schemaUT, sw2);

            Assert.assertEquals(sw1.getBuffer().toString(), sw2.getBuffer().toString());
        } catch (Exception e) {
            log.error("testSchemaConsistent failed:", e);
            Assert.fail();
        } finally {
            try {
                sw1.close();
                sw2.close();
            } catch (IOException e) {
                log.warn("IO close exception:{}", e);
            }
        }
    }

    @Test
    public void testSchemaCheckRoundTrip() {
        BufferedWriter writer = null;
        String schemaFile = FileUtils.getTempDirectoryPath() + "/.schema";
        SchemaUT schemaUT = new SchemaUT(srcSchema);
        JAXBContext jc;
        try {
            File output = new File(schemaFile);
            writer = new BufferedWriter(new FileWriter(output));

            jc = JAXBContext.newInstance(SchemaUT.class);
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            StringWriter sw = new StringWriter();
            m.marshal(schemaUT, sw);

            writer.write(sw.toString());
        } catch (Exception e) {
            log.error("testSchemaCheckRoundTrip failed:{}", e);
            Assert.fail();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                log.warn("IO close exception:{}", e);
            }
        }

        try {
            File input = new File(schemaFile);

            jc = JAXBContext.newInstance(DbSchemas.class);
            Unmarshaller um = jc.createUnmarshaller();

            DbSchemas schemas = (DbSchemas) um.unmarshal(input);

            Assert.assertEquals(srcSchema, schemas.getSchemas().get(0));
        } catch (Exception e) {
            log.error("testSchemaCheckRoundTrip failed:{}", e);
            Assert.fail();
        }
    }

    @Test
    public void testNewClassAnnotation() {
        tgtSchema = new DataObjectSchema(NewClassAnnotation.class);
        // a little bit of hack
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        // No new class annotation is allowed
        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());

        Assert.assertTrue(diff.getNewClasses().isEmpty());
        Assert.assertTrue(diff.getNewFields().isEmpty());
        Assert.assertFalse(diff.getNewClassAnnotations().isEmpty());
        Assert.assertEquals(diff.getNewClassAnnotations().size(), 1);
        Assert.assertTrue(diff.getNewFieldAnnotations().isEmpty());
        Assert.assertTrue(diff.getNewAnnotationValues().isEmpty());
    }

    @Test
    public void testRemovedClassAnnotation() {
        tgtSchema = new DataObjectSchema(RemovedClassAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testRemovedFieldIndexAnnotation() {
        srcSchema = new DataObjectSchema(WithIndexAnnotation.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        tgtSchema = new DataObjectSchema(WithRemovedIndexAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testNewClassAnnotationValue() {
        tgtSchema = new DataObjectSchema(NewClassAnnotationValue.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testNewField() {
        tgtSchema = new DataObjectSchema(NewField.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());

        Assert.assertTrue(diff.getNewClasses().isEmpty());
        Assert.assertFalse(diff.getNewFields().isEmpty());
        Assert.assertEquals(diff.getNewFields().size(), 1);
        Assert.assertTrue(diff.getNewClassAnnotations().isEmpty());
        Assert.assertTrue(diff.getNewFieldAnnotations().isEmpty());
        Assert.assertTrue(diff.getNewAnnotationValues().isEmpty());
    }

    @Test
    public void testRemovedField() {
        tgtSchema = new DataObjectSchema(RemovedField.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testNewFieldType() {
        tgtSchema = new DataObjectSchema(NewFieldType.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testNewFieldAnnotation() {
        tgtSchema = new DataObjectSchema(NewFieldAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());

        Assert.assertTrue(diff.getNewClasses().isEmpty());
        Assert.assertTrue(diff.getNewFields().isEmpty());
        Assert.assertTrue(diff.getNewClassAnnotations().isEmpty());
        Assert.assertFalse(diff.getNewFieldAnnotations().isEmpty());
        Assert.assertEquals(diff.getNewFieldAnnotations().size(), 1);
        Assert.assertTrue(diff.getNewAnnotationValues().isEmpty());
    }

    @Test
    public void testRemovedFieldAnnotation() {
        tgtSchema = new DataObjectSchema(RemovedFieldAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testNewFieldAnnotationValue() {
        tgtSchema = new DataObjectSchema(WithChangedAnnotationValue.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testNewPermittedFieldAnnotation() {
        tgtSchema = new DataObjectSchema(NewPermittedFieldAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());

        Assert.assertTrue(diff.getNewClasses().isEmpty());
        Assert.assertTrue(diff.getNewFields().isEmpty());
        Assert.assertTrue(diff.getNewClassAnnotations().isEmpty());
        Assert.assertFalse(diff.getNewFieldAnnotations().isEmpty());
        Assert.assertEquals(diff.getNewFieldAnnotations().size(), 1);
        Assert.assertTrue(diff.getNewAnnotationValues().isEmpty());
    }

    @Test
    public void testNewNotPermittedFieldAnnotation() {
        tgtSchema = new DataObjectSchema(NewNotPermittedFieldAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testDuplicateCF() {
        tgtSchema = new DataObjectSchema(DuplicateCF1.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        srcSchema = new DataObjectSchema(classUT2.class);
        srcSchemas.addSchema(srcSchema);

        tgtSchema = new DataObjectSchema(DuplicateCF2.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        tgtSchemas.addSchema(srcSchema);

        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
        Assert.assertFalse(diff.getSchemaCT().getDuplicateList().isEmpty());
    }

    @Test
    public void testMultipleIllegalUpgradeChanges() {
        srcSchema = new DataObjectSchema(WithIndexAnnotation.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        srcSchema = new DataObjectSchema(ClassUT7.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        srcSchema = new DataObjectSchema(ClassUT4.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        srcSchema = new DataObjectSchema(ClassUT5.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        tgtSchema = new DataObjectSchema(WithRemovedIndexAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        tgtSchema = new DataObjectSchema(ClassUT3.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        tgtSchema = new DataObjectSchema(ClassUT6.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);

        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testRemoveNamedRelationIndex() {
        srcSchema = new DataObjectSchema(WithNamedRelationIndex.class);
        srcSchema.setType(srcSchema.getType());
        srcSchemas.addSchema(srcSchema);

        tgtSchema = new DataObjectSchema(WithRemovedIndexAnnotation.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testDataObjectWithInvalidDependencyCheck() {
        log.info("testDataObjectWithInvalidDependencyCheck() started...");
        DataObjectScanner scanner = new DataObjectScanner();
        String[] packages = new String[] { "com.emc.storageos.db.common.model" };
        scanner.setPackages(packages);

        boolean pass = true;
        try {
            scanner.init();
            log.info("testDataObjectWithInvalidDependencyCheck() passed.");
        } catch (Exception ex) {
            // do something
            log.info("testDataObjectWithInvalidDependencyCheck(), dependency check failed -> " + ex.getMessage());
            pass = false;
        }

        Assert.assertFalse(pass);
    }

    @Test
    public void testDataObjectWithValidDependencyCheck() {
        log.info("testDataObjectWithValidDependencyCheck() started...");
        DataObjectScanner scanner = new DataObjectScanner();
        String[] packages = new String[] { "com.emc.storageos.db.common.model2" };
        scanner.setPackages(packages);
        boolean pass = true;
        try {
            scanner.init();
            log.info("testDataObjectWithValidDependencyCheck() passed.");
        } catch (Exception ex) {
            // do something
            log.info("testDataObjectWithValidDependencyCheck(), dependency check failed -> " + ex.getMessage());
            pass = false;
        }

        Assert.assertTrue(pass);
    }

    @Test
    public void testGeoNewAnnotationOnExistingField() {
        DbSchema srcGeoSchema = new DataObjectSchema(GeoClassUT.class);
        srcSchemas.addSchema(srcGeoSchema);
        tgtSchema = new DataObjectSchema(GeoNewAnnotationOnExistingField.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        tgtSchemas.addSchema(srcSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        // Adding index on existing field of Geo object is not allowed
        Assert.assertFalse(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testGeoNewAnnotationOnNewField() {
        DbSchema srcGeoSchema = new DataObjectSchema(GeoClassUT.class);
        srcSchemas.addSchema(srcGeoSchema);
        tgtSchema = new DataObjectSchema(GeoNewField.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        tgtSchemas.addSchema(srcSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        // Adding index on new field of Geo object is allowed
        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

    @Test
    public void testGeoNewAnnotationOnNewCF() {
        DbSchema srcGeoSchema = new DataObjectSchema(GeoClassUT.class);
        srcSchemas.addSchema(srcGeoSchema);
        tgtSchemas.addSchema(srcGeoSchema);
        tgtSchema = new DataObjectSchema(GeoNewCF.class);
        tgtSchema.setType(srcSchema.getType());
        tgtSchemas.addSchema(tgtSchema);
        tgtSchemas.addSchema(srcSchema);
        DbSchemasDiff diff = new DbSchemasDiff(srcSchemas, tgtSchemas);

        // Adding index of new Geo object is allowed
        Assert.assertTrue(diff.isUpgradable());
        Assert.assertTrue(diff.isChanged());
    }

}
