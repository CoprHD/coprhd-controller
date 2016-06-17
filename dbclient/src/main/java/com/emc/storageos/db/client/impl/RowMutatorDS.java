package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
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

/**
 * Will rename to RowMutator same as previous after the Astyanax is removed.
 */
public class RowMutatorDS {
    private static final Logger log = LoggerFactory.getLogger(RowMutatorDS.class);

    private DbClientContext context;
    private BatchStatement recordAndIndexBatch;

    private ConsistencyLevel writeCL = ConsistencyLevel.LOCAL_QUORUM; // default

    private UUID timeUUID;

    private static final String insertRecordFormat = "INSERT INTO \"%s\" (key, column1, column2, column3, column4, value) VALUES (?, ?, ?, ?, ?)";
    private static final String insertIndexFormat = "INSERT INTO \"%s\" (key, column1, column2, column3, column4, column5, value) VALUES (?, ?, ?, ?, ?, ?)";

    public RowMutatorDS(DbClientContext context) {
        this.context = context;
        this.recordAndIndexBatch = new BatchStatement();
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
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(insertRecordFormat, tableName));
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
        recordAndIndexBatch.add(insert);
    }

    public void insertIndexColumn(String tableName, String indexRowKey, IndexColumnName column, Object val) {
        PreparedStatement insertPrepared = context.getPreparedStatement(String.format(insertIndexFormat, tableName));
        BoundStatement insert = insertPrepared.bind();
        // For PRIMARY KEY (key, column1, column2, column3, column4, column5), the primary key cannot be null
        insert.setString("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne());
        insert.setString("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo());
        insert.setString("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree());
        insert.setString("column4", column.getFour() == null ? StringUtils.EMPTY : column.getFour());
        insert.setUUID("column5", column.getTimeUUID() == null ? UUIDs.timeBased() : column.getTimeUUID());
        ByteBuffer blobVal = getByteBufferFromPrimitiveValue(val);
        insert.setBytes("value", blobVal);
        recordAndIndexBatch.add(insert);
    }

    public void execute() {
        context.getSession().execute(recordAndIndexBatch.setConsistencyLevel(writeCL));
    }

    public static ByteBuffer getByteBufferFromPrimitiveValue(Object val) {
        if (val == null) {
            return ByteBufferUtil.EMPTY_BYTE_BUFFER;
        }
        ByteBuffer blobVal = null;
        Class valClass = val.getClass();
        if (valClass == byte[].class) {
            blobVal = ByteBuffer.wrap((byte[]) val);
        } else if (valClass == String.class) {
            blobVal = UTF8Serializer.instance.serialize((String) val);
        } else if (valClass == URI.class ||
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
