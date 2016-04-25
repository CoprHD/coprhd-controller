package com.emc.storageos.db.client.javadriver;

import com.datastax.driver.core.Row;
import com.emc.storageos.db.client.impl.CompositeColumnName;

public class CassandraRow {
    private String key;
    private Row row;
    private CompositeColumnName compositeColumnName;
    
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public Row getRow() {
        return row;
    }
    public void setRow(Row row) {
        this.row = row;
    }
    public CompositeColumnName getCompositeColumnName() {
        return compositeColumnName;
    }
    public void setCompositeColumnName(CompositeColumnName compositeColumnName) {
        this.compositeColumnName = compositeColumnName;
    }
    
}
