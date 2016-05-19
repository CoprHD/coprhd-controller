package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Token;
import com.datastax.driver.core.TupleValue;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.utils.Bytes;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.model.DataObject;
import com.google.common.reflect.TypeToken;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

public class RowMutatorDS {
    private static final Logger log = LoggerFactory.getLogger(RowMutatorDS.class);

    private Session session;

    private PreparedStatement insertRecord;
    private PreparedStatement updateRecord;
    private PreparedStatement deleteRecord;

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
        log.info("hlj, id={}, column={}, val={}, typeofval={}", recordKey, column, val, val.getClass());
        String valString = (String) val;
        ByteBuffer valByte = ByteBuffer.wrap(valString.getBytes());

        BoundStatement insert = insertRecord.bind();
        insert.setString("key", recordKey);
        if (column.getOne() != null) {
            insert.setString("column1", column.getOne());
        }
        if (column.getTwo() != null) {
            insert.setString("column2", column.getTwo());
        }
        if (column.getThree() != null) {
            insert.setString("column3", column.getThree());
        }
        if (column.getTimeUUID() != null) {
            insert.setUUID("column4", column.getTimeUUID());
        }
        if (valByte != null) {
            insert.setBytes("value", valByte);
        }
        log.info("hlj insert={}", insert);
        recordAndIndexBatch.add(insert);
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
