/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.*;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbViewQuery;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Iterator;

/**
 * Created by wangs12 on 7/25/2017.
 */
public class DbViewQueryImpl implements DbViewQuery {
    private static Logger log = LoggerFactory.getLogger(DbViewQueryImpl.class);
    private DbClientImpl dbClient;

    public DbViewQueryImpl(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public <T extends DataObject> QueryResultList<T> listByView(Class<T> clazz, String viewName, String key, Object[] clusterKeys) {
        return null;
    }

    @Override
    public void listVolumesByProject(URI project, int type, QueryResultList<Volume> resultList) {
        DbClientContext dbCtx = dbClient.getDbClientContext(Volume.class);
        String cql = "select * from vol_view where project = ? and type = ?";
        PreparedStatement queryStatement = dbCtx.getPreparedStatement(cql);

        BoundStatement bindStmt = queryStatement.bind(project.toString(), type);
        // bindStmt.setFetchSize(DEFAULT_TS_PAGE_SIZE);

        ResultSet rs = dbCtx.getSession().execute(bindStmt);
        resultList.setResult(new MyIterator(rs));
    }

    private static class MyIterator implements Iterator<Volume> {

        private ResultSet rs;
        public MyIterator(ResultSet rs) {
            this.rs = rs;
        }

        @Override
        public boolean hasNext() {
            return rs.iterator().hasNext();
        }

        @Override
        public Volume next() {
            Row row = rs.iterator().next();
            for (ColumnDefinitions.Definition colDef: row.getColumnDefinitions().asList()) {
                log.info("====== Columns returned by vol view: {}", colDef.getName());
            }

            Volume vol = new Volume();
            vol.setType(row.getInt("type"));
            vol.setLabel(row.getString("label"));
            vol.setId(URI.create(row.getString("id")));
            return vol;
        }
    }

}
