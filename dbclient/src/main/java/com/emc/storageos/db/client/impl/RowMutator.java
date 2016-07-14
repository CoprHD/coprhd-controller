/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.exceptions.DatabaseException;
import org.apache.cassandra.serializers.BooleanSerializer;
import org.apache.cassandra.serializers.DoubleSerializer;
import org.apache.cassandra.serializers.FloatSerializer;
import org.apache.cassandra.serializers.Int32Serializer;
import org.apache.cassandra.serializers.LongSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

public class RowMutator {
    private static final Logger log = LoggerFactory.getLogger(RowMutator.class);

    private DbClientContext context;
    private BatchStatement atomicBatch;
    private UUID timeUUID;

    private ConsistencyLevel writeCL = ConsistencyLevel.EACH_QUORUM; // default
    private final int defaultTTL = 0;

    private static final String updateRecordFormat = "UPDATE \"%s\" SET value = ? WHERE key = ? AND column1 = ? AND column2 = ? AND column3 = ? AND column4 = ?";
    private static final String updateIndexFormat = "UPDATE \"%s\" SET value = ? WHERE key = ? AND column1 = ? AND column2 = ? AND column3 = ? AND column4 = ? AND column5 = ?";
    private static final String insertTimeSeriesFormat = "INSERT INTO \"%s\" (key, column1, value) VALUES(?, ?, ?) USING TTL ?";
    private static final String insertSchemaRecordFormat = "INSERT INTO \"%s\" (key, column1, value) VALUES(?, ?, ?)";
    private static final String insertGlobalLockFormat = "INSERT INTO \"%s\" (key, column1, value) VALUES(?, ?, ?) USING TTL ?";

    public RowMutator(DbClientContext context) {
        this.context = context;
        this.atomicBatch = new BatchStatement();
        this.timeUUID = UUIDs.timeBased();
        /*
         * will consider codeRegistry later.
         * CodecRegistry codecRegistry = CodecRegistry.DEFAULT_INSTANCE;
         * add more customized codec
         * codecRegistry.register();
         */
    }

