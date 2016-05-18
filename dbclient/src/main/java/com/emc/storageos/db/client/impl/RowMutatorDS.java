package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
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



}
