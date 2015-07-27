/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;

import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.serializers.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.GlobalLock;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Encapsulate Global Lock
 */
public class GlobalLockType {
    private static final Logger log = LoggerFactory.getLogger(GlobalLockType.class);

    private final Class type = GlobalLock.class;
    private ColumnFamily<String, String> cf;

    /**
     * Constructor
     *
     * @param clazz
     */
    public GlobalLockType() {
        cf = new ColumnFamily<String, String>(((Cf)type.getAnnotation(Cf.class)).value(),
                StringSerializer.get(), StringSerializer.get());
    }

    /**
     * Get CF for global lock 
     *
     * @return
     */
    public ColumnFamily<String, String> getCf() {
        return cf;
    }

    public void serialize(MutationBatch batch, GlobalLock glock) throws ConnectionException {
        batch.withRow(cf, glock.getName()).putColumn(GlobalLock.GL_MODE_COLUMN, glock.getMode(), null);
        batch.withRow(cf, glock.getName()).putColumn(GlobalLock.GL_OWNER_COLUMN, glock.getOwner(), null);
        batch.withRow(cf, glock.getName()).putColumn(GlobalLock.GL_EXPIRATION_COLUMN, glock.getExpirationTime(), null);
        batch.execute();
    }

    public GlobalLock deserialize(Row<String, String> row) {  
        if (row == null)
            return null;

        ColumnList<String> columnList = row.getColumns();
        if (columnList == null || columnList.isEmpty())
            return null;

        Column<String> mode = columnList.getColumnByName(GlobalLock.GL_MODE_COLUMN);
        Column<String> owner = columnList.getColumnByName(GlobalLock.GL_OWNER_COLUMN);
        Column<String> expiration = columnList.getColumnByName(GlobalLock.GL_EXPIRATION_COLUMN);

        GlobalLock glock = new GlobalLock();
        glock.setName(row.getKey());
        glock.setMode(mode.getStringValue());
        glock.setOwner(owner.getStringValue());
        glock.setExpirationTime(expiration.getStringValue());
      
        return glock;
    }
}
