/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
        if (!(o instanceof DataObjectSchema)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this);
    }
}
