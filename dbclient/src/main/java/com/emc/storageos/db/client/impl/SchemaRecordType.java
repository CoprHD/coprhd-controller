/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.serializers.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.SchemaRecord;

/**
 * Encapsulate schema information
 */
public class SchemaRecordType {
    private static final Logger log = LoggerFactory.getLogger(SchemaRecordType.class);

    private static final String SCHEMA_COLUMN_NAME = "schema";
    private final Class type = SchemaRecord.class;
    private ColumnFamily<String, String> cf;

    /**
     * Constructor
     * 
     * @param clazz
     */
    public SchemaRecordType() {
        cf = new ColumnFamily<String, String>(((Cf) type.getAnnotation(Cf.class)).value(),
                StringSerializer.get(), StringSerializer.get());
    }

    /**
     * Get CF for this schema data
     * 
     * @return
     */
    public ColumnFamily<String, String> getCf() {
        return cf;
    }

    public void serialize(MutationBatch batch, SchemaRecord record) throws ConnectionException {
        batch.withRow(cf, record.getVersion())
                .putColumn(SCHEMA_COLUMN_NAME, record.getSchema(), null);
        batch.execute();
    }

    public SchemaRecord deserialize(Row<String, String> row) {
        if (row == null) {
            return null;
        }

        ColumnList<String> columnList = row.getColumns();
        if (columnList == null || columnList.isEmpty()) {
            return null;
        }

        Column<String> column = columnList.getColumnByName(SCHEMA_COLUMN_NAME);
        SchemaRecord record = new SchemaRecord();
        record.setVersion(row.getKey());
        record.setSchema(column.getStringValue());

        return record;
    }
}
