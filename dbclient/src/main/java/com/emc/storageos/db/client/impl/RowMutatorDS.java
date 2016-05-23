package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
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

public class RowMutatorDS {
    private static final Logger log = LoggerFactory.getLogger(RowMutatorDS.class);

    private Session session;

    private PreparedStatement insertRecord;
    private PreparedStatement updateRecord;
    private PreparedStatement deleteRecord;
    private PreparedStatement insertIndex;


    private BatchStatement recordAndIndexBatch;

    private UUID timeUUID;

    public RowMutatorDS(Session session, String tableName) {
        this.session = session;
        this.insertRecord = this.session.prepare(insertInto(String.format("\"%s\"", tableName))
                .value("key", bindMarker())
                .value("column1", bindMarker())
                .value("column2", bindMarker())
                .value("column3", bindMarker())
                .value("column4", bindMarker())
                .value("value", bindMarker())).setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        this.recordAndIndexBatch = new BatchStatement();

        this.timeUUID = UUIDs.timeBased();
    }

    public void addColumn(String recordKey, CompositeColumnName column, Object val) {
        //todo we should consider 'ttl'
        log.info("hlj, id={}, column={}, val={}, typeofval={}", recordKey, column, val, val.getClass());
        BoundStatement insert = insertRecord.bind();
        insert.setString("key", recordKey);
        //For PRIMARY KEY (key, column1, column2, column3, column4), the primary key cannot be null
        insert.setString("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne());
        insert.setString("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo());
        insert.setString("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree());
        //todo when column4 is null, "Invalid null value for clustering key part column4" exception will be thrown, so we should set column4 with an empty UUID here(but don't find how to). but below is timeBased() not empty.
        insert.setUUID("column4", column.getTimeUUID() == null ? UUIDs.timeBased() : column.getTimeUUID());
        ByteBuffer blobVal = RowMutatorDS.getByteBufferFromPrimitiveValue(val);
        insert.setBytes("value", blobVal);
        log.info("hlj insert={}, blobval={}", insert, blobVal);
        recordAndIndexBatch.add(insert);
    }

    public void addIndexColumn(String tableName, String indexRowKey, IndexColumnName column, Object val) {
        log.info("hlj INDEX, rowKey={}, tableName={}, column={}, val={}, typeofval={}", indexRowKey, tableName, column, val, val.getClass());
        //todo move to constructed method
        insertIndex = this.session.prepare(insertInto(String.format("\"%s\"", tableName))
                .value("key", bindMarker())
                .value("column1", bindMarker())
                .value("column2", bindMarker())
                .value("column3", bindMarker())
                .value("column4", bindMarker())
                .value("column5", bindMarker())
                .value("value", bindMarker())).setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        BoundStatement insert = insertIndex.bind();
        insert.setString("key", indexRowKey);
        // For PRIMARY KEY (key, column1, column2, column3, column4, column5), the primary key cannot be null
        insert.setString("column1", column.getOne() == null ? StringUtils.EMPTY : column.getOne());
        insert.setString("column2", column.getTwo() == null ? StringUtils.EMPTY : column.getTwo());
        insert.setString("column3", column.getThree() == null ? StringUtils.EMPTY : column.getThree());
        insert.setString("column4", column.getFour() == null ? StringUtils.EMPTY : column.getFour());
        insert.setUUID("column5", column.getTimeUUID() == null ? UUIDs.timeBased() : column.getTimeUUID());
        ByteBuffer blobVal = RowMutatorDS.getByteBufferFromPrimitiveValue(val);
        insert.setBytes("value", blobVal);
        log.info("hlj INDEX insert={}, blobval={}", insert, blobVal);
        recordAndIndexBatch.add(insert);
    }

    public static ByteBuffer getByteBufferFromPrimitiveValue(Object val) {
        log.info("hlj in convert={}, type={}", val, val.getClass());
        if (val == null) {
            return null;// ByteBufferUtil.EMPTY_BYTE_BUFFER
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

    public void execute() {
        log.info("hlj in execute={}", recordAndIndexBatch);
        session.execute(recordAndIndexBatch);
        log.info("hlj in execute done.", recordAndIndexBatch);
    }

    public UUID getTimeUUID() {
        return timeUUID;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(super.toString());
        sb.append(this.insertRecord);
        sb.append(this.session);
        sb.append(this.recordAndIndexBatch);
        sb.append(this.timeUUID);
        return sb.toString();
    }
}
