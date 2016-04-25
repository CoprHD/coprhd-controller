package com.emc.storageos.db.client.javadriver;

import java.util.ArrayList;
import java.util.List;

public class CassandraRows {
    private String key;
    private List<CassandraRow> rows = new ArrayList<CassandraRow>();
    
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public List<CassandraRow> getRows() {
        return rows;
    }
    public void setRows(List<CassandraRow> rows) {
        this.rows = rows;
    }
    
    
}
