/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

public class DuplicatedFieldsTest {

    private DbSchemas dbSchemas;
    private final int DUPLICATE_SCHEMA_COUNT = 1;
    private final String DUPLICATE_SCHEMA_NAME = "Dummy";

    @Before
    public void setup() {
        dbSchemas = new DbSchemas();
    }

    @Test
    public void testDuplicateField() {
        DbSchema schema = new DbSchema(DuplicateFieldObject.class);
        dbSchemas.addSchema(schema);

        boolean hasDuplicateColumn = dbSchemas.hasDuplicateField();
        Assert.assertTrue(hasDuplicateColumn);

        Map<String, List<FieldInfo>> schemaDuplicateColumns = dbSchemas.getDuplicateFields();
        Assert.assertEquals(DUPLICATE_SCHEMA_COUNT, schemaDuplicateColumns.size());
        Assert.assertTrue(schemaDuplicateColumns.containsKey(DUPLICATE_SCHEMA_NAME));
    }

    @Test
    public void testNoDuplicateField() {
        DbSchema schema = new DbSchema(NoDuplicateFieldObject.class);
        dbSchemas.addSchema(schema);

        boolean hasDuplicateColumn = dbSchemas.hasDuplicateField();
        Assert.assertFalse(hasDuplicateColumn);
    }

    @SuppressWarnings("serial")
    @Cf("Dummy")
    private static class DuplicateFieldObject extends DataObject {
        private String dummy;

        @Name("status")
        public String getDummy() {
            return dummy;
        }

        public void setDummy(String dummy) {
            this.dummy = dummy;
        }
    }

    @SuppressWarnings("serial")
    @Cf("Dummy")
    private static class NoDuplicateFieldObject extends DataObject {
    }

}
