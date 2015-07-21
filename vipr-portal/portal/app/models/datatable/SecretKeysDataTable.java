/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

public class SecretKeysDataTable extends DataTable {

    public SecretKeysDataTable() {
        addColumn("secretKey");
        addColumn("creationTimestamp");
        sortAll();
        setDefaultSortField("secretKey");
    }

    public static class SecretKey {
        public String id;
        public String secretKey;
        public String creationTimestamp;

        public SecretKey(String id, String secretKey, String creationTimestamp) {
        	this.id = id;
        	this.secretKey = secretKey;
        	this.creationTimestamp = creationTimestamp;
        }
        
    }
}
