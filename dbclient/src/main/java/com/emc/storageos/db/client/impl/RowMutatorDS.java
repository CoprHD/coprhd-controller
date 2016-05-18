package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.model.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        this.insertRecord = this.session.prepare(insertInto(tableName)
                .value("key", bindMarker())
                .value("column1", bindMarker())
                .value("column2", bindMarker())
                .value("column3", bindMarker())
                .value("column4", bindMarker())
                .value("value", bindMarker()));

        this.timeUUID = UUIDs.timeBased();
    }

    public void addColumn(String recordKey, CompositeColumnName column, Object val) {
        recordAndIndexBatch
                .add(insertRecord.bind(recordKey, column.getOne(), column.getTwo(), column.getThree(), column.getTimeUUID(), val));
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
