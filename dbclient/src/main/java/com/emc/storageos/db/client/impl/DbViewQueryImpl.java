/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbViewQuery;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;

import java.net.URI;

/**
 * Created by wangs12 on 7/25/2017.
 */
public class DbViewQueryImpl implements DbViewQuery {
    DbClientImpl dbClient;

    public DbViewQueryImpl(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public <T extends DataObject> QueryResultList<T> listByView(Class<T> clazz, String viewName, String key, Object[] clusterKeys) {
        /*
        DbClientContext dbCtx = dbClient.getDbClientContext(clazz);
        String cql = "select * from vol_view where project = ? and type = ?";
        PreparedStatement queryStatement = dbCtx.getPreparedStatement(cql);

        BoundStatement bindStmt = queryStatement.bind(project.toString(), type);
        bindStmt.setFetchSize(DEFAULT_TS_PAGE_SIZE);

        ResultSet rs = dbCtx.getSession().execute(bindStmt);
        volumes.setResult(new DbClientImpl.MyIterator(rs));
        */

        return null;
    }

    @Override
    public void listVolumesByProject(URI project, int type, QueryResultList<Volume> volumes) {
    }
}
