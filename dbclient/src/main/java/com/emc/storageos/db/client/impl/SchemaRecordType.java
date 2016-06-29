/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import org.apache.cassandra.serializers.UTF8Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Row;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.SchemaRecord;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.serializers.StringSerializer;

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

    public SchemaRecord deserialize(Row row) {
        if (row == null) {
            return null;
        }
        
        SchemaRecord record = new SchemaRecord();
        record.setVersion(row.getString(0));
        record.setSchema(UTF8Serializer.instance.deserialize(row.getBytes(2)));

        return record;
    }
}
