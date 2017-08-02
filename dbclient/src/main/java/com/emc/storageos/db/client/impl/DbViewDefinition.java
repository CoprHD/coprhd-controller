/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.impl;

import java.util.List;

/**
 * Created by wangs12 on 7/6/2017.
 */
public class DbViewDefinition {
    private String viewName;
    private String keyName;
    private List<String> clusters;
    private List<String> columns;
    private String metaViewName;

    public DbViewDefinition(String viewName, String keyName, List<String> clusters, List<String> columns) {
        this.viewName = viewName;
        this.keyName = keyName;
        this.clusters = clusters;
        this.columns = columns;
        this.metaViewName = this.viewName + "_meta";
    }

    public String getKeyName() {
        return keyName;
    }

    public List<String> getClusterColumnNames() {
        return clusters;
    }

    public List<String> getColumnNames() {
        return columns;
    }

    public boolean hasField(String name) {
        return keyName.equals(name) || clusters.contains(name) || columns.contains(name);
    }

    public String getViewName() {
        return viewName;
    }

    public boolean isClustering(String name) {
        return clusters.contains(name);
    }

    public boolean isKey(String name) {
        return keyName.equals(name);
    }

    @Override
    public String toString() {
        return "DbViewDefinition{" +
                "viewName='" + viewName + '\'' +
                ", keyName='" + keyName + '\'' +
                ", clusters=" + clusters +
                ", columns=" + columns +
                '}';
    }

    public String getMetaViewName() {
        return metaViewName;
    }
}
