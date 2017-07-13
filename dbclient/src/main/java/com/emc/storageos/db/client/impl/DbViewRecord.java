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

/**
 * Created by wangs12 on 7/7/2017.
 */
public class DbViewRecord {

    private DbViewDefinition viewDef;
    private String keyValue;
    private List<ViewColumn> clusters = new ArrayList<>();
    private List<ViewColumn> columns = new ArrayList<>();

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

    public void addClusteringColumn(String name, Object val) { // todo: suppose value must be string for now. could be num or maybe else.
        clusters.add(new ViewColumn(name, val));
    }

    public void addColumn(String name, Object val) {
        columns.add(new ViewColumn(name, val));
    }

    public String getInsertCql() {
        StringBuilder cql = new StringBuilder();
        cql.append("INSERT INTO " + viewDef.getViewName() + " (" + getKeyName() + "," );

        for (ViewColumn cluster: clusters) {
            cql.append(cluster.getName());
            cql.append(",");
        }
        for (ViewColumn col: columns) {
            cql.append(col.getName());
            cql.append(",");
        }
        cql.deleteCharAt(cql.length()-1);

        cql.append(") VALUES(");
        int valCount = 1 + clusters.size() + columns.size();
        for (int i = 0; i < valCount; i++) {
            cql.append("?,");
        }
        cql.deleteCharAt(cql.length()-1);
        cql.append(")");

        return cql.toString();
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

        return cql.toString();
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }

    public DbViewDefinition getDef() {
        return this.viewDef;
    }
}
