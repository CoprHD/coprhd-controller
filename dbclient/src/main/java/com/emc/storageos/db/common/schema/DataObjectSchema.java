/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.util.Objects;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.DbSchemaScannerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataObjectSchema extends DbSchema {
    private static final Logger log = LoggerFactory.getLogger(DataObjectSchema.class);
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
        if (!(o instanceof DataObjectSchema)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        log.info("lbyt: class={} stack=", this.getClass().getSimpleName(), new Throwable());
        return Objects.hash(this);
    }
}