    public void insertRecordColumn(String tableName, String recordKey, CompositeColumnName column, Object val) {
        // todo consider 'ttl'
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(updateRecordFormat, tableName));
        BoundStatement insert = insertPrepared.bind();
        insert.setString("key", recordKey);
        // For PRIMARY KEY (key, column1, column2, column3, column4), the primary key cannot be null
        insert.setString("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne());
        insert.setString("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo());
        insert.setString("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree());
        // todo when column4 is null, "Invalid null value for clustering key part column4" exception will be thrown, so we set column4 with timeBased() not empty. But previously Astyanax allows column4 is null
        insert.setUUID("column4", column.getTimeUUID() == null ? UUIDs.timeBased() : column.getTimeUUID());
        ByteBuffer blobVal = getByteBufferFromPrimitiveValue(val);
        insert.setBytes("value", blobVal);
        atomicBatch.add(insert);
    }

    public void insertIndexColumn(String tableName, String indexRowKey, IndexColumnName column, Object val) {
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(updateIndexFormat, tableName));
        BoundStatement insert = insertPrepared.bind();
        insert.setString("key", indexRowKey);
        // For PRIMARY KEY (key, column1, column2, column3, column4, column5), the primary key cannot be null
        insert.setString("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne());
        insert.setString("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo());
        insert.setString("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree());
        insert.setString("column4", column.getFour() == null ? StringUtils.EMPTY : column.getFour());
        insert.setUUID("column5", column.getTimeUUID() == null ? UUIDs.timeBased() : column.getTimeUUID());
        ByteBuffer blobVal = getByteBufferFromPrimitiveValue(val);
        insert.setBytes("value", blobVal);
        atomicBatch.add(insert);
    }

    public void deleteRecordColumn(String tableName, String recordKey, CompositeColumnName column) {
        Delete.Where deleteRecord = delete().from(String.format("\"%s\"", tableName)).where(eq("key", recordKey))
                .and(eq("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne()))
                .and(eq("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo()))
                .and(eq("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree()));
        UUID uuidColumn = column.getTimeUUID();
        if (uuidColumn != null) {
            deleteRecord.and(eq("column4", uuidColumn));
        }
        atomicBatch.add(deleteRecord);
    }

    public void deleteIndexColumn(String tableName, String indexRowKey, IndexColumnName column) {
        Delete.Where deleteIndex = delete().from(String.format("\"%s\"", tableName)).where(eq("key", indexRowKey))
                .and(eq("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne()))
                .and(eq("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo()))
                .and(eq("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree()))
                .and(eq("column4", column.getFour() == null ? StringUtils.EMPTY : column.getFour()));
        UUID uuidColumn = column.getTimeUUID();
        if (uuidColumn != null) {
            deleteIndex.and(eq("column5", uuidColumn));
        }
        atomicBatch.add(deleteIndex);
    }

    public void execute() {
        atomicBatch.setConsistencyLevel(writeCL);
        context.getSession().execute(atomicBatch);
        //todo executeWithRetry
    }
    
    public void insertTimeSeriesColumn(String tableName, String rowKey, UUID uuid, Object val, Integer ttl) {
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(insertTimeSeriesFormat, tableName));
        BoundStatement insert = insertPrepared.bind();
        insert.setString(0, rowKey);
        insert.setUUID(1, uuid);
        insert.setBytes(2, getByteBufferFromPrimitiveValue(val));
        insert.setInt(3, ttl);
        atomicBatch.add(insert);
    }
    
    public void insertSchemaRecord(String tableName, String rowKey, String column, Object val) {
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(insertSchemaRecordFormat, tableName));
        BoundStatement insert = insertPrepared.bind();
        insert.setString(0, rowKey);
        insert.setString(1, column);
        insert.setBytes(2, getByteBufferFromPrimitiveValue(val));
        atomicBatch.add(insert);
    }
    
    public void insertGlobalLockRecord(String tableName, String rowKey, String column, Object val, Integer ttl) {
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(insertGlobalLockFormat, tableName));
        BoundStatement insert = insertPrepared.bind();
        insert.setString(0, rowKey);
        insert.setString(1, column);
        insert.setBytes(2, getByteBufferFromPrimitiveValue(val));
        insert.setInt(3, ttl == null ? defaultTTL : ttl);
        atomicBatch.add(insert);
    }
    
    public void deleteGlobalLockRecord(String tableName, String rowKey, String column) {
        Delete.Where deleteRecord = delete().from(String.format("\"%s\"", tableName)).where(eq("key", rowKey)).and(eq("column1", column));
        atomicBatch.add(deleteRecord);
    }

    public static ByteBuffer getByteBufferFromPrimitiveValue(Object val) {
        if (val == null) {
            return ByteBufferUtil.EMPTY_BYTE_BUFFER;
        }
        ByteBuffer blobVal = null;
        Class valClass = val.getClass();
        if (valClass == byte[].class) {
            blobVal = ByteBuffer.wrap((byte[]) val);
        } else if (valClass == String.class ||
                valClass == URI.class ||
                valClass == NamedURI.class ||
                valClass == ScopedLabel.class) {
            blobVal = UTF8Serializer.instance.serialize(val.toString());
        } else if (valClass == Byte.class) {
            blobVal = Int32Serializer.instance.serialize((Byte) val & 0xff);
        } else if (valClass == Boolean.class) {
            blobVal = BooleanSerializer.instance.serialize((Boolean) val);
        } else if (valClass == Short.class) {
            blobVal = ByteBuffer.allocate(2);
            blobVal.putShort((Short) val);
        } else if (valClass == Integer.class) {
            blobVal = Int32Serializer.instance.serialize((Integer) val);
        } else if (valClass == Long.class) {
            blobVal = LongSerializer.instance.serialize((Long) val);
        } else if (valClass == Float.class) {
            blobVal = FloatSerializer.instance.serialize((Float) val);
        } else if (valClass == Double.class) {
            blobVal = DoubleSerializer.instance.serialize((Double) val);
        } else if (valClass == Date.class) {
            blobVal = LongSerializer.instance.serialize(((Date) val).getTime());
        } else if (val instanceof Calendar) {
            long timestamp = ((Calendar) val).getTimeInMillis();
            blobVal = LongSerializer.instance.serialize(timestamp);
        } else if (valClass.isEnum()) {
            blobVal = UTF8Serializer.instance.serialize(((Enum<?>) val).name());
        } else {
            throw DatabaseException.fatals.serializationFailedUnsupportedType(val);
        }

        return blobVal;
    }

    public void setWriteCL(ConsistencyLevel writeCL) {
        this.writeCL = writeCL;
    }

    public UUID getTimeUUID() {
        return timeUUID;
    }

}
