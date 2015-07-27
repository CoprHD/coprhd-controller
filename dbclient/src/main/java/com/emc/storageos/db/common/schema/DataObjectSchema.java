/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.util.Objects;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.DbSchemaScannerInterceptor;

public class DataObjectSchema extends DbSchema {
    public DataObjectSchema() {
    }

    public DataObjectSchema(Class<? extends DataObject> clazz) {
        this(clazz, null);
    }

    public DataObjectSchema(Class<? extends DataObject> clazz, DbSchemaScannerInterceptor scannerInterceptor) {
        super(clazz, scannerInterceptor);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DataObjectSchema)) 
            return false;

        return super.equals(o);
    }
    
    @Override
    public int hashCode(){
    	return Objects.hash(this);
    }
}
