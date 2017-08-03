/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.model.DataObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by wangs12 on 7/7/2017.
 */
public class DbViewRecord {

    private DbViewDefinition viewDef;
    private String keyValue;
    private List<ViewColumn> clusters = new ArrayList<>();
    private List<ViewColumn> columns = new ArrayList<>();
    private UUID timeUUID;

    public DbViewDefinition getViewDef() {
        return viewDef;
    }

    public DbViewRecord(DbViewDefinition viewDef) {
        this.viewDef = viewDef;
    }

    public String getKeyName() {
        return viewDef.getKeyName();
    }

    public String getKeyValue() {
        return keyValue;
    }

    public List<ViewColumn> getClusterColumns() {
        return clusters;
    }

    public List<ViewColumn> getColumns() {
        return columns;
    }

    public <T> void addClusteringColumn(String name, Object val) { // todo: suppose value must be string for now. could be num or maybe else.
        clusters.add(new ViewColumn(name, val, val.getClass()));
    }

    public void addColumn(String name, Object val) {
        columns.add(new ViewColumn(name, val, val.getClass()));
    }

    public String getUpsertCql() {
        StringBuilder cql = new StringBuilder();
        cql.append("UPDATE " + viewDef.getViewName() + " SET ");

        for (ViewColumn col: columns) {
            cql.append(col.getName() + " = ?,");
        }
        cql.deleteCharAt(cql.length()-1);

        cql.append(" WHERE " + viewDef.getKeyName() + " = ? and ");

        for (int i = 0; i < clusters.size(); i++) {
            ViewColumn cluster = clusters.get(i);
            cql.append(cluster.getName() + " = ? ");
            if (i < clusters.size()-1) {
                cql.append(" and ");
            }
        }

        cql.append(" and timeuuid = ?");
        return cql.toString();
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }

    public DbViewDefinition getDef() {
        return this.viewDef;
    }

    public String getDeleteCql() {
        StringBuilder cql = new StringBuilder();
        cql.append("DELETE FROM " + viewDef.getViewName() + " " );

        cql.append(" WHERE " + viewDef.getKeyName() + " = ? and ");

        for (int i = 0; i < clusters.size(); i++) {
            ViewColumn cluster = clusters.get(i);
            cql.append(cluster.getName() + " = ? ");
            if (i < clusters.size()-1) {
                cql.append(" and ");
            }
        }
        cql.append(" and timeuuid = ?");

        return cql.toString();
    }

    public UUID getTimeUUID() {
        return timeUUID;
    }

    public void setTimeUUID(UUID timeUUID) {
        this.timeUUID = timeUUID;
    }
}
