/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.util.Objects;

import com.emc.storageos.db.client.model.TimeSeriesSerializer;

public class DataPointSchema extends DbSchema {

    public DataPointSchema() {
    }

    public DataPointSchema(Class<? extends TimeSeriesSerializer.DataPoint> clazz) {
        super(clazz);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DataPointSchema)) 
            return false;

        return super.equals(o);
    }
    
    @Override
    public int hashCode(){
    	return Objects.hash(this);
    }
}
